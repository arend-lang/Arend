package org.arend.arc

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import org.arend.ArendTestBase
import java.io.File
import java.nio.file.Files

class ArcTest : ArendTestBase() {
    override var dataPath = "org/arend/arc"

    private fun arcFile() = LocalFileSystem.getInstance().findFileByIoFile(File("$testDataPath/Test.arc"))!!

    fun `test decompile arc file`() {
        assertEquals("\\func f : Prelude.Nat => 0", ArcFileDecompiler.decompile(arcFile()))
    }

    fun `test arc file PSI agrees with its document`() {
        val file = arcFile()
        val psiFile = PsiManager.getInstance(project).findFile(file)
        assertInstanceOf(psiFile, ArcFile::class.java)
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        assertEquals("\\func f : Prelude.Nat => 0", document.text)
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
    fun `test arc file text follows a document replaced under it`() {
        // A copy on disk, so that editing its document cannot leak into the other tests or onto the fixture
        val dir = Files.createTempDirectory("arc-test")
        try {
            val copy = Files.copy(File("$testDataPath/Test.arc").toPath(), dir.resolve("Reloaded.arc"))
            val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(copy)!!
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
        } finally {
            dir.toFile().deleteRecursively()
        }
    }
}
