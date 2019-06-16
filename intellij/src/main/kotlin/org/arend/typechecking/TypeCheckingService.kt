package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.core.definition.Definition
import org.arend.error.DummyErrorReporter
import org.arend.error.GeneralError
import org.arend.library.LibraryManager
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ModulePath
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.SimpleReferableConverter
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.order.dependency.DependencyCollector
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.util.FullName
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

interface TypeCheckingService {
    val libraryManager: LibraryManager

    val typecheckerState: TypecheckerState

    val dependencyListener: DependencyListener

    val project: Project

    val prelude: ArendFile?

    val updatedModules: HashSet<ModulePath>

    val isInitialized: Boolean

    fun initialize(): Boolean

    fun newReferableConverter(withPsiReferences: Boolean): ArendReferableConverter

    fun getTypechecked(definition: ArendDefinition): Definition?

    fun updateDefinition(referable: LocatedReferable)

    fun processEvent(child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, additionOrRemoval: Boolean)

    fun reportError(error: GeneralError)

    fun getErrors(file: ArendFile): List<GeneralError>

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(override val project: Project) : TypeCheckingService {
    override val typecheckerState = SimpleTypecheckerState()
    override val dependencyListener = DependencyCollector(typecheckerState)
    private val libraryErrorReporter = NotificationErrorReporter(project, PrettyPrinterConfig.DEFAULT)
    override val libraryManager = LibraryManager(ArendLibraryResolver(project), null, libraryErrorReporter, libraryErrorReporter)

    private val simpleReferableConverter = SimpleReferableConverter()

    override val updatedModules = HashSet<ModulePath>()

    override fun newReferableConverter(withPsiReferences: Boolean) =
        ArendReferableConverter(if (withPsiReferences) project else null, simpleReferableConverter)

    override var isInitialized = false
        private set

    private val listener = TypeCheckerPsiTreeChangeListener()

    private val errorMap = WeakHashMap<SmartPsiElementPointer<PsiLocatedReferable>, MutableList<GeneralError>>()

    override fun initialize(): Boolean {
        if (isInitialized) {
            return false
        }

        // Initialize prelude
        val preludeLibrary = ArendPreludeLibrary(project, typecheckerState)
        libraryManager.loadLibrary(preludeLibrary)
        val referableConverter = newReferableConverter(false)
        val concreteProvider = PsiConcreteProvider(project, referableConverter, DummyErrorReporter.INSTANCE, null)
        preludeLibrary.resolveNames(referableConverter, concreteProvider, libraryManager.libraryErrorReporter)
        Prelude.PreludeTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), typecheckerState, concreteProvider).typecheckLibrary(preludeLibrary)

        // Set the listener that updates typechecked definitions
        PsiManager.getInstance(project).addPsiTreeChangeListener(listener)

        isInitialized = true
        return true
    }

    override val prelude: ArendFile?
        get() {
            for (library in libraryManager.registeredLibraries) {
                if (library is ArendPreludeLibrary) {
                    return library.prelude
                }
            }
            return null
        }

    override fun getTypechecked(definition: ArendDefinition) =
        simpleReferableConverter.toDataLocatedReferable(definition)?.let { typecheckerState.getTypechecked(it) }

    private fun removeDefinition(referable: LocatedReferable): TCReferable? {
        val fullName = FullName(referable)
        val tcReferable = simpleReferableConverter.remove(referable, fullName) ?: return null
        val curRef = PsiLocatedReferable.fromReferable(referable)
        val prevRef = PsiLocatedReferable.fromReferable(tcReferable)
        if (curRef != null && prevRef != null && prevRef != curRef) {
            if (FullName(prevRef) == fullName) {
                simpleReferableConverter.putIfAbsent(referable, tcReferable)
            }
            return null
        }

        if (curRef != null) {
            errorMap.remove(SmartPointerManager.createPointer(curRef))
        }

        val tcTypecheckable = tcReferable.typecheckable ?: return null
        tcTypecheckable.location?.let { updatedModules.add(it) }
        return tcTypecheckable
    }

    override fun updateDefinition(referable: LocatedReferable) {
        val tcReferable = removeDefinition(referable) ?: return
        for (ref in dependencyListener.update(tcReferable)) {
            removeDefinition(ref)
        }
        if (referable is ArendDefFunction && referable.useKw != null) {
            (referable.parentGroup as? ArendDefinition)?.let { updateDefinition(it) }
        }
    }

    override fun processEvent(child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, additionOrRemoval: Boolean) {
        listener.processParent(child, oldChild, newChild, parent, additionOrRemoval)
    }

    override fun reportError(error: GeneralError) {
        if (!error.isTypecheckingError || error.cause !is ArendCompositeElement) {
            return
        }

        error.affectedDefinitions.mapNotNull {
            runReadAction {
                val ref = PsiLocatedReferable.fromReferable(it)
                if (ref is PsiLocatedReferable) {
                    errorMap.computeIfAbsent(SmartPointerManager.createPointer(ref)) { ArrayList() }.add(error)
                }
            }
        }
    }

    override fun getErrors(file: ArendFile): List<GeneralError> {
        val list = ArrayList<GeneralError>()
        for (entry in errorMap) {
            val ref = entry.key.element
            if (ref != null && ref.containingFile == file) {
                list.addAll(entry.value)
            }
        }
        return list
    }

    private inner class TypeCheckerPsiTreeChangeListener : PsiTreeChangeAdapter() {
        override fun childAdded(event: PsiTreeChangeEvent) {
            processParent(event, true)
        }

        override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
            processParent(event, false)
        }

        override fun beforeChildMovement(event: PsiTreeChangeEvent) {
            processParent(event, false)
        }

        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
            val child = event.child
            if (child is ArendFile) { // whole file has been removed
                invalidateChildren(child)
            } else {
                processParent(event, true)
            }
        }

        private fun isDynamicDef(elem: PsiElement?) = elem is ArendClassStat && (elem.definition != null || elem.defModule != null)

        private fun processParent(event: PsiTreeChangeEvent, checkCommentStart: Boolean) {
            if (event.file is ArendFile) {
                processChildren(event.child)
                processChildren(event.oldChild)
                processParent(event.child, event.oldChild, event.newChild, event.parent ?: event.oldParent, checkCommentStart)
            }
        }

        fun processParent(child: PsiElement?, oldChild: PsiElement?, newChild: PsiElement?, parent: PsiElement?, checkCommentStart: Boolean) {
            if (child is PsiErrorElement ||
                child is PsiWhiteSpace ||
                child is ArendWhere ||
                isDynamicDef(child) ||
                child is LeafPsiElement && AREND_COMMENTS.contains(child.node.elementType)) {
                return
            }
            if (oldChild is PsiWhiteSpace && newChild is PsiWhiteSpace ||
                (oldChild is ArendWhere || oldChild is PsiErrorElement || isDynamicDef(oldChild)) && (newChild is ArendWhere || newChild is PsiErrorElement || isDynamicDef(newChild)) ||
                oldChild is LeafPsiElement && AREND_COMMENTS.contains(oldChild.node.elementType) && newChild is LeafPsiElement && AREND_COMMENTS.contains(newChild.node.elementType)) {
                return
            }

            if (checkCommentStart) {
                var node = (child as? ArendCompositeElement)?.node ?: child as? LeafPsiElement
                while (node != null && node !is LeafPsiElement) {
                    val first = node.firstChildNode
                    if (first == null || node.lastChildNode != first) {
                        break
                    }
                    node = first
                }
                if (node is LeafPsiElement && node.textLength == 1) {
                    val ch = node.charAt(0)
                    if (ch == '-' || ch == '{' || ch == '}') {
                        return
                    }
                }
            }

            ((parent as? ArendDefIdentifier)?.parent as? ArendGroup)?.let { invalidateChildren(it) }

            var elem = parent
            while (elem != null) {
                if (elem is ArendWhere || elem is ArendFile || isDynamicDef(elem)) {
                    return
                }
                if (elem is ArendDefinition) {
                    updateDefinition(elem)
                    return
                }
                elem = elem.parent
            }
        }

        private fun invalidateChildren(group: ArendGroup) {
            if (group is ArendDefinition) {
                updateDefinition(group)
            }
            for (subgroup in group.subgroups) {
                invalidateChildren(subgroup)
            }
            for (subgroup in group.dynamicSubgroups) {
                invalidateChildren(subgroup)
            }
        }

        private fun processChildren(element: PsiElement?) {
            when (element) {
                is ArendGroup -> invalidateChildren(element)
                is ArendStatement -> {
                    element.definition?.let { invalidateChildren(it) }
                    element.defModule?.let { invalidateChildren(it) }
                }
            }
        }
    }
}
