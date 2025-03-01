package org.arend.typechecking

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import org.arend.ext.error.ErrorReporter
import org.arend.library.SourceLibrary
import org.arend.module.ModuleLocation
import org.arend.psi.ArendFile
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.FileUtils
import org.arend.util.getRelativeFile


@Service(Service.Level.PROJECT)
class BinaryFileSaver(private val project: Project) {
    private val typecheckedModules = LinkedHashSet<ArendFile>()

    init {
        // TODO: Replace with AsyncFileListener?
        project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (typecheckedModules.isEmpty()) {
                    return
                }

                val savedFiles = HashSet<VirtualFile>()
                for (event in events) {
                    val file = (if (event is VFileContentChangeEvent && event.isFromSave) PsiManager.getInstance(project).findFile(event.file) as? ArendFile else null) ?: continue
                    synchronized(project) {
                        if (typecheckedModules.remove(file)) {
                            saveFile(file, NotificationErrorReporter(project), savedFiles)
                        }
                    }
                }
                updateFiles(savedFiles)
            }
        })

        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosing(closedProject: Project) {
                if (closedProject == project) {
                    saveAll()
                }
            }
        })
    }

    private fun updateFiles(savedFiles: Set<VirtualFile>) {
        // We need to update them because we save files using Java API and not the VFS because the latter is very slow for some reason
        // TODO: Probably a better way is to save files using VFS immediately after typechecking
        VfsUtil.markDirtyAndRefresh(true, false, false, *savedFiles.toTypedArray())
    }

    private fun saveFile(file: ArendFile, errorReporter: ErrorReporter, savedFiles: HashSet<VirtualFile>) {
        val moduleLocation = file.moduleLocation ?: return
        if (moduleLocation.locationKind != ModuleLocation.LocationKind.SOURCE) {
            return
        }
        /* TODO[server2]
        val library = typeCheckingService.libraryManager.getRegisteredLibrary(moduleLocation.libraryName) as? SourceLibrary ?: return
        if (library.supportsPersisting()) {
            if (runReadAction { library.persistModule(moduleLocation.modulePath, errorReporter) }) {
                val config = (library as? ArendRawLibrary)?.config ?: return
                val root = config.root ?: return
                val binDir = config.binariesDirList ?: return
                val vFile = root.getRelativeFile(binDir + moduleLocation.modulePath.toList(), FileUtils.SERIALIZED_EXTENSION) ?: return
                savedFiles.add(vFile)
            }
        }
        */
    }

    fun addToQueue(file: ArendFile) {
        synchronized(project) {
            typecheckedModules.add(file)
        }
    }

    fun saveAll() {
        if (typecheckedModules.isEmpty()) {
            return
        }

        synchronized(project) {
            val savedFiles = HashSet<VirtualFile>()
            for (file in typecheckedModules) {
                ApplicationManager.getApplication().executeOnPooledThread {
                    saveFile(file, NotificationErrorReporter(project), savedFiles)
                }
            }
            typecheckedModules.clear()
            updateFiles(savedFiles)
        }
    }
}