package org.vclang.typechecking

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.scopeprovider.EmptyModuleScopeProvider
import com.jetbrains.jetpad.vclang.module.scopeprovider.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.prelude.Prelude
import com.jetbrains.jetpad.vclang.term.concrete.Concrete
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import com.jetbrains.jetpad.vclang.typechecking.CancellationIndicator
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.order.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.CachingConcreteProvider
import org.vclang.module.PsiModuleScopeProvider
import org.vclang.module.source.VcFileStorage
import org.vclang.module.source.VcPreludeStorage
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcFile
import org.vclang.psi.ancestors
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.fullName
import org.vclang.psi.findGroupByFullName
import org.vclang.resolving.PsiConcreteProvider
import org.vclang.typechecking.execution.TypecheckingEventsProcessor

typealias VcSourceIdT = CompositeSourceSupplier<
        VcFileStorage.SourceId,
        VcPreludeStorage.SourceId
        >.SourceId

interface TypeCheckingService {
    var eventsProcessor: TypecheckingEventsProcessor?

    val moduleScopeProvider: ModuleScopeProvider

    fun typeCheck(modulePath: ModulePath, definitionFullName: String, cancellationIndicator: CancellationIndicator)

    fun getState(definition: GlobalReferable) : Boolean?

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(private val project: Project) : TypeCheckingService {
    override var eventsProcessor: TypecheckingEventsProcessor?
        get() = logger.eventsProcessor
        set(value) {
            logger.eventsProcessor = value
        }

    private val projectStorage = VcFileStorage(project)
    private val preludeStorage = VcPreludeStorage(project)
    private val storage = CompositeStorage<VcFileStorage.SourceId, VcPreludeStorage.SourceId>(
            projectStorage,
            preludeStorage
    )

    private val logger = TypeCheckConsoleLogger(PrettyPrinterConfig.DEFAULT)

    private val sourceScopeProvider: ModuleScopeProvider
        get() {
            val modules = ModuleManager.getInstance(project).modules // TODO[library]
            return if (modules.isEmpty()) EmptyModuleScopeProvider.INSTANCE else PsiModuleScopeProvider(modules[0])
        }

    private val sourceInfoProvider = CacheSourceInfoProvider(VcSourceInfoProvider())

    override val moduleScopeProvider: CacheModuleScopeProvider<VcSourceIdT> = CacheModuleScopeProvider(sourceScopeProvider)

    private val cacheManager = SourcelessCacheManager(
        storage,
        VcPersistenceProvider(),
        EmptyModuleScopeProvider.INSTANCE,
        moduleScopeProvider,
        sourceInfoProvider,
        VcSourceVersionTracker()
    )

    private val typeCheckerState = cacheManager.typecheckerState
    private val dependencyCollector = DependencyCollector(typeCheckerState)

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(TypeCheckerPsiTreeChangeListener())
        moduleScopeProvider.initialise(storage, cacheManager)
        loadPrelude()
    }

    override fun typeCheck(modulePath: ModulePath, definitionFullName: String, cancellationIndicator: CancellationIndicator) {
        Typechecking.CANCELLATION_INDICATOR = cancellationIndicator
        try {
            val sourceId = sourceIdByPath(modulePath) ?: return
            val module = loadSource(sourceId) ?: return

            /* TODO[caching]
            try {
                cacheManager.loadCache(sourceId)
            } catch (ignored: CacheLoadingException) {
            }
            */

            val eventsProcessor = eventsProcessor!!
            val psiConcreteProvider = PsiConcreteProvider(logger, eventsProcessor)
            val concreteProvider = CachingConcreteProvider(psiConcreteProvider)
            val typeChecking = TestBasedTypechecking(
                eventsProcessor,
                typeCheckerState,
                concreteProvider,
                logger,
                dependencyCollector
            )

            var computationFinished = true

            if (definitionFullName.isEmpty()) {
                eventsProcessor.onSuiteStarted(module)
                DefinitionResolveNameVisitor(logger).resolveGroup(module, CachingScope.make(module.scope), concreteProvider)
                psiConcreteProvider.isResolving = true
                computationFinished = typeChecking.typecheckModules(listOf(module))

                /* TODO[caching]
                try {
                    cacheManager.persistCache(sourceId)
                } catch (e: CachePersistenceException) {
                    e.printStackTrace()
                }
                */
            } else {
                val group = module.findGroupByFullName(definitionFullName.split('.'))
                val ref = group?.referable
                if (ref == null) {
                    Notifications.Bus.notify(Notification("Vclang typechecking", "Typechecking", "Definition $definitionFullName not found", NotificationType.ERROR))
                } else {
                    val typechecked = typeCheckerState.getTypechecked(ref)
                    if (typechecked == null || typechecked.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
                        psiConcreteProvider.isResolving = true
                        val definition = concreteProvider.getConcrete(ref)
                        if (definition is Concrete.Definition) computationFinished = typeChecking.typecheckDefinitions(listOf(definition))
                        else if (definition != null) error(definitionFullName + " is not a definition")
                    } else {
                        if (ref is PsiGlobalReferable) {
                            eventsProcessor.onTestStarted(ref)
                            typeChecking.typecheckingFinished(ref, typechecked)
                        }
                    }
                }
            }

            if (computationFinished) eventsProcessor.onSuitesFinished()
        } finally {
            Typechecking.setDefaultCancellationIndicator()
        }
    }

    override fun getState(definition: GlobalReferable) : Boolean? {
        val d = typeCheckerState.getTypechecked(definition) ?: return null
        return d.status() == Definition.TypeCheckingStatus.NO_ERRORS
    }

    private fun loadPrelude() {
        val sourceId = storage.locateModule(VcPreludeStorage.PRELUDE_MODULE_PATH)
        /* TODO[caching]
        val prelude = checkNotNull(loadSource(sourceId)) { "Failed to load prelude" }
        PsiModuleScopeProvider.preludeScope = LexicalScope.opened(prelude)
        */

        try {
            cacheManager.loadCache(sourceId)
        } catch (e: CacheLoadingException) {
            throw IllegalStateException("Prelude cache is not available", e)
        }

        Prelude.initialise(moduleScopeProvider.forCacheModule(PreludeStorage.PRELUDE_MODULE_PATH).root, typeCheckerState)
    }

    private fun loadSource(sourceId: VcSourceIdT): VcFile? =
            storage.loadSource(sourceId, logger)?.group as? VcFile

    private fun sourceIdByPath(modulePath: ModulePath): VcSourceIdT? {
        val sourceId = storage.locateModule(modulePath)
        if (sourceId != null && storage.isAvailable(sourceId)) {
            return sourceId
        } else {
            error(modulePath.toString() + " is not available")
        }
    }

    internal inner class VcPersistenceProvider : ModuleCacheIdProvider<VcSourceIdT> {
        override fun getCacheId(sourceId: VcSourceIdT): String {
            return when {
                sourceId.source1 != null -> "file:" + sourceId.source1.modulePath.toString()
                sourceId.source2 != null -> "prelude"
                else -> error("Invalid sourceId")
            }
        }

        override fun getModuleId(cacheId: String): VcSourceIdT? =
            when {
                cacheId == "prelude" -> storage.idFromSecond(preludeStorage.preludeSourceId)
                cacheId.startsWith("file:") -> storage.idFromFirst(projectStorage.locateModule(ModulePath(cacheId.substring(5).split('.'))))
                else -> null
            }
    }

    private inner class TypeCheckerPsiTreeChangeListener : PsiTreeChangeAdapter() {
         override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildAddition(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildMovement(event: PsiTreeChangeEvent) {
            processParent(event)
        }

        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
            if (event.child is VcFile) { // whole file has been removed
                for (child in event.child.children) invalidateChild(child)
            } else {
                processChildren(event)
                processParent(event)
            }
        }

        private fun processParent(event: PsiTreeChangeEvent) {
            if (event.file is VcFile) {
                val ancestors = event.parent.ancestors
                val definition = ancestors.filterIsInstance<VcDefinition>().firstOrNull()
                definition?.let {
                    dependencyCollector.update(definition)
                }
            }
        }

        private fun invalidateChild(element : PsiElement) {
            element.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement?) {
                    super.visitElement(element)
                    if (element is GlobalReferable) {
                        dependencyCollector.update(element)
                    }
                }
            })
        }

        private fun processChildren(event: PsiTreeChangeEvent) {
            if (event.file is VcFile) {
                invalidateChild(event.child)
            }
        }
    }

    private inner class VcSourceVersionTracker : SourceVersionTracker<VcSourceIdT> {
        override fun getCurrentVersion(sourceId: VcSourceIdT): Long =
                storage.getAvailableVersion(sourceId)
    }

    private inner class VcSourceInfoProvider : SourceInfoProvider<VcSourceIdT> {
        override fun cacheIdFor(definition: GlobalReferable): String {
            if (definition !is PsiGlobalReferable) throw IllegalStateException()
            return definition.fullName
        }

        override fun sourceOf(definition: GlobalReferable): VcSourceIdT? {
            val module = (definition as? PsiElement)?.containingFile?.originalFile as? VcFile ?: return null
            return if (module.virtualFile.nameWithoutExtension != "Prelude") {
                storage.idFromFirst(projectStorage.locateModule(module))
            } else {
                storage.idFromSecond(preludeStorage.preludeSourceId)
            }
        }
    }
}
