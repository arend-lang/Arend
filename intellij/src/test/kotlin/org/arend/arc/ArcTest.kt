package org.arend.arc

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.arend.ArendTestBase
import org.arend.core.definition.FunctionDefinition
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.TCDefReferable
import org.arend.server.ArendServerService
import org.arend.typechecking.ArendBinaryCacheService
import org.arend.util.ArendBundle
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

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
     * A module with a source is shown as it is in the server, even with nothing typechecked in it. Loading its .arc
     * instead would put the group of the .arc in the place of the module, with a timestamp that outranks every
     * later update from the source.
     */
    fun `test arc file of a module with a source does not replace the module`() {
        InlineFile("-- nothing to typecheck")
        typecheck()
        val server = project.service<ArendServerService>().server
        val location = ModuleLocation(module.name, ModuleLocation.LocationKind.SOURCE, ModulePath("Main"))
        val group = server.getRawGroup(location)!!

        withArcCopy("Main.arc") { file ->
            assertEquals("", runBlocking(Dispatchers.Default) { ArcFileDecompiler.decompile(project, file) })
            assertSame(group, server.getRawGroup(location))
        }
    }

    /**
     * A definition without a header (a shell left by a cache that failed to load, or one being typechecked) is not
     * printed, and does not make the module count as loaded.
     */
    fun `test arc file whose module holds a definition shell is loaded again`() = withArcCopy("Shell.arc") { file ->
        assertEquals("\\func f : Prelude.Nat => 0", runBlocking(Dispatchers.Default) { ArcFileDecompiler.decompile(project, file) })
        val group = project.service<ArendServerService>().server
            .getRawGroup(ModuleLocation(module.name, ModuleLocation.LocationKind.SOURCE, ModulePath("Shell")))!!
        val f = group.statements.firstNotNullOf { it.group?.referable as? TCDefReferable }
        f.typechecked = FunctionDefinition(f)

        assertEquals("\\func f : Prelude.Nat => 0", runBlocking(Dispatchers.Default) { ArcFileDecompiler.decompile(project, file) })
    }

    // Modules whose .arc is up to date come from the binary cache instead of being typechecked, so it is loaded first
    fun `test arc file of a module with a source loads the binary cache`() {
        InlineFile("\\func g => 1")
        val cache = project.service<ArendBinaryCacheService>()
        cache.invalidate(listOf(module.name))

        withArcCopy("Main.arc") { file ->
            runBlocking(Dispatchers.Default) { ArcFileDecompiler.decompile(project, file) }
            assertTrue(cache.isLoaded(module.name))
        }
    }

    /**
     * Before its library is registered, an .arc has nothing to show: it keeps its placeholder, and the registration
     * (reloadBinaryFiles) decompiles it again.
     */
    fun `test arc file of a library that is not registered keeps the placeholder`() = withArcCopy("Test.arc") { file ->
        val config = ArendModuleConfigService.getInstance(module)!!
        val document = try {
            config.isInitialized = false
            FileDocumentManager.getInstance().getDocument(file)!!.also { waitForDecompilation() }
        } finally {
            config.isInitialized = true
        }
        assertEquals(ArendBundle.message("arend.arc.decompiling.placeholder", file.name), document.text)

        FileDocumentManager.getInstance().reloadBinaryFiles()
        waitForDecompilation()
        assertEquals("\\func f : Prelude.Nat => 0", document.text)
    }

    /**
     * Decompilations wait for each other, so a result held back because the file was requested again meanwhile
     * would leave the file empty for as long as the others take: it is shown, then replaced by the next one.
     */
    fun `test result is shown even if the file is requested again meanwhile`() = withArcCopy("Again.arc") { file ->
        val service = project.service<ArcDecompilationService>()
        val runs = AtomicInteger()
        val firstMayFinish = CompletableDeferred<Unit>()
        service.setDecompiler({ _, _ ->
            if (runs.incrementAndGet() == 1) {
                firstMayFinish.await()
                "\\func first => 1"
            } else {
                "\\func second => 2"
            }
        }, testRootDisposable)

        val document = FileDocumentManager.getInstance().getDocument(file)!!
        // Only changes of the text: the reloadBinaryFiles of the fixture's registration can come in between, and
        // reload the same text
        val shown = mutableListOf<String>()
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (shown.lastOrNull() != document.text) shown += document.text
            }
        }, testRootDisposable)
        service.decompile(file)
        firstMayFinish.complete(Unit)

        waitForDecompilation()
        assertEquals(listOf("\\func first => 1", "\\func second => 2"), shown)
    }

    // A decompilation waits for the previous one, and the .arc can be deleted in the meantime
    fun `test decompile deleted arc file`() = withArcCopy("Deleted.arc") { file ->
        runWriteAction { file.delete(this) }
        assertEquals("", runBlocking(Dispatchers.Default) { ArcFileDecompiler.decompile(project, file) })
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
     * Whatever was computed for the placeholder in the meantime (the folding of an editor being opened) checks
     * the modification stamp before it is applied, so the decompiled text must come with a new one. The document
     * must not become unsaved either, or saving it would write the decompiled text into the .arc.
     */
    fun `test decompiled text comes with a new modification stamp`() = withArcCopy("Test.arc") { file ->
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val placeholderStamp = document.modificationStamp

        waitForDecompilation()
        assertEquals("\\func f : Prelude.Nat => 0", document.text)
        assertFalse(document.modificationStamp == placeholderStamp)
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
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
