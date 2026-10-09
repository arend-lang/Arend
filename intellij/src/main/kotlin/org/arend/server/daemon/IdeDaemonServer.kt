package org.arend.server.daemon

import org.arend.daemon.LockFile
import org.arend.daemon.server.SocketBinder
import org.arend.daemon.wire.FrameChannel
import java.io.IOException
import java.nio.channels.ClosedChannelException
import java.nio.channels.SocketChannel
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Where a request's output goes: the frames of the client that sent it. The IDE's own
 * `System.out` is never touched, unlike the CLI daemon's, which owns its process.
 */
class IdeDaemonOutput(private val channel: FrameChannel, private val requestId: String) {
    fun out(text: String) = send(FrameChannel.stdoutFrame(requestId, text))

    fun err(text: String) = send(FrameChannel.stderrFrame(requestId, text))

    fun done(exitCode: Int) = send(FrameChannel.doneFrame(requestId, exitCode))

    private fun send(frame: Map<String, Any>) {
        try {
            channel.write(frame)
        } catch (_: IOException) {
            // The client has gone; the request runs to its end regardless.
        }
    }
}

/** What answers the requests that need the IDE's server: a `cli` command and a `refresh`. */
interface IdeDaemonRunner {
    /**
     * Runs `arend` [args] against the IDE's server, as issued from the client's [cwd] (null when it
     * did not say); a set [cancel] abandons it. Returns the exit code.
     */
    fun run(args: List<String>, cwd: String?, output: IdeDaemonOutput, cancel: AtomicBoolean): Int

    /** Brings the IDE's server up to date with the files on disk. Returns the exit code. */
    fun refresh(output: IdeDaemonOutput, cancel: AtomicBoolean): Int
}

/**
 * The daemon wire protocol, answered from inside the IDE: the same ops and frames as the CLI's
 * `DaemonServer`, so `arend` and the MCP server cannot tell the two apart. Requests that need the
 * server run one at a time on a worker thread; `ping`, `status` and `cancel` are answered at once.
 * Every thread is a daemon thread, and nothing here exits the process: [shutdown] hands the stop
 * back to the IDE through [onShutdown].
 */
class IdeDaemonServer(
    private val bound: SocketBinder.Bound,
    private val libraries: () -> List<String>,
    private val runner: IdeDaemonRunner,
    private val onShutdown: () -> Unit,
    /** Says what a request asks for, for a person: `refresh` for null. */
    private val describe: (List<String>?) -> String = { it?.joinToString(" ") ?: "refresh" },
    /** Called whenever [activity] has changed. */
    private val onActivity: () -> Unit = {}
) {
    /** A request queued for the worker; [command] is what [describe] says of it. */
    private class WorkItem(val requestId: String, val args: List<String>?, val cwd: String?, replyTo: FrameChannel, val command: String) {
        val cancel = AtomicBoolean()
        val output = IdeDaemonOutput(replyTo, requestId)
    }

    /** A request the worker has finished, cancelled ones included. */
    data class Finished(val command: String, val startedAt: Long, val durationMs: Long, val exitCode: Int)

    /** What the daemon is doing: for the IDE to show, not for clients. */
    data class Activity(
        val state: String,
        /** The request running, with when it started; null when idle. */
        val current: String?,
        val currentSince: Long?,
        val queued: List<String>,
        val served: Int,
        /** The latest requests finished, newest first. */
        val recent: List<Finished>
    )

    val startedAt = System.currentTimeMillis()
    /** The request the worker runs, with when it started; null when idle. */
    @Volatile private var current: Pair<WorkItem, Long>? = null
    private val recent = ArrayDeque<Finished>()
    private var served = 0
    private val workQueue = LinkedBlockingQueue<WorkItem>()
    private val inFlight = ConcurrentHashMap<String, AtomicBoolean>()
    private val clientPool: ExecutorService = Executors.newCachedThreadPool { daemonThread(it, "arend-ide-daemon-client") }
    private val acceptThread = daemonThread(::acceptLoop, "arend-ide-daemon-accept")
    private val workerThread = daemonThread(::workerLoop, "arend-ide-daemon-worker")

    fun start() {
        acceptThread.start()
        workerThread.start()
    }

    private val state: String
        get() = if (current == null) "IDLE" else "BUSY"

    val activity: Activity
        get() = synchronized(recent) {
            val running = current
            Activity(state, running?.first?.command, running?.second, workQueue.map { it.command }, served, recent.toList())
        }

    /**
     * Records [item] as finished and only then tells its client, so that a client hearing `done`
     * finds the daemon already past it.
     */
    private fun complete(item: WorkItem, startedAt: Long, exitCode: Int) {
        synchronized(recent) {
            served++
            recent.addFirst(Finished(item.command, startedAt, System.currentTimeMillis() - startedAt, exitCode))
            while (recent.size > RECENT_LIMIT) recent.removeLast()
        }
        inFlight.remove(item.requestId)
        item.output.done(exitCode)
    }

    /** Stops accepting and abandons what is running; the caller removes the lock and the socket. */
    fun close() {
        try {
            bound.channel().close()
        } catch (_: IOException) {
        }
        inFlight.values.forEach { it.set(true) }
        clientPool.shutdownNow()
        acceptThread.interrupt()
        workerThread.interrupt()
    }

    private fun acceptLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val channel = try {
                bound.channel().accept()
            } catch (_: ClosedChannelException) {
                return
            } catch (_: IOException) {
                if (Thread.currentThread().isInterrupted) return
                continue
            }
            try {
                clientPool.submit { serveClient(channel) }
            } catch (_: RuntimeException) {
                channel.close()
                return
            }
        }
    }

    private fun serveClient(channel: SocketChannel) {
        try {
            channel.use {
                val client = FrameChannel(channel)
                while (true) {
                    val request = FrameChannel.read(channel) ?: return
                    handleRequest(client, request)
                }
            }
        } catch (_: IOException) {
            // The peer went away or sent garbage; nothing to answer.
        }
    }

    private fun handleRequest(channel: FrameChannel, request: Map<String, Any?>) {
        val id = request["id"]?.toString() ?: ""
        when (val op = request["op"]?.toString() ?: "") {
            "ping" -> {
                channel.write(FrameChannel.stateFrame(id, state))
                channel.write(FrameChannel.doneFrame(id, 0))
            }
            "status" -> {
                channel.write(FrameChannel.statusFrame(id, state, workQueue.size, System.currentTimeMillis() - startedAt,
                    current?.first?.requestId ?: "", libraries(), emptyMap(), LockFile.HOST_INTELLIJ))
                channel.write(FrameChannel.doneFrame(id, 0))
            }
            "cancel" -> {
                val target = request["targetId"]?.toString() ?: ""
                val accepted = target.isNotEmpty() && cancelTask(target)
                channel.write(FrameChannel.cancelAckFrame(id, target, accepted))
                channel.write(FrameChannel.doneFrame(id, if (accepted) 0 else 1))
            }
            "shutdown" -> {
                channel.write(FrameChannel.doneFrame(id, 0))
                // Off this thread: stopping closes the client pool this very thread belongs to.
                daemonThread(onShutdown, "arend-ide-daemon-shutdown").start()
            }
            "cli" -> queue(id, (request["args"] as? List<*>)?.map { it.toString() } ?: emptyList(),
                (request["cwd"] as? String)?.takeIf { it.isNotEmpty() }, channel)
            "refresh" -> queue(id, null, null, channel)
            else -> {
                channel.write(FrameChannel.errorFrame(id, "unknown op: $op"))
                channel.write(FrameChannel.doneFrame(id, 1))
            }
        }
    }

    private fun queue(id: String, args: List<String>?, cwd: String?, channel: FrameChannel) {
        val item = WorkItem(id, args, cwd, channel, describe(args))
        inFlight[item.requestId] = item.cancel
        workQueue.add(item)
        onActivity()
    }

    /** Abandons [target], running or queued; a queued one is answered here, since the worker never sees it. */
    private fun cancelTask(target: String): Boolean {
        val cancel = inFlight[target] ?: return false
        cancel.set(true)
        // A request id names one request, so at most one is queued under it.
        val queued = workQueue.firstOrNull { it.requestId == target }
        if (queued != null && workQueue.remove(queued)) {
            queued.output.err("[CANCELLED]\n")
            complete(queued, System.currentTimeMillis(), FrameChannel.CANCELLED_EXIT_CODE)
            onActivity()
        }
        return true
    }

    private fun workerLoop() {
        while (!Thread.currentThread().isInterrupted) {
            val item = try {
                workQueue.take()
            } catch (_: InterruptedException) {
                return
            }
            val itemStartedAt = System.currentTimeMillis()
            current = item to itemStartedAt
            onActivity()
            val output = item.output
            var exitCode = 1
            try {
                if (!item.cancel.get()) {
                    exitCode = if (item.args == null) runner.refresh(output, item.cancel) else runner.run(item.args, item.cwd, output, item.cancel)
                }
            } catch (e: Throwable) {
                if (e is InterruptedException) Thread.currentThread().interrupt()
                output.err("[ERROR] ${e.javaClass.simpleName}: ${e.message}\n")
            } finally {
                if (item.cancel.get()) {
                    output.err("[CANCELLED]\n")
                    exitCode = FrameChannel.CANCELLED_EXIT_CODE
                }
                current = null
                complete(item, itemStartedAt, exitCode)
                onActivity()
            }
        }
    }

    companion object {
        /** How many finished requests [activity] remembers. */
        const val RECENT_LIMIT = 20

        private fun daemonThread(body: Runnable, name: String) = Thread(body, name).apply { isDaemon = true }
    }
}
