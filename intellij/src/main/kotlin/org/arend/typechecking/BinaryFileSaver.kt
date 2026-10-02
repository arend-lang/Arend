package org.arend.typechecking

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import org.arend.ext.module.ModuleLocation
import org.arend.module.config.LibraryConfig
import org.arend.server.ArendServerService
import org.arend.server.BinaryCacheFilter
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.typechecking.error.DeduplicatingErrorReporter
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.ArendBundle
import org.arend.util.findLibrary
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class BinaryFileSaver(private val project: Project) {
    fun saveAll() {
        val application = ApplicationManager.getApplication()
        if (application.isDispatchThread && !application.isUnitTestMode) {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                { saveAll(ProgressManager.getInstance().progressIndicator) },
                ArendBundle.message("arend.binaries.saving"), true, project)
        } else {
            saveAll(ProgressManager.getGlobalProgressIndicator())
        }
    }

    private fun saveAll(indicator: ProgressIndicator?) {
        val server = project.service<ArendServerService>().server
        val errorReporter = DeduplicatingErrorReporter(NotificationErrorReporter(project))
        val configs = HashMap<String, LibraryConfig?>()
        val binariesDirs = HashSet<Path>()
        var persisted = 0
        var failed = 0

        val modules = server.modules.filter { it.locationKind == ModuleLocation.LocationKind.SOURCE }
        indicator?.isIndeterminate = false
        for ((index, module) in modules.withIndex()) {
            indicator?.checkCanceled()
            indicator?.fraction = index.toDouble() / modules.size
            val target = runReadAction { targetFor(module, configs) } ?: continue

            indicator?.text2 = module.toString()
            val binarySource = GZIPStreamBinarySource(FileBinarySource(target.binariesDir, module))
            if (runReadAction { binarySource.persist(server, errorReporter) }) {
                persisted++
                binariesDirs.add(target.binariesDir)
            } else {
                failed++
            }
        }

        errorReporter.flush()
        if (persisted > 0 || failed > 0) {
            LOG.info("Binary cache: persisted $persisted module(s)" + if (failed > 0) ", $failed failed" else "")
        }
        refresh(binariesDirs)
    }

    private class Target(val binariesDir: Path)

    private fun targetFor(module: ModuleLocation, configs: MutableMap<String, LibraryConfig?>): Target? {
        val server = project.service<ArendServerService>().server
        val config = configs.getOrPut(module.libraryName) { project.findLibrary(module.libraryName) } ?: return null
        val binariesDir = config.binariesDirPath ?: return null
        val group = server.getRawGroup(module) ?: return null
        if (!BinaryCacheFilter.isCacheable(group)) return null
        return Target(binariesDir)
    }

    private fun refresh(binariesDirs: Collection<Path>) {
        if (binariesDirs.isEmpty()) return
        val fileManager = VirtualFileManager.getInstance()
        val files = binariesDirs.mapNotNull { dir ->
            fileManager.findFileByNioPath(dir) ?: dir.parent?.let { fileManager.findFileByNioPath(it) }
        }
        if (files.isNotEmpty()) {
            VfsUtil.markDirtyAndRefresh(true, true, false, *files.toTypedArray())
        }
    }

    companion object {
        private val LOG = logger<BinaryFileSaver>()
    }
}
