package org.arend.arc

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.util.IconUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.arend.ArendIcons
import org.arend.ArendTestBase
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.module.config.ArendModuleConfigService
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.settings.ArendProjectSettings
import org.arend.typechecking.ArendBinaryCacheService
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.util.ArendBundle
import java.util.EnumSet
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
        runBlocking(Dispatchers.Default) { ArcViewPrinter.prepare(project, file)?.text }

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
        // Editor tabs show the .arc icon, as the project view does, faded while there is no .arc on disk
        assertSame(ArendIcons.ARC_FILE, file.fileType.icon)
        assertSame(ArendIcons.ARC_FILE_NOT_ON_DISK, IconUtil.computeFileIcon(file, 0, project))
        assertSame(ArendIcons.ARC_FILE_NOT_ON_DISK, psiFile!!.getIcon(0))
    }

    private fun withBinariesDir(action: () -> Unit) {
        val config = ArendModuleConfigService.getInstance(module)!!
        config.flaggedBinariesDir = "bin"
        try {
            action()
        } finally {
            config.flaggedBinariesDir = null
        }
    }

    /**
     * A view stands for the .arc of its module on disk, if there is one, and is shown faded if there is none. Deleting
     * it deletes that file, or the directory with it, and nothing else: the view stays, as it shows the server.
     */
    fun `test view stands for its arc on disk`() = withBinariesDir {
        InlineFile("\\func g => 1")
        myFixture.addFileToProject("Dir/Inner.ard", "\\func i => 2")
        val file = view()
        val inner = ArcFileSystem.getInstance().findFile(project, module.name, ModulePath("Dir", "Inner"))
        assertNull(file.fileOnDisk)

        val arc = myFixture.tempDirFixture.createFile("bin/Main.arc")
        val innerArc = myFixture.tempDirFixture.createFile("bin/Dir/Inner.arc")
        assertEquals(arc, file.fileOnDisk)
        assertEquals(innerArc, inner.fileOnDisk)
        assertSame(ArendIcons.ARC_FILE, IconUtil.computeFileIcon(file, 0, project))
        assertSame(ArendIcons.ARC_FILE, PsiManager.getInstance(project).findFile(file)!!.getIcon(0))

        val provider = ArcDeleteProvider(project, listOf(arc))
        assertTrue(provider.canDeleteElement(DataContext.EMPTY_CONTEXT))
        assertFalse(ArcDeleteProvider(project, emptyList()).canDeleteElement(DataContext.EMPTY_CONTEXT))
        TestDialogManager.setTestDialog(TestDialog.OK, testRootDisposable)
        provider.deleteElement(DataContext.EMPTY_CONTEXT)
        assertFalse(arc.isValid)
        assertNull(file.fileOnDisk)
        assertTrue(file.isValid)
        assertSame(ArendIcons.ARC_FILE_NOT_ON_DISK, IconUtil.computeFileIcon(file, 0, project))

        // A file in a selected directory goes with it
        val dir = innerArc.parent
        ArcDeleteProvider(project, listOf(innerArc, dir)).deleteElement(DataContext.EMPTY_CONTEXT)
        assertFalse(dir.isValid)
        assertNull(inner.fileOnDisk)
        assertTrue(inner.isValid)
        assertNotNull(ArendModuleConfigService.getInstance(module)!!.binariesDirFile)
    }

    // Cancelling the confirmation deletes nothing
    fun `test cancelled deletion keeps the arc`() = withBinariesDir {
        InlineFile("\\func g => 1")
        val arc = myFixture.tempDirFixture.createFile("bin/Main.arc")
        TestDialogManager.setTestDialog(TestDialog.NO, testRootDisposable)
        ArcDeleteProvider(project, listOf(arc)).deleteElement(DataContext.EMPTY_CONTEXT)
        assertTrue(arc.isValid)
        assertEquals(arc, view().fileOnDisk)
    }

    /**
     * There is a view for every .ard file and only for them: one whose source is deleted is not valid. On the EDT, where
     * looking files up in the VFS is a slow operation, only the source found last and the files the VFS has loaded are
     * checked; a view whose source was never looked up is settled by looking it up elsewhere.
     */
    fun `test view of a module without a source is not valid`() {
        val source = InlineFile("\\func g => 1").psiFile.virtualFile
        val file = view()
        assertTrue(file.isValid)
        runWriteAction { source.delete(this) }
        assertFalse(file.isValid)
        val missing = view("Missing")
        assertFalse(runBlocking(Dispatchers.Default) { readAction { missing.isValid } })
        assertFalse(missing.isValid)
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

    // A view shows the definitions of \where blocks and of class bodies too, and follows the server in them
    fun `test view shows where and class members`() {
        InlineFile("\\func f => g \\where \\func g => 7\n\\class C (n : Nat) {\n  \\func m => 8\n}")
        typecheck()
        val file = view()
        val service = project.service<ArcViewService>()
        val text = prepare(file)!!
        assertTrue(text, text.contains("\\where"))
        assertTrue(text, text.contains("=> 7"))
        assertTrue(text, text.contains("=> 8"))
        assertFalse(service.isOutdated(file, true))

        // Only m is typechecked again: nothing depends on it
        InlineFile("\\func f => g \\where \\func g => 7\n\\class C (n : Nat) {\n  \\func m => 10\n}")
        typecheck()
        assertTrue("m is typechecked again", service.isOutdated(file, false))
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
                PrintedView("\\func first => 1")
            } else {
                PrintedView("\\func second => 2")
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

    // The print options of the views are settings of the shared project, so a test that changes them restores them
    private fun withPrintOptions(action: (EnumSet<PrettyPrinterFlag>) -> Unit) {
        val options = project.service<ArendProjectSettings>().arcViewPrintingOptionsFilterSet
        val saved = EnumSet.copyOf(options)
        try {
            action(options)
        } finally {
            options.clear()
            options.addAll(saved)
        }
    }

    /**
     * The floating toolbar of a view has the print options of the views, as the Arend Messages tool window has them.
     * Changing one prints the shown view again with it.
     */
    fun `test view is printed again with new print options`() = withPrintOptions {
        InlineFile("\\func id {A : \\Type} (a : A) => a\n\\func g => id 1")
        typecheck()
        val file = view()
        val service = project.service<ArcViewService>()
        val document = show(file)
        waitForText(document, "id 1")
        assertFalse(document.text, document.text.contains("{Nat}"))
        assertFalse(service.isOutdated(file, false))

        val event = TestActionEvent.createTestEvent(SimpleDataContext.getProjectContext(project))
        val printOptions = ArcViewToolbarActionGroup().getChildren(event).single() as ActionGroup
        val implicitArgs = printOptions.getChildren(event).filterIsInstance<ToggleAction>()
            .single { it.templatePresentation.text == ArendPrintOptionsFilterAction.flagToString(PrettyPrinterFlag.SHOW_IMPLICIT_ARGS) }
        assertFalse(implicitArgs.isSelected(event))
        implicitArgs.setSelected(event, true)
        assertTrue("the options have changed", service.isOutdated(file, false))
        waitForText(document, "id {Nat} 1")
        assertFalse(service.isOutdated(file, false))
    }

    // Whatever the options, the view is printed
    fun `test view is printed with every print option`() = withPrintOptions { options ->
        InlineFile("\\func id {A : \\Type} (a : A) => a\n\\func g => id 1\n\\data D | con Nat\n\\func h (d : D) : Nat \\elim d | con n => n\n\\func p : 1 = 1 => idp")
        options.addAll(EnumSet.allOf(PrettyPrinterFlag::class.java))
        val text = prepare(view())!!
        assertTrue(text, text.contains("id {Nat} 1"))
        assertTrue(text, text.contains("\\func p"))
    }

    // The floating toolbar is in the editor tabs of views, and only there: not in other editors of views or of sources
    fun `test floating toolbar is in the editors of views`() {
        val source = InlineFile("\\func g => 1").psiFile.virtualFile
        val provider = ArcViewFloatingToolbarProvider()
        fun isApplicable(file: VirtualFile, kind: EditorKind): Boolean {
            val document = FileDocumentManager.getInstance().getDocument(file)!!
            val editor = EditorFactory.getInstance().createEditor(document, project, file, true, kind)
            try {
                return runBlocking { provider.isApplicableAsync(SimpleDataContext.getSimpleContext(CommonDataKeys.EDITOR, editor)) }
            } finally {
                EditorFactory.getInstance().releaseEditor(editor)
            }
        }
        assertTrue(isApplicable(view(), EditorKind.MAIN_EDITOR))
        assertFalse(isApplicable(view(), EditorKind.PREVIEW))
        assertFalse(isApplicable(source, EditorKind.MAIN_EDITOR))
    }

    private fun arcFile(file: ArcVirtualFile): ArcFile {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        return PsiManager.getInstance(project).findFile(file) as ArcFile
    }

    // What the name at offset in the view links to
    private fun linkAt(file: ArcFile, offset: Int) = file.findReferenceAt(offset)!!.resolve()

    // A local is linked to its binder, and a definition of the module to where the view shows it
    fun `test names of a view link to its binders and definitions`() {
        InlineFile("\\func g (n : Nat) : Nat => n\n\\func h => g 1")
        typecheck()
        val file = view()
        val document = show(file)
        waitForText(document, "=> g 1")
        val psiFile = arcFile(file)
        val text = document.text

        val binder = linkAt(psiFile, text.indexOf("=> n") + 3)!!
        assertEquals(text.indexOf("(n") + 1, binder.textOffset)
        assertSame(psiFile, binder.containingFile)

        val definition = linkAt(psiFile, text.indexOf("=> g 1") + 3)!!
        assertEquals(text.indexOf("\\func g") + "\\func ".length, definition.textOffset)
        assertSame(psiFile, definition.containingFile)
    }

    /**
     * A definition of another module is linked to the view of its module. If that view is not printed yet, following the
     * link shows it, and goes to the definition once it is printed.
     */
    fun `test names of a view link to the views of other modules`() {
        myFixture.addFileToProject("Other.ard", "\\func a => 0\n\\func o => 2")
        InlineFile("\\import Other\n\\func h => o")
        typecheck(listOf(ModulePath("Other"), ModulePath("Main")))
        val file = view()
        val document = show(file)
        waitForText(document, "=> o")
        val psiFile = arcFile(file)
        val text = document.text

        val other = view("Other")
        assertSame(PsiManager.getInstance(project).findFile(other), linkAt(psiFile, text.indexOf("\\import Other") + "\\import ".length))

        val pending = linkAt(psiFile, text.indexOf("=> o") + 3)
        assertInstanceOf(pending, ArcPendingTarget::class.java)
        (pending as ArcPendingTarget).navigate(true)
        val otherDocument = FileDocumentManager.getInstance().getDocument(other)!!
        waitForText(otherDocument, "\\func o")
        val declaration = otherDocument.text.indexOf("\\func o") + "\\func ".length
        PlatformTestUtil.waitWithEventsDispatching("The caret did not go to o", {
            FileEditorManager.getInstance(project).selectedTextEditor?.let { it.document == otherDocument && it.caretModel.offset == declaration } == true
        }, 60)

        // Once that view is printed, the link is to its definition
        val target = linkAt(arcFile(file), text.indexOf("=> o") + 3)!!
        assertSame(arcFile(other), target.containingFile)
        assertEquals(declaration, target.textOffset)
    }
}
