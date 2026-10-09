package org.arend.server.daemon

import org.arend.daemon.LockFile
import org.arend.daemon.server.SocketBinder
import org.arend.daemon.wire.FrameChannel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.net.UnixDomainSocketAddress
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The daemon protocol as the IDE answers it, spoken to over a real socket, with a runner that only records. */
class IdeDaemonServerTest {
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private lateinit var dir: Path
    private lateinit var server: IdeDaemonServer
    private lateinit var socket: SocketChannel
    private val ran = ArrayList<List<String>>()
    private val holding = CountDownLatch(1)
    private val stopped = CountDownLatch(1)

    @Before
    fun setUp() {
        assumeTrue("Unix domain sockets required", !System.getProperty("os.name", "").lowercase().startsWith("windows"))
        dir = Files.createTempDirectory("ide-daemon")
        val bound = SocketBinder.bind(dir.resolve("daemon.sock"), "0123456789abcdef")
        server = IdeDaemonServer(bound, { listOf("/lib/arend.yaml") }, object : IdeDaemonRunner {
            override fun run(args: List<String>, output: IdeDaemonOutput, cancel: AtomicBoolean): Int {
                synchronized(ran) { ran.add(args) }
                if ("Hold" in args) {
                    holding.countDown()
                    while (!cancel.get()) Thread.sleep(5)
                    return 0
                }
                output.out("checked ${args.joinToString(" ")}\n")
                output.err("a warning\n")
                return 3
            }

            override fun refresh(output: IdeDaemonOutput, cancel: AtomicBoolean): Int {
                output.out("refreshed\n")
                return 0
            }
        }, onShutdown = { stopped.countDown() })
        server.start()
        socket = SocketChannel.open(UnixDomainSocketAddress.of(bound.address().removePrefix("uds:")))
    }

    @After
    fun tearDown() {
        if (::socket.isInitialized) socket.close()
        if (::server.isInitialized) server.close()
        if (::dir.isInitialized) dir.toFile().deleteRecursively()
    }

    private fun send(request: Map<String, Any>) = FrameChannel(socket).write(request)

    /** Frames read while waiting for another request's answer, by request id. */
    private val pending = HashMap<Any?, ArrayList<Map<String, Any?>>>()

    /** The frames answering [id], up to and including its `done`; those of other requests are kept for later. */
    private fun answer(id: String): List<Map<String, Any?>> {
        val frames = pending.remove(id) ?: ArrayList()
        while (frames.lastOrNull()?.get("kind") != "done") {
            val frame = FrameChannel.read(socket) ?: error("the daemon hung up")
            if (frame["id"] == id) frames.add(frame) else pending.getOrPut(frame["id"]) { ArrayList() }.add(frame)
        }
        return frames
    }

    private fun exitCode(frames: List<Map<String, Any?>>) = (frames.last()["exitCode"] as Number).toInt()

    @Test
    fun theStatusSaysTheDaemonIsTheIde() {
        send(mapOf("id" to "1", "op" to "status"))
        val status = answer("1").first()
        assertEquals(ProcessHandle.current().pid(), (status["pid"] as Number).toLong())
        assertEquals(LockFile.HOST_INTELLIJ, status["host"])
        assertEquals(listOf("/lib/arend.yaml"), status["libraries"])
        assertEquals("IDLE", status["state"])

        send(mapOf("id" to "2", "op" to "ping"))
        assertEquals("IDLE", answer("2").first()["state"])
    }

    @Test
    fun aCommandStreamsItsOutputAndExitCode() {
        send(mapOf("id" to "1", "op" to "cli", "args" to listOf("/lib/arend.yaml", "Foo")))
        val frames = answer("1")
        assertEquals(listOf("stdout", "stderr", "done"), frames.map { it["kind"] })
        assertEquals("checked /lib/arend.yaml Foo\n", frames[0]["data"])
        assertEquals(3, exitCode(frames))

        send(mapOf("id" to "2", "op" to "refresh"))
        assertEquals("refreshed\n", answer("2").first()["data"])

        // What the IDE shows: both answered, newest first, and nothing left running.
        val activity = server.activity
        assertEquals("IDLE", activity.state)
        assertEquals(null, activity.current)
        assertEquals(2, activity.served)
        assertEquals(listOf("refresh" to 0, "/lib/arend.yaml Foo" to 3), activity.recent.map { it.command to it.exitCode })
    }

    /** A running command and one queued behind it are both abandoned, each answered once. */
    @Test
    fun aCancelAbandonsRunningAndQueuedCommands() {
        send(mapOf("id" to "1", "op" to "cli", "args" to listOf("Hold")))
        assertTrue(holding.await(10, TimeUnit.SECONDS))
        send(mapOf("id" to "2", "op" to "cli", "args" to listOf("Queued")))
        send(mapOf("id" to "3", "op" to "cancel", "targetId" to "2"))
        assertEquals(true, answer("3").first()["accepted"])
        assertEquals(FrameChannel.CANCELLED_EXIT_CODE, exitCode(answer("2")))

        send(mapOf("id" to "4", "op" to "cancel", "targetId" to "1"))
        assertEquals(FrameChannel.CANCELLED_EXIT_CODE, exitCode(answer("1")))
        assertEquals(listOf(listOf("Hold")), synchronized(ran) { ArrayList(ran) })
    }

    /** A shutdown is acknowledged and handed to the IDE, which stops the daemon; the IDE itself goes on. */
    @Test
    fun aShutdownIsHandedToTheIde() {
        send(mapOf("id" to "1", "op" to "shutdown"))
        assertEquals(0, exitCode(answer("1")))
        assertTrue(stopped.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun anUnknownOpIsRefused() {
        send(mapOf("id" to "1", "op" to "frobnicate"))
        val frames = answer("1")
        assertEquals("error", frames.first()["kind"])
        assertEquals(1, exitCode(frames))
    }
}
