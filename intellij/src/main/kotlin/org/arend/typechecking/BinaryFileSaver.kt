package org.arend.typechecking

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import org.arend.arc.ArcTrace
import org.arend.arc.ArcViewService
import org.arend.ext.module.ModuleLocation
import org.arend.module.config.LibraryConfig
import org.arend.server.ArendServerService
import org.arend.server.BinaryCacheFilter
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.typechecking.error.DeduplicatingErrorReporter
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.ArendBundle
import org.arend.util.FileUtils
import org.arend.util.findLibrary
import java.nio.file.Files
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
        val start = System.nanoTime()
        val outcomes = LinkedHashMap<ModuleLocation, String>()
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
            val target = runReadAction { targetFor(module, configs, outcomes) } ?: continue

            indicator?.text2 = module.toString()
            val binarySource = GZIPStreamBinarySource(FileBinarySource(target.binariesDir, module))
            val existed = Files.exists(FileUtils.binaryFile(target.binariesDir, module.modulePath))
            if (runReadAction { binarySource.persist(server, errorReporter) }) {
                persisted++
                binariesDirs.add(target.binariesDir)
                val counts = target.counts
                outcomes[module] = (if (counts.typechecked == counts.total) "written" else
                    if (counts.typechecked == 0) "written with nothing typechecked" else "written incomplete") +
                    ": ${counts.typechecked} of ${counts.total} definitions typechecked" + if (existed) ", replacing the .arc" else ", new .arc"
            } else {
                failed++
                outcomes[module] = "failed to write"
            }
        }

        errorReporter.flush()
        if (persisted > 0 || failed > 0) {
            LOG.info("Binary cache: persisted $persisted module(s)" + if (failed > 0) ", $failed failed" else "")
        }
        refresh(binariesDirs)
        traceSave(outcomes, (System.nanoTime() - start) / 1_000_000)
        project.serviceIfCreated<ArcViewService>()?.serverChanged()
    }

    private class Target(val binariesDir: Path, val counts: ArcTrace.Counts)

    private fun targetFor(module: ModuleLocation, configs: MutableMap<String, LibraryConfig?>, outcomes: MutableMap<ModuleLocation, String>): Target? {
        val server = project.service<ArendServerService>().server
        val config = configs.getOrPut(module.libraryName) { project.findLibrary(module.libraryName) }
            ?: return null.also { outcomes[module] = "skipped: no library" }
        val binariesDir = config.binariesDirPath ?: return null.also { outcomes[module] = "skipped: no binaries directory" }
        val group = server.getRawGroup(module) ?: return null.also { outcomes[module] = "skipped: not in the server" }
        val counts = ArcTrace.counts(group)
        if (!BinaryCacheFilter.isCacheable(group)) {
            outcomes[module] = "skipped: " + (if (counts.errors > 0) "${counts.errors} definitions with errors" else "${counts.goals} definitions with goals") +
                ", ${counts.typechecked} of ${counts.total} typechecked"
            return null
        }
        return Target(binariesDir, counts)
    }

    // How many modules each outcome of the save had, and every module that was not written whole
    private fun traceSave(outcomes: Map<ModuleLocation, String>, time: Long) {
        val byKind = outcomes.values.groupingBy { it.substringBefore(':') }.eachCount()
        ArcTrace.log("save: ${outcomes.size} modules in $time ms: " + byKind.entries.sortedByDescending { it.value }.joinToString { "${it.value} ${it.key}" })
        for ((module, outcome) in outcomes) {
            if (!outcome.startsWith("written:")) {
                ArcTrace.log("save:   $module: $outcome")
            }
        }
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
