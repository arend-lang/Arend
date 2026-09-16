package org.arend.typechecking

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import fleet.multiplatform.shims.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.arend.ext.module.ModuleLocation
import org.arend.prelude.Prelude
import org.arend.server.ArendServerService
import org.arend.server.BinaryCacheLoader
import org.arend.server.ProgressReporter
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.source.StreamBinarySource
import org.arend.util.FileUtils
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.findLibrary
import java.nio.file.Files
import java.util.function.Function
import java.util.function.ToLongFunction

@Service(Service.Level.PROJECT)
class ArendBinaryCacheService(private val project: Project) {
    private val loadedLibraries = ConcurrentHashMap<String, Set<ModuleLocation>>()

    private val loadMutex = Mutex()

    suspend fun loadCache(library: String): Boolean {
        if (loadedLibraries.containsKey(library))
            return false
        val server = project.service<ArendServerService>().server
        val reporter = NotificationErrorReporter(project)
        val loadedModules = HashSet<ModuleLocation>()

        if (!Prelude.isInitialized()) {
            readAction {
                server.getCheckerFor(listOf(Prelude.MODULE_LOCATION))
                    .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
            }
        }

        val ordered = LinkedHashSet<String>()
        collectDependencies(library, ordered)

        loadMutex.withLock {
            for (libraryName in ordered) {
                val alreadyLoaded = loadedLibraries[libraryName]
                if (alreadyLoaded != null) {
                    continue
                }
                val config = project.findLibrary(libraryName) ?: continue
                readAction {
                    server.getCheckerFor(config.findModules(false).map { ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, it) })
                        .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
                }

                val binariesDir = config.binariesDirFile?.toNioPath()
                val binarySourceProvider = Function<ModuleLocation, StreamBinarySource?> { module ->
                    if (binariesDir == null || !Files.exists(FileUtils.binaryFile(binariesDir, module.modulePath))) {
                        null
                    } else {
                        GZIPStreamBinarySource(FileBinarySource(binariesDir, module))
                    }
                }
                val rawTimestampProvider = ToLongFunction<ModuleLocation> { module ->
                    config.findArendFile(module)?.virtualFile?.timeStamp ?: 0L
                }

                val loader = BinaryCacheLoader(server, reporter) { message -> LOG.info(message) }
                loader.loadBinaryCache(libraryName, binarySourceProvider, rawTimestampProvider)

                val loaded = loader.binaryCacheLoaded
                loadedLibraries[libraryName] = loaded
                loadedModules.addAll(loaded)
            }
        }
        return true
    }

    fun invalidate(libraries: Collection<String>) {
        for (library in libraries) loadedLibraries.remove(library)
    }

    private fun collectDependencies(libraryName: String, result: LinkedHashSet<String>) {
        if (result.contains(libraryName)) return
        val config = project.findLibrary(libraryName)
        if (config != null) {
            for (dependency in config.dependencies) {
                collectDependencies(dependency.name, result)
            }
        }
        result.add(libraryName)
    }

    companion object {
        private val LOG = logger<ArendBinaryCacheService>()
    }
}
