package org.arend.typechecking

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.testFramework.TestModeFlags
import com.intellij.util.containers.MultiMap
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.*
import org.arend.core.expr.visitor.CompareVisitor
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.definition.CoreDefinition
import org.arend.ext.core.definition.CoreFunctionDefinition
import org.arend.ext.core.ops.CMP
import org.arend.ext.instance.InstanceSearchParameters
import org.arend.ext.instance.SubclassSearchParameters
import org.arend.ext.module.LongName
import org.arend.ext.typechecking.DefinitionListener
import org.arend.extImpl.DefinitionRequester
import org.arend.library.Library
import org.arend.library.LibraryManager
import org.arend.module.*
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.psi.listener.ArendDefinitionChangeListener
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.resolving.*
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.instance.pool.GlobalInstancePool
import org.arend.typechecking.instance.pool.LocalInstancePool
import org.arend.typechecking.instance.pool.RecursiveInstanceHoleExpression
import org.arend.typechecking.instance.provider.InstanceProviderSet
import org.arend.typechecking.instance.provider.SimpleInstanceProvider
import org.arend.typechecking.order.DFS
import org.arend.typechecking.order.dependency.DependencyCollector
import org.arend.typechecking.visitor.CheckTypeVisitor
import org.arend.util.*
import org.arend.yaml.YAMLFileListener
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

class TypeCheckingService(val project: Project) : ArendDefinitionChangeListener, DefinitionRequester, DefinitionListener, Disposable {
    val dependencyListener = DependencyCollector()
    private val libraryErrorReporter = NotificationErrorReporter(project)
    val libraryManager = object : LibraryManager(ArendLibraryResolver(project), null, libraryErrorReporter, libraryErrorReporter, this, this) {
        override fun showLibraryNotFoundError(libraryName: String) {
            if (libraryName == AREND_LIB) {
                showDownloadNotification(project, Reason.MISSING)
            } else if (project.allModules.find { it.name == libraryName } == null) {
                super.showLibraryNotFoundError(libraryName)
            }
        }

        override fun showIncorrectLanguageVersionError(libraryName: String?, range: Range<Version>?) {
            if (libraryName == AREND_LIB) {
                showDownloadNotification(project, Reason.WRONG_VERSION)
            } else {
                super.showIncorrectLanguageVersionError(libraryName, range)
            }
        }

        override fun getRegisteredLibraries(): MutableCollection<out Library> {
            if (ApplicationManager.getApplication().isUnitTestMode) {
                val libs = LibraryManagerTestingOptions.getRegisteredLibraries()
                if (libs != null) {
                    return libs
                }
            }
            return super.getRegisteredLibraries()
        }

        override fun afterLibraryLoading(library: Library, loaded: Int, total: Int) {
            if (loaded < 0 || !service<ArendSettings>().checkForUpdates) return
            for (dependency in library.dependencies) {
                if (dependency.name == AREND_LIB) {
                    val arendLib = getRegisteredLibrary(AREND_LIB)
                    if (arendLib != null) {
                        checkForUpdates(project, arendLib.version)
                    }
                    break
                }
            }
        }
    }

    private val extensionDefinitions = HashMap<TCDefReferable, Library>()

    private val additionalNames = HashMap<String, ArrayList<PsiLocatedReferable>>()

    private val instances = MultiMap.createConcurrent<TCDefReferable, TCDefReferable>()

    /*
    val tcRefMaps = EnumMap<Referable.RefKind, Map<ModuleLocation, ArendFile.LifetimeAwareDefinitionRegistry>>(Referable.RefKind::class.java).also {
        for (kind in Referable.RefKind.values()) {
            it[kind] = ConcurrentHashMap<ModuleLocation, ArendFile.LifetimeAwareDefinitionRegistry>()
        }
    }
    */

    private val tcRefMaps = Array<MutableMap<ModuleLocation, ConcurrentHashMap<LongName, IntellijTCReferable>>>(Referable.RefKind.values().size) {
        ConcurrentHashMap<ModuleLocation, ConcurrentHashMap<LongName, IntellijTCReferable>>()
    }

    fun getTCRefMaps(refKind: Referable.RefKind) = tcRefMaps[refKind.ordinal]

    fun clearTCRefMaps() {
        for (tcRefMap in tcRefMaps) {
            tcRefMap.clear()
        }
    }

    fun cleanupTCRefMaps(module: ModuleLocation) {
        for (tcRefMap in tcRefMaps) {
            tcRefMap[module]?.values?.removeIf { !it.isConsistent }
        }
    }

    val updatedModules = HashSet<ModuleLocation>()

    var isInitialized = false
        private set

    var isLoaded = false
        private set

    fun initialize(): Boolean {
        if (isInitialized) {
            return false
        }

        synchronized(ArendPreludeLibrary::class.java) {
            if (isInitialized) {
                return false
            }

            // Initialize prelude
            val preludeLibrary = ArendPreludeLibrary(project)
            this.preludeLibrary = preludeLibrary
            libraryManager.loadLibrary(preludeLibrary, null)
            preludeLibrary.prelude?.generatedModuleLocation = Prelude.MODULE_LOCATION

            if (Prelude.isInitialized()) {
                val tcRefMap = preludeLibrary.prelude?.getTCRefMap(Referable.RefKind.EXPR)
                if (tcRefMap != null) {
                    Prelude.forEach {
                        val name = it.referable.refLongName
                        tcRefMap[name] = it.referable as IntellijTCReferable
                        val dataRef = it.referable
                        if (dataRef is DataLocatedReferable) {
                            val ref = Scope.resolveName(preludeScope, name.toList())
                            if (ref is PsiLocatedReferable) {
                                dataRef.setPointer(ref)
                            }
                        }
                    }
                }
            }

            val concreteProvider = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null)
            preludeLibrary.resolveNames(concreteProvider, libraryManager.libraryErrorReporter)
            Prelude.PreludeTypechecking(InstanceProviderSet(), concreteProvider, ArendReferableConverter, PsiElementComparator).typecheckLibrary(preludeLibrary)
            preludeLibrary.prelude?.let {
                fillAdditionalNames(it, additionalNames)
            }

            // Set the listener that updates typechecked definitions
            service<ArendPsiChangeService>().addListener(this)

            // Listen for YAML files changes
            YAMLFileListener(project).register()

            ModuleSynchronizer(project).install()

            isInitialized = true
            isLoaded = true
        }

        return true
    }

    private var preludeLibrary: ArendPreludeLibrary? = null

    val prelude: ArendFile?
        get() = preludeLibrary?.prelude

    val preludeScope: Scope
        get() = prelude?.let { LexicalScope.opened(it) } ?: EmptyScope.INSTANCE

    fun getPsiReferable(referable: LocatedReferable): PsiLocatedReferable? {
        (referable.underlyingReferable as? PsiLocatedReferable)?.let { return it }
        return Scope.resolveName(preludeScope, referable.refLongName.toList()) as? PsiLocatedReferable
    }

    fun getDefinitionPsiReferable(definition: Definition) = getPsiReferable(definition.referable)

    override fun typechecked(definition: CoreDefinition) {
        addInstance(definition)
    }

    override fun loaded(definition: CoreDefinition) {
        addInstance(definition)
    }

    private fun addInstance(definition: CoreDefinition) {
        if (definition !is FunctionDefinition) return
        if (definition.kind != CoreFunctionDefinition.Kind.INSTANCE) return
        val classCall = definition.resultType as? ClassCallExpression ?: return
        val dfs = object : DFS<ClassDefinition,Void>() {
            override fun forDependencies(classDef: ClassDefinition): Void? {
                for (superClass in classDef.superClasses) {
                    visit(superClass)
                }
                return null
            }
        }
        dfs.visit(classCall.definition)
        for (classDef in dfs.visited) {
            instances.putValue(classDef.referable, definition.referable)
        }
    }

    // Returns the list of possible solutions. Each solution is a list of functions that are required for this instance to work.
    fun findInstances(classRef: TCDefReferable?, classifyingExpression: Expression?): List<List<FunctionDefinition>> {
        val classDef = classRef?.typechecked as? ClassDefinition ?: return emptyList()
        val result = ArrayList<List<FunctionDefinition>>()
        val functions = ArrayList(instances[classRef])
        while (functions.isNotEmpty()) {
            val collected = getInstances(GlobalInstancePool(SimpleInstanceProvider(functions), null), classDef, classifyingExpression, SubclassSearchParameters(classDef))
            if (collected.isEmpty()) break
            result.add(collected)
            if (!functions.remove(collected[0].referable)) break
        }
        return result
    }

    fun isInstanceAvailable(classRef: TCDefReferable?): Boolean {
        val classDef = classRef?.typechecked as? ClassDefinition ?: return false
        return classDef.classifyingField == null && instances[classRef].isNotEmpty()
    }

    private fun getInstances(pool: GlobalInstancePool, classDef: ClassDefinition, classifyingExpression: Expression?, parameters: InstanceSearchParameters): List<FunctionDefinition> {
        fun getFunction(expr: Concrete.Expression?) =
            (((expr as? Concrete.ReferenceExpression)?.referent as? TCDefReferable)?.typechecked as? FunctionDefinition)?.let { listOf(it) }

        val result = pool.findInstance(classifyingExpression, parameters, null, null, null)
        getFunction(result)?.let { return it }
        if (result !is Concrete.AppExpression) return emptyList()

        var isRecursive = false
        for (argument in result.arguments) {
            if (argument.getExpression() is RecursiveInstanceHoleExpression) {
                isRecursive = true
                break
            }
        }

        if (isRecursive) {
            val visitor = CheckTypeVisitor(DummyErrorReporter.INSTANCE, pool, null)
            visitor.instancePool = GlobalInstancePool(pool.instanceProvider, visitor, LocalInstancePool(visitor))
            val tcResult = visitor.checkExpr(result, null)
            val field = classDef.classifyingField
            if (tcResult != null && classifyingExpression != null && field != null) {
                CompareVisitor.compare(visitor.equations, CMP.EQ, classifyingExpression, FieldCallExpression.make(field, tcResult.expression), null, null)
            }
            val resultExpr = visitor.finalize(tcResult, result, false)?.expression
            if (resultExpr != null) {
                val collected = ArrayList<FunctionDefinition>()
                fun collect(expr: Expression) {
                    if (expr is AppExpression) {
                        collect(expr.function)
                        collect(expr.argument)
                    } else if (expr is FunCallExpression) {
                        if (expr.definition.kind == CoreFunctionDefinition.Kind.INSTANCE) {
                            collected.add(expr.definition)
                        }
                        for (argument in expr.defCallArguments) {
                            collect(argument)
                        }
                    }
                }
                collect(resultExpr)
                if (collected.isNotEmpty()) return collected
            }
        }

        return getFunction(result.function) ?: emptyList()
    }

    fun reload(onlyInternal: Boolean, refresh: Boolean = true) {
        ComputationRunner.getCancellationIndicator().cancel()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reloading Arend libraries", false) {
            override fun run(indicator: ProgressIndicator) {
                if (refresh) {
                    refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
                }

                runReadAction {
                    service<ArendPsiChangeService>().definitionModificationTracker.incModificationCount()
                    isLoaded = false
                    if (onlyInternal) {
                        libraryManager.reloadInternalLibraries {
                            project.service<ArendResolveCache>().clear()
                            val it = extensionDefinitions.iterator()
                            while (it.hasNext()) {
                                if (!it.next().value.isExternal) {
                                    it.remove()
                                }
                            }

                            prepareReload()
                        }
                    } else {
                        libraryManager.reload {
                            project.service<ArendResolveCache>().clear()
                            extensionDefinitions.clear()
                            clearTCRefMaps()

                            prepareReload()
                        }
                    }

                    for (library in libraryManager.registeredLibraries) {
                        if (library.isExternal || library !is ArendRawLibrary) continue
                        for (module in library.config.findModules(false)) {
                            library.config.findArendFile(module, false)?.decLastModification()
                        }
                        for (module in library.config.findModules(true)) {
                            library.config.findArendFile(module, true)?.decLastModification()
                        }
                    }
                }
                isLoaded = true
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        })
    }

    private fun prepareReload(): ArendTypechecking {
        project.service<ErrorService>().clearAllErrors()
        service<TypecheckingTaskQueue>().clearQueue()
        return ArendTypechecking.create(project)
    }

    override fun request(definition: Definition, library: Library) {
        extensionDefinitions[definition.referable] = library
    }

    fun getAdditionalReferables(name: String) = additionalNames[name] ?: emptyList()

    val additionalReferables: Map<String, List<PsiLocatedReferable>>
        get() = additionalNames

    private fun resetErrors(def: Referable) {
        if (def is TCDefinition) {
            project.service<ErrorService>().clearTypecheckingErrors(def)
        }
    }

    private fun removeDefinition(referable: LocatedReferable): TCReferable? {
        if (referable is PsiElement && !referable.isValid) {
            return null
        }

        val curRef = referable.underlyingReferable
        val fullName = FullName(referable)
        val tcRefMap = fullName.modulePath?.let { getTCRefMaps(Referable.RefKind.EXPR)[it] }
        val tcReferable = tcRefMap?.get(fullName.longName)
        if (tcReferable !is TCDefReferable) {
            resetErrors(curRef)
            return tcReferable
        }

        removeTCDefinition(tcReferable)

        val prevRef = (tcReferable as TCDefReferable).underlyingReferable
        val tcTypecheckable = (tcReferable as TCDefReferable).typecheckable
        if (curRef is PsiLocatedReferable && prevRef is PsiLocatedReferable && prevRef != curRef && prevRef.containingFile == curRef.containingFile) {
            return null
        }
        resetErrors(curRef)
        return tcTypecheckable
    }

    private fun removeTCDefinition(ref: TCDefReferable) {
        instances.remove(ref)
        val funcDef = ref.typechecked as? FunctionDefinition
        if (funcDef != null) {
            val classDef = (funcDef.resultType as? ClassCallExpression)?.definition
            if (classDef != null) {
                instances.remove(classDef.referable, funcDef.referable)
            }
        }

        if (extensionDefinitions.containsKey(ref)) {
            runReadAction {
                service<ArendExtensionChangeListener>().notifyIfNeeded(project)
            }
        }

        val tcTypecheckable = ref.typecheckable
        if (tcTypecheckable.typechecked?.goals?.isNotEmpty() != true) {
            tcTypecheckable.location?.let { updatedModules.add(it) }
        }
    }

    private fun doUpdateDefinition(referable: LocatedReferable, file: ArendFile) {
        val tcReferable = removeDefinition(referable) ?: return
        val dependencies = synchronized(project) {
            dependencyListener.update(tcReferable)
        }
        for (ref in dependencies) {
            removeDefinition(ref)
        }

        if ((referable as? ArendDefFunction)?.functionKind?.isUse == true) {
            (referable.parentGroup as? TCDefinition)?.let { doUpdateDefinition(it, file) }
        }
    }

    fun updateDefinition(ref: TCDefReferable) {
        removeTCDefinition(ref)
        val tcRef = ref.typecheckable
        val dependencies = synchronized(project) {
            dependencyListener.update(tcRef)
        }
        for (dep in dependencies) {
            if (dep is TCDefReferable) {
                removeTCDefinition(dep)
            }
        }
    }

    override fun updateDefinition(def: PsiConcreteReferable, file: ArendFile, isExternalUpdate: Boolean) {
        doUpdateDefinition(def, file)
    }

    class LibraryManagerTestingOptions {
        companion object {
            private val stdLib: Key<Library?> = Key.create("AREND_TEST_STD_LIBRARY")

            @TestOnly
            fun setStdLibrary(lib: Library, disposable: Disposable) {
                TestModeFlags.set(stdLib, lib, disposable)
            }

            @TestOnly
            internal fun getRegisteredLibraries(): MutableList<Library>? {
                return TestModeFlags.get(stdLib)?.let { mutableListOf(it) }
            }
        }
    }

    override fun dispose() {}
}
