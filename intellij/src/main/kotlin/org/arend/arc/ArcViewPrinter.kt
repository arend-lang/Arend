package org.arend.arc

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Definition
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.visitor.VoidExpressionVisitor
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModuleLocation
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.server.ArendServerService
import org.arend.term.concrete.Concrete
import org.arend.term.group.ConcreteGroup
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.ArendBinaryCacheService
import org.arend.typechecking.CoroutineCancellationIndicator
import org.arend.typechecking.runner.IntellijProgressReporter
import org.arend.util.ArendBundle
import java.util.EnumSet

// Prints the module of an .arc view as it is in the server, after loading or typechecking it
object ArcViewPrinter {
    private val LOG = logger<ArcViewPrinter>()

    // The text is for reading: it leaves out what the typechecker inferred (implicit arguments, parameters of
    // constructors, types of lambda parameters, levels, coercions) and proofs
    private val READER_FLAGS = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)

    private sealed interface Lookup

    // The library of the view is not registered yet; its registration prepares the open views again
    private object NotRegistered : Lookup

    private class Source(val config: ArendModuleConfigService, val arendFile: ArendFile, val module: ModuleLocation) : Lookup

    private fun findSource(project: Project, file: ArcVirtualFile): Lookup? {
        // The source may have been deleted while the view was waiting for the previous one
        if (!file.isValid) return null
        val config = file.config ?: return null
        if (!config.isInitialized) return NotRegistered
        val arendFile = file.sourceFile?.let { PsiManager.getInstance(project).findFile(it) } as? ArendFile ?: return null
        return Source(config, arendFile, arendFile.moduleLocation ?: return null)
    }

    /**
     * Loads the binary cache of the library of [file] and typechecks the module, which leaves nothing to typecheck
     * in the modules whose .arc is up to date, and prints its definitions. Returns null while the library is not
     * registered: there is nothing to show yet, and the registration prepares the open views again. Tells
     * [ArcViewService] what the text shows, for [isOutdated]. Reports to the current progress step, and holds a read
     * action only while it reads PSI, so it must not be called under one.
     */
    suspend fun prepare(project: Project, file: ArcVirtualFile): String? = reportSequentialProgress { reporter ->
        when (val lookup = readAction { findSource(project, file) }) {
            null -> ""
            NotRegistered -> null
            is Source -> {
                val group = getGroup(project, file, lookup, reporter)
                val service = project.service<ArcViewService>()
                if (group == null) {
                    readAction { service.setShown(file, shownState(null)) }
                    ""
                } else reporter.nextStep(100, ArendBundle.message("arend.arc.printing")) {
                    reportRawProgress { progress ->
                        readAction {
                            val text = print(project, group, lookup.arendFile, progress)
                            service.setShown(file, shownState(group))
                            text
                        }
                    }
                }
            }
        }
    }

    // What a view of the group shows: its typechecked definitions, by identity, as a retypechecked definition is a new one
    private fun shownState(group: ConcreteGroup?): List<Int> =
        group?.statements?.map { System.identityHashCode(getTypechecked(it.group)) } ?: emptyList()

    // Whether the server has loaded or typechecked every definition of the group
    private fun isComplete(group: ConcreteGroup): Boolean =
        group.statements.all { it.group?.referable !is TCDefReferable || getTypechecked(it.group) != null }

    /**
     * Whether the view [file], which shows [shown], is out of date: the server holds other definitions of its
     * module, and all of them are loaded or typechecked. If [activated], it is also out of date if they are not all
     * loaded or typechecked; preparing the view loads or typechecks them. Must be called under a read action.
     */
    fun isOutdated(project: Project, file: ArcVirtualFile, shown: List<Int>?, activated: Boolean): Boolean {
        val source = findSource(project, file) as? Source ?: return false
        val group = project.service<ArendServerService>().server.getRawGroup(source.module) ?: return activated
        return if (isComplete(group)) shownState(group) != shown else activated
    }

    private suspend fun getGroup(project: Project, file: ArcVirtualFile, source: Source, reporter: SequentialProgressReporter): ConcreteGroup? {
        val server = project.service<ArendServerService>().server
        reporter.nextStep(30, ArendBundle.message("arend.arc.loading.cache", source.module.libraryName)) {
            loadBinaryCache(project, source.module.libraryName)
        }
        reporter.nextStep(70, ArendBundle.message("arend.arc.typechecking", source.module.modulePath)) {
            reportRawProgress { progress ->
                val progressReporter = IntellijProgressReporter<List<Concrete.ResolvableDefinition>>(progress) {
                    it.firstOrNull()?.data?.refLongName?.toString()
                }
                server.getCheckerFor(listOf(source.module)).typecheck(null, DummyErrorReporter.INSTANCE, CoroutineCancellationIndicator(this), progressReporter)
            }
        }
        // Interrupted typechecking returns normally
        currentCoroutineContext().ensureActive()

        // The module is shown as it is in the server, never from its .arc: loading the .arc would put its group in the
        // place of the module, with the timestamp of the .arc, which outranks every later update from the source
        val group = server.getRawGroup(source.module)
        val unloadedModules = project.service<ArcUnloadedModuleService>()
        if (group == null) unloadedModules.addUnloadedModule(file) else unloadedModules.removeLoadedModule(file)
        EditorNotifications.getInstance(project).updateNotifications(file)
        return group
    }

    private suspend fun loadBinaryCache(project: Project, library: String) {
        try {
            // As in RunnerService: definitions loaded from the binaries outdate the highlighting of the sources
            if (project.service<ArendBinaryCacheService>().loadCache(library) && !ApplicationManager.getApplication().isUnitTestMode) {
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        } catch (e: ProcessCanceledException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LOG.warn("Failed to load binary cache for $library", e)
        }
    }

    private fun print(project: Project, group: ConcreteGroup, arendFile: ArendFile, progress: RawProgressReporter): String {
        val builder = StringBuilder()
        val server = project.service<ArendServerService>().server

        val definitions = group.statements.mapNotNull { getTypechecked(it.group) }
        val statementVisitor = object : VoidExpressionVisitor<Void>() {
            val referables = mutableSetOf<PsiLocatedReferable>()

            override fun visitDefCall(expr: DefCallExpression?, params: Void?): Void? {
                val referable = expr?.definition?.referable?.data as? PsiLocatedReferable? ?: return super.visitDefCall(expr, params)
                if (referable !is ArendFieldDefIdentifier) {
                    referables.add(referable)
                } else {
                    ((referable.resultType as? ArendNewExpr?)
                        ?.appExpr as? ArendArgumentAppExpr?)
                        ?.atomFieldsAcc?.atom?.literal?.refIdentifier?.resolve?.let {
                            resultReferable -> (resultReferable as? PsiLocatedReferable?)?.let {
                                referables.add(it)
                            }
                        }
                }
                return super.visitDefCall(expr, params)
            }

            override fun visitClass(def: ClassDefinition?, params: Void?): Void? {
                for (superClass in def?.superClasses ?: emptyList()) {
                    val referable = superClass.referable?.data as? PsiLocatedReferable? ?: continue
                    referables.add(referable)
                }
                return super.visitClass(def, params)
            }
        }
        for (definition in definitions) {
            ProgressManager.checkCanceled()
            definition.accept(statementVisitor, null)
        }

        val filesToDefinitions = mutableMapOf<ArendFile, MutableList<String>>()
        val definitionsToFiles = mutableSetOf<String>()
        for (referable in statementVisitor.referables) {
            val file = referable.containingFile as ArendFile
            if (project.service<ArendServerService>().isPrelude(file)) {
                continue
            }
            val fullName = referable.fullName.toString()
            filesToDefinitions.getOrPut(file) { mutableListOf() }.add(fullName)
            definitionsToFiles.add(fullName)
        }

        val fullFiles = mutableMapOf<ArendFile, Boolean>()
        filesLoop@ for ((file, fileDefinitions) in filesToDefinitions) {
            val moduleLocation = file.moduleLocation ?: continue@filesLoop
            val fileGroup = server.getRawGroup(moduleLocation)
            for (element in LexicalScope.opened(fileGroup).elements) {
                val fullName = (element as? LocatedReferable?)?.refFullName?.toString() ?: continue
                if (!fileDefinitions.contains(fullName) && definitionsToFiles.contains(fullName)) {
                    fullFiles[file] = false
                    continue@filesLoop
                }
                fullFiles[file] = true
            }
        }

        // In the order of the modules in the sources: by the segments of their paths, as directories and files
        val importedFiles = filesToDefinitions.entries.sortedWith { (file1, _), (file2, _) ->
            compareModulePaths(modulePathOf(file1), modulePathOf(file2))
        }
        for ((file, importedDefinitions) in importedFiles) {
            if (fullFiles[file] == true) {
                builder.append("\\import ${file.fullName}\n")
            } else {
                builder.append("\\import ${file.fullName}(${importedDefinitions.joinToString(",")})\n")
            }
        }
        if (filesToDefinitions.isNotEmpty()) {
            builder.append("\n")
        }

        val config = PrettyPrinterConfigWithRenamer(CachingScope.make(arendFile.scope ?: LexicalScope.opened(group) ?: EmptyScope.INSTANCE))
        config.expressionFlags = EnumSet.copyOf(READER_FLAGS)

        val statements = group.statements
        for ((i, statement) in statements.withIndex()) {
            // A write action interrupts the read action here, which is then restarted
            ProgressManager.checkCanceled()
            if (addStatement(statement.group, builder, config) && i < statements.lastIndex) {
                builder.append("\n\n")
            }
            progress.fraction((i + 1).toDouble() / statements.size)
        }
        return builder.toString()
    }

    private fun modulePathOf(file: ArendFile): List<String> = file.moduleLocation?.modulePath?.toList() ?: listOf(file.fullName)

    // Segment by segment, a prefix first; a segment ignoring case first, as the project view sorts files
    private fun compareModulePaths(path1: List<String>, path2: List<String>): Int {
        for (i in 0 until minOf(path1.size, path2.size)) {
            val result = path1[i].compareTo(path2[i], ignoreCase = true).takeIf { it != 0 } ?: path1[i].compareTo(path2[i])
            if (result != 0) return result
        }
        return path1.size.compareTo(path2.size)
    }

    // Skips definitions without a header yet: being typechecked, or shells of a cache that failed to load
    private fun getTypechecked(group: ConcreteGroup?): Definition? =
        (group?.referable as? TCDefReferable?)?.typechecked?.takeIf { !it.status().needsTypeChecking() }

    private fun addStatement(group: ConcreteGroup?, builder: StringBuilder, config: PrettyPrinterConfig): Boolean {
        getTypechecked(group)?.let {
            ToAbstractVisitor.convert(it, config).prettyPrint(builder, config)
        } ?: return false
        return true
    }
}
