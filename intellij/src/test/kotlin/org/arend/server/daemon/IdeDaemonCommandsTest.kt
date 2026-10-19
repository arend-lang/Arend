package org.arend.server.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.PlatformTestUtil
import org.arend.ArendTestBase
import org.arend.daemon.server.DaemonServer
import org.arend.typechecking.computation.BooleanCancellationIndicator
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/** A typecheck the IDE's daemon answers: what it checks, what it prints and how it exits. */
class IdeDaemonCommandsTest : ArendTestBase() {
    private class Answer(val exitCode: Int, val out: String, val err: String) {
        val text get() = out + err
    }

    /** Runs `arend [args]` on the daemon off the EDT, as its worker does, which a sync waits on. */
    private fun daemon(vararg args: String): Answer {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val output = object : DaemonServer.Output {
            override fun requestId() = "test"

            override fun stream(stdout: Boolean) = if (stdout) out else err

            override fun out(text: String) = out.writeBytes(text.toByteArray(StandardCharsets.UTF_8))

            override fun err(text: String) = err.writeBytes(text.toByteArray(StandardCharsets.UTF_8))

            override fun done(exitCode: Int) {}
        }
        val commands = IdeDaemonCommands(project) { mapOf("/test/arend.yaml" to library.name) }
        val future = ApplicationManager.getApplication().executeOnPooledThread<Int> {
            commands.run(DaemonServer.Request(args.toList(), null, output, BooleanCancellationIndicator()))
        }
        val exitCode = PlatformTestUtil.waitForFuture(future, 60_000)
        return Answer(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8))
    }

    fun testAnErrorFailsTheModule() {
        myFixture.configureByText("Main.ard", """
            \func ok : Nat => 0
            \func bad : Nat => \Sigma
        """.trimIndent())
        val answer = daemon("Main")
        assertEquals(answer.text, 1, answer.exitCode)
        assertTrue(answer.text, answer.text.contains("[ERROR]"))
    }

    fun testADefinitionIsCheckedAlone() {
        myFixture.configureByText("Main.ard", """
            \func ok : Nat => 0
            \func bad : Nat => \Sigma
        """.trimIndent())
        val answer = daemon("Main:ok")
        assertEquals(answer.text, 0, answer.exitCode)
        assertFalse(answer.text, answer.text.contains("[ERROR]"))
    }

    fun testAGoalIsReportedWithoutFailing() {
        myFixture.configureByText("Main.ard", """
            \func f (n : Nat) : n = n => {?}
        """.trimIndent())
        val answer = daemon("Main")
        assertEquals(answer.text, 0, answer.exitCode)
        assertTrue(answer.text, answer.text.contains("[GOAL]"))
    }

    fun testTheLibraryIsCheckedWithNoTarget() {
        myFixture.configureByText("Main.ard", """
            \func bad : Nat => \Sigma
        """.trimIndent())
        val answer = daemon()
        assertEquals(answer.text, 1, answer.exitCode)
        assertTrue(answer.text, answer.text.contains("[ERROR]"))
    }

    fun testADefinitionTheModuleDoesNotDeclareFails() {
        myFixture.configureByText("Main.ard", """
            \func ok : Nat => 0
        """.trimIndent())
        val answer = daemon("Main:nope")
        assertEquals(answer.text, 1, answer.exitCode)
    }

    /** Two definitions of one module are both checked: the second used to replace the first. */
    fun testTwoDefinitionsOfOneModuleAreBothChecked() {
        myFixture.configureByText("Main.ard", """
            \func ok : Nat => 0
            \func bad : Nat => \Sigma
        """.trimIndent())
        val answer = daemon("Main:ok", "Main:bad")
        assertEquals(answer.text, 1, answer.exitCode)
        assertTrue(answer.text, answer.text.contains("[ERROR]"))
        assertEquals(1, daemon("Main:bad", "Main:ok").exitCode)
    }

    /**
     * What a typecheck finds goes where `arend` puts it: rendered by the same code, and to stderr only
     * when severe -- which a diagnostic located in the source, a goal or an error, is not.
     */
    fun testDiagnosticsGoWhereTheClisDo() {
        myFixture.configureByText("Main.ard", """
            \func f (n : Nat) : n = n => {?}
            \func bad : Nat => \Sigma
        """.trimIndent())
        val answer = daemon("Main")
        assertTrue(answer.out, answer.out.contains("[GOAL] Main.ard:1:30: Goal"))
        assertTrue(answer.out, answer.out.contains("[ERROR] Main.ard:2:20: Type mismatch"))
        assertEquals(answer.err, "", answer.err.trim())
    }
}
