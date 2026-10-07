package org.arend.arc

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.arend.ArendTestBase
import org.arend.util.ArendBundle
import java.io.File
import java.nio.file.Files

class ArcTest : ArendTestBase() {
    override var dataPath = "org/arend/arc"

    private fun waitForDecompilation() {
        PlatformTestUtil.waitWithEventsDispatching("Decompilation did not finish", { !project.service<ArcDecompilationService>().isDecompiling }, 60)
    }

    /**
     * Runs [action] on a copy of Test.arc on disk, so that the copy has no document yet, and editing its
     * document cannot leak into the other tests or onto the fixture.
     */
    private fun withArcCopy(name: String, action: (VirtualFile) -> Unit) {
        val dir = Files.createTempDirectory("arc-test")
        try {
            val copy = Files.copy(File("$testDataPath/Test.arc").toPath(), dir.resolve(name))
            action(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(copy)!!)
        } finally {
            waitForDecompilation()
            dir.toFile().deleteRecursively()
        }
    }

    fun `test decompile arc file`() {
        val file = LocalFileSystem.getInstance().findFileByIoFile(File("$testDataPath/Test.arc"))!!
        // decompile takes read actions, so it runs off the EDT
        assertEquals("\\func f : Prelude.Nat => 0", runBlocking(Dispatchers.Default) { ArcFileDecompiler.decompile(project, file) })
    }

    /**
     * The document of an .arc file starts as a placeholder; the decompilation runs in the background and then
     * reparses the file, which puts the decompiled text into the document and rebuilds the PSI from it.
     */
    fun `test arc file is decompiled in the background`() = withArcCopy("Test.arc") { file ->
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        assertEquals(ArendBundle.message("arend.arc.decompiling.placeholder", file.name), document.text)

        waitForDecompilation()
        assertEquals("\\func f : Prelude.Nat => 0", document.text)
        val psiFile = PsiManager.getInstance(project).findFile(file)
        assertInstanceOf(psiFile, ArcFile::class.java)
        assertSame(psiFile, PsiDocumentManager.getInstance(project).getPsiFile(document))
        assertEquals(document.text, psiFile!!.text)
        assertEquals(document.textLength, psiFile.textLength)
        assertEquals(document.text, psiFile.node.text)

        myFixture.openFileInEditor(file)
        myFixture.doHighlighting()
    }

    /**
     * Typechecking the module from the notification panel calls `reloadBinaryFiles`, which re-decompiles
     * the .arc into its existing document. A binary document is never committed into PSI, so an
     * [ArcFile] that was already parsed keeps its old tree; its text must follow the document instead of
     * failing the platform's PSI/document text-mismatch assertion.
     */
    fun `test arc file text follows a document replaced under it`() = withArcCopy("Reloaded.arc") { file ->
        FileDocumentManager.getInstance().getDocument(file)!!
        waitForDecompilation()

        val psiFile = PsiManager.getInstance(project).findFile(file)!!
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val newText = psiFile.node.text + "\n\\func g : Prelude.Nat => 1"

        runWriteAction {
            document.setReadOnly(false)
            document.setText(newText)
            document.setReadOnly(true)
        }
        assertEquals(newText, psiFile.text)
        assertEquals(newText.length, psiFile.textLength)
    }
}
