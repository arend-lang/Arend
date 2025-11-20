package org.arend.toolWindow.repl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.util.PsiModificationTracker
import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.library.LibraryDependency
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.FullModuleReferable
import org.arend.prelude.Prelude
import org.arend.psi.ArendPsiFactory
import org.arend.repl.Repl
import org.arend.repl.action.LoadLibraryCommand
import org.arend.server.ArendLibrary
import org.arend.server.ArendServer
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.settings.ArendProjectSettings
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.group.ConcreteGroup
import org.arend.toolWindow.repl.action.SetPromptCommand
import org.arend.typechecking.ModificationCancellationIndicator
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.typechecking.result.TypecheckingResult
import org.arend.util.FileUtils
import org.arend.util.FileUtils.USER_HOME
import org.arend.util.SingletonList
import org.arend.util.arendModules
import org.arend.util.findExternalLibrary
import java.nio.file.Files
import java.util.function.Consumer

abstract class IntellijRepl private constructor(
    private val project: Project,
    val handler: ArendReplExecutionHandler,
    server: ArendServer,
    errorReporter: ListErrorReporter
) : Repl(
    errorReporter,
    server
) {
    constructor(
        handler: ArendReplExecutionHandler,
        project: Project,
        server: ArendServer = project.service<ArendServerService>().server
    ) : this(project, handler, server, ListErrorReporter()) {
        pwd = USER_HOME
    }

    val replLibraries = mutableMapOf<String, LibraryConfig>()

    private val psiFactory = ArendPsiFactory(project, REPL_NAME)
    override fun parseStatements(line: String): ConcreteGroup? = psiFactory.createFromText(line)
        ?.let { ConcreteBuilder.convertGroup(it, it.moduleLocation, errorReporter) }
    override fun parseExpr(text: String) = psiFactory.createExpressionMaybe(text)
        ?.let { ConcreteBuilder.convertExpression(it) }

    override fun getPrettyPrinterFlags() = project.service<ArendProjectSettings>().replPrintingOptionsFilterSet

    override fun getNormalizationMode(): NormalizationMode? {
        val modeString = project.service<ArendProjectSettings>().data.replNormalizationMode
        return if (modeString == "NULL") null else NormalizationMode.valueOf(modeString)
    }

    override fun setNormalizationMode(mode: NormalizationMode?) {
        project.service<ArendProjectSettings>().data.replNormalizationMode = mode?.toString() ?: "NULL"
    }

    override fun loadCommands() {
        super.loadCommands()
        registerAction("prompt", SetPromptCommand)
        val arendFile = handler.arendFile
        arendFile.enforcedLibraryConfig = myLibraryConfig
        arendFile.generatedModuleLocation = replModuleLocation
    }

    override fun loadLibraries() {
        myServer.addReadOnlyModule(Prelude.MODULE_LOCATION) { ConcreteBuilder.convertGroup(project.service<ArendServerService>().prelude, Prelude.MODULE_LOCATION, errorReporter) }
        typecheckModules(SingletonList(Prelude.MODULE_LOCATION))
        project.arendModules.forEach {
          ArendModuleConfigService.getInstance(it)?.let { library -> loadLibrary(library) }
        }

        myServer.updateLibrary(myLibraryConfig, errorReporter)
        updateReplModule(ConcreteGroup(DocFactory.nullDoc(), FullModuleReferable(replModuleLocation), null, mutableListOf(), mutableListOf(), mutableListOf()), true)
        replLibraries[REPL_NAME] = myLibraryConfig
    }

    override fun createLibrary(libraryName: String): LibraryConfig? {
        replLibraries[libraryName]?.let { return it }

        val modules = project.arendModules
        modules.find { it.name == libraryName }?.let { ArendModuleConfigService.getInstance(it) }?.let {
            replLibraries[libraryName] = it
            return it
        }
        val configFile = (if (pwd.endsWith(libraryName) || libraryName == LoadLibraryCommand.CUR_DIR) {
            pwd
        } else {
            pwd.resolve(libraryName)
        }).resolve(FileUtils.LIBRARY_CONFIG_FILE)
        return if (Files.exists(configFile)) project.findExternalLibrary(pwd, libraryName)?.apply {
            replLibraries[libraryName] = this
        } else null
    }

    override fun loadLibrary(library: ArendLibrary) {
        super.loadLibrary(library)
        (library as LibraryConfig?)?.let { replLibraries.put(library.libraryName, it) }

        val replGroup = myServer.getRawGroup(replModuleLocation)
        myServer.updateLibrary(myLibraryConfig, errorReporter)
        if (replGroup != null) {
            updateReplModule(replGroup, true)
        }
    }

    override fun unloadLibrary(libraryName: String) {
        super.unloadLibrary(libraryName)
        myServer.modules.filter { it.libraryName == libraryName }.forEach { myServer.removeModule(it)}
        replLibraries.remove(libraryName)

        val replGroup = myServer.getRawGroup(replModuleLocation)
        myServer.updateLibrary(myLibraryConfig, errorReporter)
        if (replGroup != null) {
            removeNotLoadedStatements(replGroup, false)
            updateReplModule(replGroup, true)
        }
    }

    override fun getAllModules(): Set<ModulePath> {
        val result = mutableSetOf<ModulePath>()
        libraries.map { libraryName -> createLibrary(libraryName)?.let {
            result.addAll(it.findModules(false))
        } }
        result.addAll(myServer.modules.filter { it.locationKind == ModuleLocation.LocationKind.GENERATED }.map { it.modulePath })
        return result
    }

    private val myLibraryConfig = object : LibraryConfig(project) {
        override val name: String get() = REPL_NAME
        override val root: VirtualFile? get() = null
        override val dependencies: MutableList<LibraryDependency>
            get() = myServer.libraries.filter { it != REPL_NAME }.map { LibraryDependency(it) }.toMutableList()
        override val modules: List<ModulePath>
            get() = listOf(replModuleLocation.modulePath)
        override fun isExternalLibrary() = true
    }

    override fun checkExpr(expr: Concrete.Expression, expectedType: Expression?, continuation: Consumer<TypecheckingResult>) {
        val moduleLocation = expr.underlyingReferable.modulePath?.let { myServer.findModule(it, null, true, true) }
        moduleLocation?.let { myServer.getCheckerFor(listOf(it)).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty()) }
        ApplicationManager.getApplication().executeOnPooledThread {
            val indicator = ModificationCancellationIndicator(PsiModificationTracker.getInstance(project))
            ComputationRunner<Unit>().run(indicator) {
                super.checkExpr(expr, expectedType, continuation)
            }
        }
    }

    override fun checkErrors() = runReadAction {
        super.checkErrors()
    }

    override fun prettyExpr(builder: StringBuilder, expression: Expression): StringBuilder =
        runReadAction { super.prettyExpr(builder, expression) }

    fun getServer() = myServer
}
