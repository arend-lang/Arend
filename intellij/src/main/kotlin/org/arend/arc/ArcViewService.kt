package org.arend.arc

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arend.naming.reference.GlobalReferable
import org.arend.util.ArendBundle
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * Prepares .arc views ([ArcVirtualFile]) in the background, with a cancellable progress indicator.
 *
 * A view shows its module as it is in the server, not the .arc on disk: preparing it loads the binary cache and
 * typechecks the module, as typechecking it manually would, and prints it; the result becomes the text of the view,
 * and its document is reloaded. Only a view that is shown is prepared: when its editor is activated, which also loads
 * or typechecks the module if the server has not, and, while its editor is selected, when the server changes (see
 * [serverChanged]), its library is registered (see [libraryRegistered]) or the print options of the views change (see
 * [printOptionsChanged]). Other views wait for their activation.
 */
@Service(Service.Level.PROJECT)
class ArcViewService(private val project: Project, private val coroutineScope: CoroutineScope) : Disposable {
    private val lock = Any()
    // Views being prepared; the value says whether the view was requested again in the meantime
    private val running = HashMap<ArcVirtualFile, Boolean>()
    private val cancelled = ConcurrentHashMap.newKeySet<VirtualFile>()
    // What each view shows, see ArcViewPrinter.isOutdated
    private val shown = ConcurrentHashMap<VirtualFile, ArcViewPrinter.Shown>()
    // Preparations typecheck and load modules on the shared server, so they run one at a time
    private val mutex = Mutex()
    private var printer: suspend (Project, ArcVirtualFile) -> PrintedView? = { project, file -> ArcViewPrinter.prepare(project, file) }
    // The definitions to go to once their views are printed, see ArcPendingTarget
    private val pendingNavigation = ConcurrentHashMap<ArcVirtualFile, GlobalReferable>()

    fun isCancelled(file: VirtualFile) = cancelled.contains(file)

    internal fun setShown(file: VirtualFile, state: ArcViewPrinter.Shown) {
        shown[file] = state
    }

    fun fileClosed(file: VirtualFile) {
        shown.remove(file)
    }

    @get:TestOnly
    val isPreparing: Boolean
        get() = synchronized(lock) { running.isNotEmpty() }

    @TestOnly
    fun isOutdated(file: ArcVirtualFile, activated: Boolean): Boolean =
        runReadAction { ArcViewPrinter.isOutdated(project, file, shown[file], activated) }

    @TestOnly
    fun setPrinter(printer: suspend (Project, ArcVirtualFile) -> PrintedView?, disposable: Disposable) {
        val previous = this.printer
        this.printer = printer
        Disposer.register(disposable) { this.printer = previous }
    }

    /**
     * Prepares [file] again if its view is out of date (see [ArcViewPrinter.isOutdated]). A cancelled preparation is
     * not repeated: the notification panel offers that.
     */
    fun refreshIfOutdated(file: ArcVirtualFile, activated: Boolean) {
        if (cancelled.contains(file)) return
        // A view that is being prepared is shown when the preparation finishes
        if (activated && synchronized(lock) { running.containsKey(file) }) return
        coroutineScope.launch {
            if (readAction { file.isValid && ArcViewPrinter.isOutdated(project, file, shown[file], activated) }) {
                prepare(file)
            }
        }
    }

    // After the server has changed: the views in the selected editors are brought up to date, the others when they are activated
    fun serverChanged() = refreshSelected(false)

    // Until its library is registered, a view has nothing to show; the shown ones are prepared now, the others when they are activated
    fun libraryRegistered() = refreshSelected(true)

    // The views in the selected editors are printed again with the new options, as activating them would, the others when they are activated
    fun printOptionsChanged() = refreshSelected(true)

    private fun refreshSelected(activated: Boolean) {
        coroutineScope.launch {
            val files = withContext(Dispatchers.EDT) {
                FileEditorManager.getInstance(project).selectedFiles.filterIsInstance<ArcVirtualFile>()
            }
            for (file in files) {
                refreshIfOutdated(file, activated)
            }
        }
    }

    fun prepare(file: ArcVirtualFile) {
        synchronized(lock) {
            if (running.containsKey(file)) {
                running[file] = true
                return
            }
            running[file] = false
        }
        if (cancelled.remove(file)) {
            EditorNotifications.getInstance(project).updateNotifications(file)
        }

        coroutineScope.launch {
            try {
                do {
                    val view = withBackgroundProgress(project, ArendBundle.message("arend.arc.preparing", file.name)) {
                        mutex.withLock { printer(project, file) }
                    }
                    // A request that came during the preparation may have made the text outdated. It is shown anyway, and
                    // replaced by the next preparation: preparations wait for each other, so holding it back until then can
                    // keep a view empty for as long as the others take.
                    val again = synchronized(lock) { running.put(file, false) == true }
                    // Nothing to show yet, or nothing asks for the text of a view whose source was deleted
                    if (view != null && file.isValid) {
                        file.setText(view)
                        withContext(Dispatchers.EDT) {
                            FileDocumentManager.getInstance().getCachedDocument(file)?.let {
                                FileDocumentManager.getInstance().reloadFromDisk(it)
                            }
                            navigatePending(file)
                        }
                    }
                } while (again || continueOrFinish(file))
            } catch (e: Throwable) {
                synchronized(lock) { running.remove(file) }
                // Cancelled in the progress UI rather than together with the project
                if (e is CancellationException && currentCoroutineContext().isActive) {
                    cancelled.add(file)
                    EditorNotifications.getInstance(project).updateNotifications(file)
                } else {
                    throw e
                }
            }
        }
    }

    /**
     * Shows [file] and goes to the definition of [referable] in it, once it is printed: activating the view prints it.
     * If the view does not show the definition, goes to its source.
     */
    fun navigateWhenPrinted(file: ArcVirtualFile, referable: GlobalReferable, requestFocus: Boolean) {
        pendingNavigation[file] = referable
        FileEditorManager.getInstance(project).openFile(file, requestFocus)
        if (file.hasText) navigatePending(file)
    }

    private fun navigatePending(file: ArcVirtualFile) {
        val referable = pendingNavigation.remove(file) ?: return
        val offset = file.links.declarationOf(referable)
        if (offset != null) {
            OpenFileDescriptor(project, file, offset).navigate(true)
        } else {
            (ArcReferences.sourceOf(referable) as? Navigatable)?.navigate(true)
        }
    }

    // Takes a request that came during the reload, or else unregisters the preparation of the view
    private fun continueOrFinish(file: ArcVirtualFile): Boolean = synchronized(lock) {
        if (running[file] == true) {
            running[file] = false
            true
        } else {
            running.remove(file)
            false
        }
    }

    override fun dispose() {
        ArcFileSystem.getInstance().forget(project)
    }
}
