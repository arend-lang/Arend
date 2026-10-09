package org.arend.server.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.arend.daemon.DaemonPaths
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.module.ModuleLocation
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.naming.scope.EmptyScope
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.typechecking.computation.CancellationIndicator
import org.arend.typechecking.error.local.GoalError
import org.arend.typechecking.runner.RunnerService
import org.arend.util.findLibrary
import java.nio.file.Paths
import java.util.EnumSet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs daemon requests on the IDE's own server. A typecheck runs on the daemon's thread as
 * [RunnerService] runs one from the editor -- the binary caches loaded first, the progress shown in
 * the IDE -- and its results land in the editor too; the client gets the errors it left, rendered as
 * the CLI renders them. Only typechecking is answered so far: a query option is refused rather than
 * ignored.
 *
 * @param served the canonical arend.yaml or zip of every library the daemon serves, to its name
 */
class IdeDaemonCommands(private val project: Project, private val served: () -> Map<String, String>) : IdeDaemonRunner {
    override fun run(args: List<String>, output: IdeDaemonOutput, cancel: AtomicBoolean): Int {
        val libraries = served()
        val scope = LinkedHashSet<String>()
        val moduleNames = ArrayList<String>()
        for (arg in args) {
            if (arg.startsWith("-")) {
                output.err("[ERROR] the daemon inside IntelliJ IDEA answers typechecking only, not `$arg`; " +
                        "stop it from the IDE's Daemon tab to use `arend -d` instead.\n")
                return 2
            }
            val paths = if (arg.contains('/') || arg.contains('\\')) DaemonPaths.resolve(Paths.get(arg)) else null
            val library = when {
                paths != null -> libraries[paths.libraryConfig.toString()] ?: run {
                    output.err("[ERROR] the daemon inside IntelliJ IDEA does not serve ${paths.libraryConfig}\n")
                    return 1
                }
                arg in libraries.values -> arg
                else -> null
            }
            if (library != null) scope.add(library) else moduleNames.add(arg)
        }
        if (scope.isEmpty()) scope.addAll(libraries.values)

        val server = project.service<ArendServerService>().server
        // Before the lookup: a module the agent has just written is not one the server knows yet.
        if (!sync(scope, cancel)) return 1
        val modules = ArrayList<ModuleLocation>()
        for (name in moduleNames) {
            val module = server.modules.firstOrNull {
                it.libraryName in scope && it.locationKind == ModuleLocation.LocationKind.SOURCE && it.modulePath.toString() == name
            }
            if (module == null) {
                output.err("[ERROR] no module `$name` in ${scope.joinToString(", ")}\n")
                return 1
            }
            modules.add(module)
        }

        if (!check(scope, modules, output, cancel)) return 1

        var failed = false
        for ((module, errors) in server.errorMap) {
            if (if (modules.isEmpty()) module.libraryName !in scope else module !in modules) continue
            for (error in errors) {
                if (error.level == GeneralError.Level.ERROR) failed = true
                val text = render(error) + "\n"
                if (error.isSevere) output.err(text) else output.out(text)
            }
        }
        return if (failed) 1 else 0
    }

    override fun refresh(output: IdeDaemonOutput, cancel: AtomicBoolean): Int {
        val libraries = served().values
        if (!sync(libraries, cancel)) return 1
        output.out("Refreshed ${libraries.joinToString(", ")}\n")
        return 0
    }

    /**
     * Makes the server see what an agent wrote to disk, the editor's unsaved changes still winning.
     * Refreshing the files is not enough: the server learns of a change from the PSI only later, and
     * a typecheck started before then checks what the files said before. A module that has not
     * changed keeps its timestamp, which the server skips. Whether [cancel] was left unset.
     */
    private fun sync(libraries: Collection<String>, cancel: AtomicBoolean = AtomicBoolean()): Boolean {
        val configs = libraries.mapNotNull { project.findLibrary(it) }
        val roots = runReadAction { configs.mapNotNull { it.root } }
        if (roots.isNotEmpty()) VfsUtil.markDirtyAndRefresh(false, true, true, *roots.toTypedArray<VirtualFile>())
        // The PSI of a file with a document follows the document, which the refresh reloads only
        // later, on the EDT: wait for that, then make the PSI match it. Not under a modal dialog:
        // committing changes the PSI, which only a write-safe modality allows.
        ApplicationManager.getApplication().invokeAndWait({
            if (!project.isDisposed) PsiDocumentManager.getInstance(project).commitAllDocuments()
        }, ModalityState.nonModal())
        val server = project.service<ArendServerService>().server
        for (library in libraries) {
            if (cancel.get()) return false
            ArendServerRequesterImpl(project).requestUpdate(server, library, true)
        }
        // Nor does the server forget a file deleted from disk, whose errors would otherwise outlive it.
        runReadAction {
            for (config in configs) {
                for (module in server.modules.filter { it.libraryName == config.name }) {
                    if (module.locationKind != ModuleLocation.LocationKind.GENERATED && config.findArendFile(module.modulePath, module.locationKind == ModuleLocation.LocationKind.TEST) == null) {
                        server.removeModule(module)
                    }
                }
            }
        }
        return true
    }

    /**
     * Typechecks [modules], or with none every source module of [libraries], on the daemon's thread
     * the way [RunnerService] checks from the editor: the binary caches loaded first, the progress
     * shown in the IDE, which can cancel it there, and the editor updated after. Whether it ran to its
     * end, which [cancel] stops it short of; why not, unless the client cancelled it, goes to [output].
     */
    private fun check(libraries: Collection<String>, modules: List<ModuleLocation>, output: IdeDaemonOutput, cancel: AtomicBoolean): Boolean {
        val server = project.service<ArendServerService>().server
        val runner = project.service<RunnerService>()
        val title = (modules.ifEmpty { libraries }).joinToString(", ")
        val finished = try {
            runBlocking {
                withBackgroundProgress(project, "Checking $title for the Arend daemon") {
                    val scope = this
                    val indicator = object : CancellationIndicator {
                        override fun isCanceled() = cancel.get() || !scope.isActive

                        override fun cancel() = cancel.set(true)
                    }
                    for (library in if (modules.isEmpty()) libraries else modules.map { it.libraryName }.distinct()) {
                        runner.loadCache(library)
                    }
                    val checker = server.getCheckerFor(modules.ifEmpty {
                        server.modules.filter { it.libraryName in libraries && it.locationKind == ModuleLocation.LocationKind.SOURCE }
                    })
                    checker.resolveAll(indicator, ProgressReporter.empty())
                    checker.typecheck(null, DummyErrorReporter.INSTANCE, indicator, ProgressReporter.empty())
                    !indicator.isCanceled
                }
            }
        } catch (_: CancellationException) {
            false
        } catch (_: InterruptedException) {
            // Closing the daemon sets [cancel], then interrupts its worker: the check is to go too.
            Thread.currentThread().interrupt()
            false
        }
        runner.typecheckedElsewhere()
        // Not the client's cancel, which the daemon reports itself: the IDE's.
        if (!finished && !cancel.get()) output.err("[ERROR] the typecheck was cancelled in the IDE\n")
        return finished
    }

    private fun render(error: GeneralError): String {
        val config = PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE)
        if (error is GoalError) config.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)
        return error.getDoc(config).toString()
    }
}
