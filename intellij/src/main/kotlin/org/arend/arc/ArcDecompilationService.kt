package org.arend.arc

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.EditorNotifications
import com.intellij.util.FileContentUtilCore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arend.util.ArendBundle
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

/**
 * Decompiles .arc files in the background, with a progress indicator.
 *
 * The platform asks [ArcFileDecompiler] for the text of an .arc file synchronously, when it creates the
 * document or reloads it, but decompiling may typecheck the module first. So that call only starts a
 * decompilation and answers with the current text of the document, or a placeholder for a new one. When the
 * decompilation finishes, the file is reparsed, and the platform asks again and gets the result.
 */
@Service(Service.Level.PROJECT)
class ArcDecompilationService(private val project: Project, private val coroutineScope: CoroutineScope) {
    private val lock = Any()
    // Files being decompiled; the value says whether the file was requested again in the meantime
    private val running = HashMap<VirtualFile, Boolean>()
    private val results = ConcurrentHashMap<VirtualFile, String>()
    private val cancelled = ConcurrentHashMap.newKeySet<VirtualFile>()
    // Decompilations typecheck and load modules on the shared server, so they run one at a time
    private val mutex = Mutex()

    fun getText(file: VirtualFile): String {
        results.remove(file)?.let { return it }
        decompile(file)
        return FileDocumentManager.getInstance().getCachedDocument(file)?.text
            ?: ArendBundle.message("arend.arc.decompiling.placeholder", file.name)
    }

    fun isCancelled(file: VirtualFile) = cancelled.contains(file)

    @get:TestOnly
    val isDecompiling: Boolean
        get() = synchronized(lock) { running.isNotEmpty() }

    fun decompile(file: VirtualFile) {
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
                    val text = withBackgroundProgress(project, ArendBundle.message("arend.arc.decompiling", file.name)) {
                        mutex.withLock { ArcFileDecompiler.decompile(project, file) }
                    }
                    // A request that came while decompiling may have made the text outdated
                    val outdated = synchronized(lock) { running.put(file, false) == true }
                    if (!outdated) {
                        results[file] = text
                        withContext(Dispatchers.EDT) {
                            FileContentUtilCore.reparseFiles(listOf(file))
                        }
                    }
                } while (outdated || continueOrFinish(file))
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

    // Takes a request that came during the reparse, or else unregisters the decompilation of the file
    private fun continueOrFinish(file: VirtualFile): Boolean = synchronized(lock) {
        if (running[file] == true) {
            running[file] = false
            true
        } else {
            running.remove(file)
            false
        }
    }
}
