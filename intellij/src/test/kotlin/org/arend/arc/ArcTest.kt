package org.arend.arc

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.arend.ArendIcons
import org.arend.ArendTestBase
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.typechecking.ArendBinaryCacheService
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.util.ArendBundle
import java.util.concurrent.atomic.AtomicInteger

class ArcTest : ArendTestBase() {
    // The project is shared by the tests, and the file system keeps the views of a project with their texts
    override fun setUp() {
        super.setUp()
        ArcFileSystem.getInstance().forget(project)
    }

    private val location: ModuleLocation
        get() = ModuleLocation(module.name, ModuleLocation.LocationKind.SOURCE, ModulePath("Main"))

    private fun view(name: String = "Main") = ArcFileSystem.getInstance().findFile(project, module.name, ModulePath(name))

    private fun prepare(file: ArcVirtualFile): String? =
        // prepare takes read actions, so it runs off the EDT
        runBlocking(Dispatchers.Default) { ArcViewPrinter.prepare(project, file) }

    private fun waitForPreparation() {
        PlatformTestUtil.waitWithEventsDispatching("The view was not prepared", { !project.service<ArcViewService>().isPreparing }, 60)
    }

    // Opening a view in an editor activates it, which prepares it
    private fun show(file: ArcVirtualFile): Document {
        myFixture.openFileInEditor(file)
        return FileDocumentManager.getInstance().getDocument(file)!!
    }

    private fun waitForText(document: Document, text: String) {
        PlatformTestUtil.waitWithEventsDispatching({ "The view does not show `$text`: ${document.text}" }, { document.text.contains(text) }, 60)
        waitForPreparation()
    }

    // A view is a file of ArcFileSystem, the same one for the same module, whether its .arc on disk exists or not
    fun `test view is a file of the arc file system`() {
        InlineFile("\\func g => 1")
        val file = view()
        assertSame(file, ArcFileSystem.getInstance().findFileByPath(file.path))
        assertSame(file, view())
        assertEquals("Main.arc", file.name)
        assertFalse(file.isWritable)
        assertTrue(file.isValid)
        val psiFile = PsiManager.getInstance(project).findFile(file)
        assertInstanceOf(psiFile, ArcFile::class.java)
        // Editor tabs show the .arc icon, as the project view does
        assertSame(ArendIcons.ARC_FILE, file.fileType.icon)
        assertSame(ArendIcons.ARC_FILE, psiFile!!.getIcon(0))
    }

    // There is a view for every .ard file and only for them: one whose source is deleted is not valid
    fun `test view of a module without a source is not valid`() {
        val source = InlineFile("\\func g => 1").psiFile.virtualFile
        val file = view()
        assertTrue(file.isValid)
        runWriteAction { source.delete(this) }
        assertFalse(file.isValid)
        assertFalse(view("Missing").isValid)
    }

    /**
     * Only a view that is shown is prepared: the project view lists a view for every module, and the platform reads
     * files it does not show (editors restored but not selected), which must not typecheck their modules.
     */
    fun `test reading a view does not prepare it`() {
        InlineFile("\\func g => 1")
        val file = view()
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        PsiManager.getInstance(project).findFile(file)!!.text
        assertFalse(project.service<ArcViewService>().isPreparing)
        assertFalse(file.hasText)
        assertEquals(ArendBundle.message("arend.arc.preparing.placeholder", file.name), document.text)

        show(file)
        waitForText(document, "=> 1")
    }

    // The imports of a view are in the order of the modules in the sources, not in the order of their uses
    fun `test view imports are sorted by module path`() {
        myFixture.addFileToProject("Zeta.ard", "\\func z => 0")
        myFixture.addFileToProject("Alpha/Inner.ard", "\\func i => 1")
        myFixture.addFileToProject("Alpha.ard", "\\func a => 2")
        InlineFile("""
            \import Zeta
            \import Alpha.Inner
            \import Alpha
            \func g => (z, i, a)
        """.trimIndent())

        val imports = prepare(view())!!.lines().filter { it.startsWith("\\import") }
        assertEquals(listOf("\\import Alpha", "\\import Alpha.Inner", "\\import Zeta"), imports)
    }

    fun `test view shows the module typechecked`() {
        InlineFile("\\func g => 1")
        assertTrue(prepare(view())!!.contains("=> 1"))
    }

    /**
     * A module is shown as it is in the server, even with nothing typechecked in it. Loading its .arc instead would
     * put the group of the .arc in the place of the module, with a timestamp that outranks every later update from
     * the source.
     */
    fun `test view does not replace the module`() {
        InlineFile("-- nothing to typecheck")
        typecheck()
        val server = project.service<ArendServerService>().server
        val group = server.getRawGroup(location)!!

        assertEquals("", prepare(view()))
        assertSame(group, server.getRawGroup(location))
    }

    // Modules whose .arc is up to date come from the binary cache instead of being typechecked, so it is loaded first
    fun `test view loads the binary cache`() {
        InlineFile("\\func g => 1")
        val cache = project.service<ArendBinaryCacheService>()
        cache.invalidate(listOf(module.name))

        prepare(view())
        assertTrue(cache.isLoaded(module.name))
    }

    /**
     * Before its library is registered, a view has nothing to show: it keeps its placeholder, and the registration
     * prepares it again.
     */
    fun `test view of a library that is not registered keeps the placeholder`() {
        InlineFile("\\func g => 1")
        val file = view()
        val config = ArendModuleConfigService.getInstance(module)!!
        val document = try {
            config.isInitialized = false
            show(file).also { waitForPreparation() }
        } finally {
            config.isInitialized = true
        }
        assertEquals(ArendBundle.message("arend.arc.preparing.placeholder", file.name), document.text)

        project.service<ArcViewService>().libraryRegistered()
        waitForText(document, "=> 1")
    }

    /**
     * The document of a view starts as a placeholder; the view is prepared in the background, and the result becomes
     * the text of the view, which reloads the document and rebuilds the PSI from it.
     */
    fun `test view is prepared in the background`() {
        InlineFile("\\func g => 1")
        val file = view()
        val document = show(file)
        assertEquals(ArendBundle.message("arend.arc.preparing.placeholder", file.name), document.text)

        waitForText(document, "=> 1")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val psiFile = PsiManager.getInstance(project).findFile(file)
        assertInstanceOf(psiFile, ArcFile::class.java)
        assertSame(psiFile, PsiDocumentManager.getInstance(project).getPsiFile(document))
        assertEquals(document.text, psiFile!!.text)
        myFixture.doHighlighting()
    }

    /**
     * Whatever was computed for the placeholder in the meantime (the folding of an editor being opened) checks the
     * modification stamp before it is applied, so the text comes with a new one. The document is not unsaved either.
     */
    fun `test view text comes with a new modification stamp`() {
        InlineFile("\\func g => 1")
        val file = view()
        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val placeholderStamp = document.modificationStamp
        show(file)

        waitForText(document, "=> 1")
        assertFalse(document.modificationStamp == placeholderStamp)
        assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document))
    }

    /**
     * Preparations wait for each other, so a result held back because the view was requested again meanwhile would
     * leave the view empty for as long as the others take: it is shown, then replaced by the next one.
     */
    fun `test result is shown even if the view is requested again meanwhile`() {
        InlineFile("\\func g => 1")
        val file = view()
        val service = project.service<ArcViewService>()
        val runs = AtomicInteger()
        val firstMayFinish = CompletableDeferred<Unit>()
        service.setPrinter({ _, _ ->
            if (runs.incrementAndGet() == 1) {
                firstMayFinish.await()
                "\\func first => 1"
            } else {
                "\\func second => 2"
            }
        }, testRootDisposable)

        val document = FileDocumentManager.getInstance().getDocument(file)!!
        val shown = mutableListOf<String>()
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (shown.lastOrNull() != document.text) shown += document.text
            }
        }, testRootDisposable)
        service.prepare(file)
        // Requested again while the first preparation runs
        service.prepare(file)
        firstMayFinish.complete(Unit)

        waitForPreparation()
        assertEquals(listOf("\\func first => 1", "\\func second => 2"), shown)
    }

    /**
     * A view shows the module as it is in the server: when the module is typechecked again, the view is out of date,
     * and is brought up to date.
     */
    fun `test view follows the server`() {
        InlineFile("\\func g => 1")
        typecheck()
        val file = view()
        val service = project.service<ArcViewService>()
        val document = show(file)
        waitForText(document, "=> 1")
        assertFalse(service.isOutdated(file, true))

        InlineFile("\\func g => 2")
        typecheck()
        assertTrue(service.isOutdated(file, false))
        service.refreshIfOutdated(file, false)
        waitForText(document, "=> 2")
        assertFalse(service.isOutdated(file, true))
    }

    /**
     * A view is brought up to date automatically only once the server has typechecked its module again, so that a
     * change of the module does not make it typecheck the module. Activating the view does.
     */
    fun `test activated view typechecks a module that the server has not`() {
        InlineFile("\\func g => 1")
        typecheck()
        val file = view()
        val service = project.service<ArcViewService>()
        val document = show(file)
        waitForText(document, "=> 1")

        InlineFile("\\func g => 2")
        project.service<ArendServerService>().server.getCheckerFor(listOf(location))
            .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
        assertFalse(service.isOutdated(file, false))
        assertTrue(service.isOutdated(file, true))

        service.refreshIfOutdated(file, true)
        waitForText(document, "=> 2")
        assertFalse(service.isOutdated(file, true))
    }
}
