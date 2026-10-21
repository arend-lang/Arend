package org.arend.server.daemon

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.platform.ide.progress.withBackgroundProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.apache.commons.cli.CommandLine
import org.apache.commons.cli.DefaultParser
import org.apache.commons.cli.ParseException
import org.arend.cli.ArendOptions
import org.arend.cli.LockedFlags
import org.arend.daemon.DaemonPaths
import org.arend.daemon.server.DaemonServer
import org.arend.error.SourcePosition
import org.arend.ext.error.ErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.module.FullName
import org.arend.ext.module.LongName
import org.arend.ext.module.ModuleLocation
import org.arend.module.config.LibraryConfig
import org.arend.module.error.ModuleNotFoundError
import org.arend.psi.ArendFile
import org.arend.query.ConsoleQueryTool
import org.arend.query.QueryConsole
import org.arend.query.QueryLibraries
import org.arend.query.SourcePositionUtils
import org.arend.term.concrete.Concrete
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.server.TimedProgressReporter
import org.arend.server.TypecheckReports
import org.arend.typechecking.computation.CancellationIndicator
import org.arend.typechecking.runner.RunnerService
import org.arend.util.findLibrary
import org.arend.util.FileUtils
import org.arend.util.findInternalLibrary
import org.arend.error.CountingErrorReporter
import java.io.IOException
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Runs daemon requests on the IDE's own server, read as `arend` reads its command line. A typecheck
 * runs on the daemon's thread as [RunnerService] runs one from the editor -- the binary caches loaded
 * first, the progress shown in the IDE -- and its results land in the editor too; the client gets
 * the errors it left, rendered as the CLI renders them, and the reports
 * `-p`, `-t` and `--show-*` ask for. A query (`-ss`, `-ps`, `-fu`, `-ch`, `-sc`) runs the CLI's own
 * tool on the IDE's libraries, so that its answer is the CLI's.
 *
 * @param served the canonical arend.yaml or zip of every library the daemon serves, to its name
 */
class IdeDaemonCommands(private val project: Project, private val served: () -> Map<String, String>) : DaemonServer.Runner {
    private companion object {
        val LOG = logger<IdeDaemonCommands>()
        /** How long a sync may take before the client is told what it waits for. */
        const val WAIT_NOTE_MILLIS = 2000L
    }

    private val server
        get() = project.service<ArendServerService>().server

    override fun run(request: DaemonServer.Request): Int = try {
        runRequest(request)
    } catch (e: Throwable) {
        // The client gets the trace as well; the log keeps it for a bug report.
        if (e !is InterruptedException && e !is CancellationException) LOG.warn("An Arend daemon request failed: ${request.args}", e)
        throw e
    }

    private fun runRequest(request: DaemonServer.Request): Int {
        val args = request.args ?: return refresh(request.output, request.cancel)
        val out = PrintStream(request.output.stream(true), true, StandardCharsets.UTF_8)
        val err = PrintStream(request.output.stream(false), true, StandardCharsets.UTF_8)
        try {
            return QueryConsole.with(out, err) { execute(args, request.cwd, out, err, request.cancel) }
        } finally {
            out.flush()
            err.flush()
        }
    }

    private fun execute(args: List<String>, cwd: String?, out: PrintStream, err: PrintStream, cancel: CancellationIndicator): Int {
        // Before parsing, as the CLI's daemon reads them: `-h -ss` leaves -ss without its value.
        ArendOptions.helpRefusal(args.toTypedArray())?.let {
            err.println(it)
            return 1
        }
        ArendOptions.helpTool(args.toTypedArray())?.let {
            out.println(it.cliHelp())
            return 0
        }
        val cmdLine = try {
            DefaultParser().parse(ArendOptions.create(), args.toTypedArray())
        } catch (e: ParseException) {
            err.println(e.message)
            return 1
        }
        val tool = ArendOptions.queryTool(cmdLine)
        ArendOptions.daemonRefusal(cmdLine)?.let {
            err.println(it)
            return 1
        }
        // As the CLI's daemon does: they shaped the daemon when it started, which here is the project.
        LockedFlags.warnIgnored(cmdLine, "the IDE's project", err)

        val libraries = served()
        val scope = LinkedHashSet<String>()
        val targets = ArrayList<String>()
        for (arg in cmdLine.argList) {
            val paths = DaemonPaths.resolve(cwd, arg)
            val library = when {
                paths != null -> libraries[paths.libraryConfig.toString()] ?: run {
                    err.println("[ERROR] the daemon inside IntelliJ IDEA does not serve ${paths.libraryConfig}")
                    return 1
                }
                arg in libraries.values -> arg
                else -> null
            }
            if (library != null) scope.add(library) else targets.add(arg)
        }
        // As the CLI's daemon does: no library named means the one the client is in, else all of them.
        if (scope.isEmpty() && cwd != null) {
            DaemonPaths.resolve(cwd, ".")?.let { libraries[it.libraryConfig.toString()] }?.let { scope.add(it) }
        }
        if (scope.isEmpty()) scope.addAll(libraries.values)

        // Before anything is looked up: a module the agent has just written is not one the server knows yet.
        if (!sync(scope, cancel) { err.println(it) }) return 1
        // As the CLI's two reporters, and an error proper through either fails the request: what is
        // wrong with the command line itself to stderr as it is, and what the typecheck finds rendered
        // as `arend` renders it, to stderr only when severe.
        val reporter = CountingErrorReporter(GeneralError.Level.ERROR) { err.println(it) }
        val diagnostics = CountingErrorReporter(GeneralError.Level.ERROR) {
            (if (it.isSevere) err else out).println(TypecheckReports.render(it))
        }
        val exitCode = if (tool != null) SourcePositionUtils.withResolver(::position) {
                query(tool, cmdLine, cwd, scope, out, err, cancel)
            }
            else typecheck(cmdLine, scope, targets, out, err, reporter, diagnostics, cancel)
        return if (exitCode == 0 && reporter.errorsNumber == 0 && diagnostics.errorsNumber == 0) 0 else 1
    }

    /**
     * Runs [tool] as `arend -<tool>` would. With `--json` the document alone goes to the client's
     * stdout and every diagnostic to a log file, as the CLI's `JsonOutputRedirect` has it.
     */
    private fun query(tool: ConsoleQueryTool, cmdLine: CommandLine, cwd: String?, scope: Collection<String>, out: PrintStream,
                      err: PrintStream, indicator: CancellationIndicator): Int {
        val runner = tool.parseArgs(cmdLine.getOptionValues(tool.shortName())) ?: return 1
        // Cached: finding a library may parse its arend.yaml, and the tools ask on every line they print.
        val configs = HashMap<String, LibraryConfig?>()
        val libraries = object : QueryLibraries {
            override fun getLibraries() = server.libraries.toList()

            override fun getLibrary(name: String) = configs.getOrPut(name) { project.findLibrary(name) }
        }
        val json = cmdLine.hasOption("json")
        val context = ConsoleQueryTool.QueryContext(scope.mapNotNull { libraries.getLibrary(it) }, libraries, server,
            out, json, emptySet(), indicator)
        if (!json) return runner.run(context)

        val logPath = ArendOptions.jsonLogPath(cmdLine, cwd)
        val log = try {
            PrintStream(ArendOptions.openJsonLog(logPath), true, StandardCharsets.UTF_8)
        } catch (e: IOException) {
            err.println(ArendOptions.jsonLogFailed(tool, logPath, e.message))
            return QueryConsole.with(err, err) { runner.run(context) }
        }
        try {
            return QueryConsole.with(log, log) { runner.run(context) }
        } finally {
            log.close()
            err.println(ArendOptions.jsonLogWritten(tool, logPath))
        }
    }

    /**
     * Typechecks [scope], or only the modules and definitions [targets] name, then prints what the
     * CLI prints after its own typecheck: the errors left, `--show-sizes` and `--show-modules` for a
     * whole library, `-p`, the tests of `-t` and `--show-times`, in that order.
     */
    private fun typecheck(cmdLine: CommandLine, scope: Collection<String>, targets: List<String>, out: PrintStream, err: PrintStream,
                          reporter: ErrorReporter, diagnostics: ErrorReporter, cancel: CancellationIndicator): Int {
        // Each module to the definitions of it asked for; null for the whole module. A target that names
        // nothing is reported, which fails the request, and the others are checked: as the CLI does.
        val modules = LinkedHashMap<ModuleLocation, MutableSet<LongName>?>()
        for (target in targets) {
            val (modulePath, definition) = TypecheckReports.parseFullName(target, reporter)?.let { it.proj1 to it.proj2 } ?: continue
            val module = server.modules.firstOrNull {
                it.libraryName in scope && it.locationKind == ModuleLocation.LocationKind.SOURCE && it.modulePath == modulePath
            }
            if (module == null) {
                reporter.report(ModuleNotFoundError(modulePath))
                continue
            }
            if (definition != null && !TypecheckReports.checkDefinitionExists(server, module, definition, reporter)) continue
            // A whole module asked for covers every definition of it asked for, before or after.
            when {
                definition == null -> modules[module] = null
                module !in modules -> modules[module] = linkedSetOf(definition)
                else -> modules[module]?.add(definition)
            }
        }
        // Not the whole library instead: what was asked for is not there, and that has been said.
        if (targets.isNotEmpty() && modules.isEmpty()) return 1
        // As the CLI does: the module -p prints is checked too, or there would be nothing to print.
        val print = cmdLine.getOptionValue("p")
        if (print != null && modules.isNotEmpty()) {
            TypecheckReports.parseFullName(print, reporter)?.let { target ->
                server.findModule(target.proj1, null, false, false)?.takeIf { it !in modules }?.let { modules[it] = null }
            }
        }

        val timed = if (cmdLine.hasOption(ArendOptions.SHOW_TIMES)) TimedProgressReporter() else null
        if (!check(scope, modules, false, diagnostics, timed, err, cancel)) return 1

        val failed = printErrors(out, err) {
            it.locationKind == ModuleLocation.LocationKind.SOURCE && if (modules.isEmpty()) it.libraryName in scope else it in modules
        }
        if (modules.isEmpty()) {
            for (library in scope) {
                TypecheckReports.showRequested(server, library, cmdLine, out)
            }
        }
        if (print != null) TypecheckReports.printDefinitions(server, print, reporter, out)

        if (cmdLine.hasOption("t")) {
            for (library in scope) {
                val time = TypecheckReports.testsStarted(library, out)
                // As the CLI reports tests: by their count alone, and a failed one does not fail the run.
                if (!check(listOf(library), emptyMap(), true, diagnostics, timed, err, cancel)) return 1
                TypecheckReports.testsFinished(server, library, time, out)
            }
        }

        timed?.print(out)
        return if (failed) 1 else 0
    }

    /** Prints the errors the server holds for the modules [included] picks; whether one of them is an error proper. */
    private fun printErrors(out: PrintStream, err: PrintStream, included: (ModuleLocation) -> Boolean): Boolean {
        var failed = false
        for ((module, errors) in server.errorMap) {
            if (!included(module)) continue
            for (error in errors) {
                if (error.level == GeneralError.Level.ERROR) failed = true
                (if (error.isSevere) err else out).println(TypecheckReports.render(error))
            }
        }
        return failed
    }

    /** Brings the server up to date with the files on disk. */
    private fun refresh(output: DaemonServer.Output, cancel: CancellationIndicator): Int {
        val libraries = served().values
        if (!sync(libraries, cancel) { output.err(it + "\n") }) return 1
        output.out("Refreshed ${libraries.joinToString(", ")}\n")
        return 0
    }

    /**
     * Makes the server see what an agent wrote to disk, the editor's unsaved changes still winning.
     * Refreshing the files is not enough: the server learns of a change from the PSI only later, and
     * a typecheck started before then checks what the files said before. A module that has not
     * changed keeps its timestamp, which the server skips. Whether [cancel] was left unset; [note]
     * tells the client what it is waiting for, when that takes a while.
     */
    private fun sync(libraries: Collection<String>, cancel: CancellationIndicator, note: (String) -> Unit): Boolean {
        // The project's own libraries: the server never re-reads a library from outside the project.
        val configs = libraries.mapNotNull { project.findInternalLibrary(it) }
        val files = runReadAction {
            configs.flatMap { listOfNotNull(it.sourcesDirFile, it.testsDirFile, it.root?.findChild(FileUtils.LIBRARY_CONFIG_FILE)) }
        }
        // The PSI of a file with a document follows the document, which the refresh reloads only
        // later, on the EDT: wait for that, then make the PSI match it. Not under a modal dialog:
        // committing changes the PSI, which only a write-safe modality allows -- so the refresh and
        // the commit wait for a dialog to close, and this waits for them in a way a cancel can end.
        val synced = CompletableFuture<Unit>()
        val commit = Runnable {
            try {
                if (!project.isDisposed) PsiDocumentManager.getInstance(project).commitAllDocuments()
            } finally {
                synced.complete(Unit)
            }
        }
        val commitLater = Runnable { ApplicationManager.getApplication().invokeLater(commit, ModalityState.nonModal()) }
        if (files.isEmpty()) {
            commitLater.run()
        } else {
            VfsUtil.markDirty(true, true, *files.toTypedArray<VirtualFile>())
            RefreshQueue.getInstance().refresh(true, true, commitLater, *files.toTypedArray<VirtualFile>())
        }
        if (!await(synced, cancel, note)) return false
        val requester = ArendServerRequesterImpl(project)
        for (config in configs) {
            if (cancel.isCanceled) return false
            requester.requestUpdate(server, config.name, true)
        }
        // Nor does the server forget a file deleted from disk, whose errors would otherwise outlive it.
        runReadAction {
            val modules = server.modules.groupBy { it.libraryName }
            for (config in configs) {
                for (module in modules[config.name].orEmpty()) {
                    if (module.locationKind != ModuleLocation.LocationKind.GENERATED && config.findArendFile(module.modulePath, module.locationKind == ModuleLocation.LocationKind.TEST) == null) {
                        server.removeModule(module)
                    }
                }
            }
        }
        return true
    }

    /**
     * Waits for [future] until it is done, or [cancel] is set; says once through [note] what it waits
     * for, should it take more than a moment. Whether it is done.
     */
    private fun await(future: CompletableFuture<Unit>, cancel: CancellationIndicator, note: (String) -> Unit): Boolean {
        val started = System.currentTimeMillis()
        var told = false
        while (true) {
            if (cancel.isCanceled || project.isDisposed) return false
            try {
                future.get(100, TimeUnit.MILLISECONDS)
                return true
            } catch (_: TimeoutException) {
                if (!told && System.currentTimeMillis() - started > WAIT_NOTE_MILLIS) {
                    told = true
                    note("[INFO] waiting for the IDE to take in the files on disk: it does so once no modal dialog is open there")
                }
            }
        }
    }

    /**
     * Typechecks [modules] -- each whole, or the definitions it maps to -- or, with none, every module
     * of [libraries], their tests too with [tests]. On the daemon's thread, with [cancel] the checker's
     * own indicator, the way [RunnerService] checks from the editor: the binary caches loaded first,
     * the progress shown in the IDE, which can cancel it there, and the editor updated after.
     * Whether it ran to its end; why not, unless the client cancelled it, goes to [err].
     */
    private fun check(libraries: Collection<String>, modules: Map<ModuleLocation, Set<LongName>?>, tests: Boolean, reporter: ErrorReporter,
                      listener: ProgressReporter<List<Concrete.ResolvableDefinition>>?, err: PrintStream, cancel: CancellationIndicator): Boolean {
        val runner = project.service<RunnerService>()
        val title = if (modules.isEmpty()) libraries.joinToString(", ") else modules.keys.joinToString(", ")
        val progress = listener ?: ProgressReporter.empty()
        val finished = try {
            runBlocking {
                withBackgroundProgress(project, "Checking $title for the Arend daemon") {
                    val scope = this
                    val indicator = object : CancellationIndicator {
                        override fun isCanceled() = cancel.isCanceled || !scope.isActive

                        override fun cancel() = cancel.cancel()
                    }
                    for (library in if (modules.isEmpty()) libraries else modules.keys.map { it.libraryName }.distinct()) {
                        runner.loadCache(library)
                    }
                    val checked = modules.keys.ifEmpty {
                        server.modules.filter {
                            it.libraryName in libraries && (it.locationKind == ModuleLocation.LocationKind.SOURCE || tests && it.locationKind == ModuleLocation.LocationKind.TEST)
                        }
                    }.toList()
                    server.getCheckerFor(checked).resolveAll(indicator, ProgressReporter.empty())
                    // A module asked for whole, and the definitions of the others: one checker each, since one checks all its modules or the definitions named.
                    val whole = checked.filter { modules[it] == null }
                    val definitions = modules.flatMap { (module, names) -> names.orEmpty().map { FullName(module, it) } }
                    if (whole.isNotEmpty()) server.getCheckerFor(whole).typecheck(null, reporter, indicator, progress)
                    if (definitions.isNotEmpty()) server.getCheckerFor(checked - whole.toSet()).typecheck(definitions, reporter, indicator, progress)
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
        if (!finished && !cancel.isCanceled) err.println("[ERROR] the typecheck was cancelled in the IDE")
        return finished
    }

    /**
     * Where a PSI element of the IDE's concrete trees is, as the CLI's parser says it: lines and
     * columns from 1, a definition at its name.
     */
    private fun position(data: Any): SourcePosition? = runReadAction {
        val element = (data as? SmartPsiElementPointer<*>)?.element ?: data as? PsiElement
        if (element == null || !element.isValid) return@runReadAction null
        val file = element.containingFile ?: return@runReadAction null
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return@runReadAction null
        val offset = element.textOffset
        if (offset < 0 || offset > document.textLength) return@runReadAction null
        val line = document.getLineNumber(offset)
        SourcePosition((file as? ArendFile)?.moduleLocation?.toString() ?: file.name, line + 1, offset - document.getLineStartOffset(line) + 1)
    }
}
