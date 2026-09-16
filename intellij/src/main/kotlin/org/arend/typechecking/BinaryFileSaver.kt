package org.arend.typechecking

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ext.error.ErrorReporter
import org.arend.ext.module.ModuleLocation
import org.arend.source.GZIPStreamBinarySource
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService
import org.arend.source.FileBinarySource
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.FileUtils
import org.arend.util.getRelativeFile


@Service(Service.Level.PROJECT)
class BinaryFileSaver(private val project: Project) {
    private val typecheckedModules = LinkedHashSet<ArendFile>()

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
        val config = file.arendLibrary ?: return
        val root = config.root ?: return
        val binDir = config.binariesDir ?: return
        val binDirList = binDir.split("/").filter { it.isNotEmpty() }
        val server = project.service<ArendServerService>().server
        val binarySource = GZIPStreamBinarySource(FileBinarySource(config.binariesDirFile?.toNioPath(), moduleLocation))
        if (runReadAction { binarySource.persist(server, errorReporter) }) {
            val vFile = root.getRelativeFile(binDirList + moduleLocation.modulePath.toList(), FileUtils.SERIALIZED_EXTENSION) ?: return
            savedFiles.add(vFile)
        }
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