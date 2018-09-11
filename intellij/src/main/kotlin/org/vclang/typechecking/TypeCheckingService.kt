package org.vclang.typechecking

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.*
import com.intellij.psi.*
import com.intellij.psi.impl.AnyPsiChangeListener
import com.intellij.psi.impl.PsiManagerImpl
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.jetbrains.jetpad.vclang.core.definition.Definition
import com.jetbrains.jetpad.vclang.library.LibraryManager
import com.jetbrains.jetpad.vclang.module.scopeprovider.EmptyModuleScopeProvider
import com.jetbrains.jetpad.vclang.module.scopeprovider.LocatingModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.reference.converter.SimpleReferableConverter
import com.jetbrains.jetpad.vclang.term.prettyprint.PrettyPrinterConfig
import com.jetbrains.jetpad.vclang.typechecking.SimpleTypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.order.dependency.DependencyCollector
import com.jetbrains.jetpad.vclang.typechecking.order.dependency.DependencyListener
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.module.VcPreludeLibrary
import org.vclang.module.VcRawLibrary
import org.vclang.module.util.defaultRoot
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcElementTypes
import org.vclang.psi.VcFile
import org.vclang.psi.ancestors
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.impl.DataDefinitionAdapter
import org.vclang.resolving.VcReferableConverter
import org.vclang.resolving.VcResolveCache
import org.vclang.typechecking.error.LogErrorReporter

interface TypeCheckingService {
    val libraryManager: LibraryManager

    val typecheckerState: TypecheckerState

    val dependencyListener: DependencyListener

    val referableConverter: ReferableConverter

    val project: Project

    val prelude: VcFile?

    fun getTypechecked(definition: VcDefinition): Definition?

    fun updateDefinition(referable: LocatedReferable)

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
    private val libraryErrorReporter = LogErrorReporter(PrettyPrinterConfig.DEFAULT)
    override val libraryManager = LibraryManager(VcLibraryResolver(project), EmptyModuleScopeProvider.INSTANCE, null, libraryErrorReporter, libraryErrorReporter)

    private val simpleReferableConverter = SimpleReferableConverter()
    override val referableConverter: ReferableConverter
        get() = VcReferableConverter(project, simpleReferableConverter)

    init {
        libraryManager.moduleScopeProvider = LocatingModuleScopeProvider(libraryManager)

        PsiManager.getInstance(project).addPsiTreeChangeListener(TypeCheckerPsiTreeChangeListener())
        project.messageBus.connect(project).subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, object : AnyPsiChangeListener.Adapter() {
            override fun beforePsiChanged(isPhysical: Boolean) {
                VcResolveCache.clearCache()
            }
        })
        VirtualFileManager.getInstance().addVirtualFileListener(MyVirtualFileListener(), project)
    }

    override val prelude: VcFile?
        get() {
            for (library in libraryManager.registeredLibraries) {
                if (library is VcPreludeLibrary) {
                    return library.prelude
                }
            }
            return null
        }

    override fun getTypechecked(definition: VcDefinition) =
        simpleReferableConverter.toDataLocatedReferable(definition)?.let { typecheckerState.getTypechecked(it) }

    override fun updateDefinition(referable: LocatedReferable) {
        simpleReferableConverter.remove(referable)?.let {
            for (ref in dependencyListener.update(it)) {
                PsiLocatedReferable.fromReferable(ref)?.let { simpleReferableConverter.remove(it) }
            }
        }

        if (referable is ClassReferable) {
            for (field in referable.fieldReferables) {
                simpleReferableConverter.remove(field)
            }
        } else if (referable is DataDefinitionAdapter) {
            for (constructor in referable.constructors) {
                simpleReferableConverter.remove(constructor)
            }
        }
    }

    private inner class MyVirtualFileListener : VirtualFileListener {
        override fun beforeFileDeletion(event: VirtualFileEvent) {
            process(event, event.fileName, event.parent, null)
        }

        override fun fileCreated(event: VirtualFileEvent) {
            process(event, event.fileName, null, event.parent)
        }

        private fun process(event: VirtualFileEvent, fileName: String, oldParent: VirtualFile?, newParent: VirtualFile?) {
            if (oldParent == null && newParent == null) {
                return
            }
            if (fileName.endsWith(FileUtils.LIBRARY_EXTENSION)) {
                val module = ModuleUtil.findModuleForFile(event.file, project) ?: return
                if (fileName == module.name + FileUtils.LIBRARY_EXTENSION) {
                    val root = module.defaultRoot
                    if (root != null && root == oldParent) {
                        libraryManager.getRegisteredLibrary(module.name)?.let { libraryManager.unloadLibrary(it) }
                    }
                    if (root != null && root == newParent) {
                        libraryManager.loadLibrary(VcRawLibrary(module, typecheckerState))
                    }
                }
            }
        }

        override fun fileMoved(event: VirtualFileMoveEvent) {
            process(event, event.fileName, event.oldParent, event.newParent)
        }

        override fun fileCopied(event: VirtualFileCopyEvent) {
            process(event, event.fileName, null, event.parent)
        }

        override fun propertyChanged(event: VirtualFilePropertyEvent) {
            if (event.propertyName == VirtualFile.PROP_NAME) {
                process(event, event.oldValue as String, event.parent, null)
                process(event, event.newValue as String, null, event.parent)
            }
        }
    }

    private inner class TypeCheckerPsiTreeChangeListener : PsiTreeChangeAdapter() {
         override fun beforeChildrenChange(event: PsiTreeChangeEvent) {
            processParent(event, false)
        }

        override fun beforeChildAddition(event: PsiTreeChangeEvent) {
            processParent(event, true)
        }

        override fun beforeChildReplacement(event: PsiTreeChangeEvent) {
            processParent(event, false)
        }

        override fun beforeChildMovement(event: PsiTreeChangeEvent) {
            processParent(event, false)
        }

        override fun beforeChildRemoval(event: PsiTreeChangeEvent) {
            if (event.child is VcFile) { // whole file has been removed
                for (child in event.child.children) invalidateChild(child)
            } else {
                processChildren(event)
                processParent(event, true)
            }
        }

        private fun processParent(event: PsiTreeChangeEvent, checkCommentStart: Boolean) {
            if (event.file !is VcFile) {
                return
            }
            val child = event.child
            if (child is PsiErrorElement ||
                child is PsiWhiteSpace ||
                child is LeafPsiElement && isComment(child.node.elementType)) {
                return
            }
            val oldChild = event.oldChild
            val newChild = event.newChild
            if (oldChild is PsiErrorElement && newChild is PsiErrorElement ||
                oldChild is PsiWhiteSpace && newChild is PsiWhiteSpace ||
                oldChild is LeafPsiElement && isComment(oldChild.node.elementType) && newChild is LeafPsiElement && isComment(newChild.node.elementType)) {
                return
            }

            if (checkCommentStart) {
                var node = (child as? VcCompositeElement)?.node ?: child as? LeafPsiElement
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

            val ancestors = event.parent.ancestors
            val definition = ancestors.filterIsInstance<VcDefinition>().firstOrNull() ?: return
            updateDefinition(definition)
        }

        private fun invalidateChild(element : PsiElement) {
            element.accept(object : PsiRecursiveElementVisitor() {
                override fun visitElement(element: PsiElement?) {
                    super.visitElement(element)
                    if (element is LocatedReferable) {
                        updateDefinition(element)
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
}

private fun isComment(element: IElementType) = element == VcElementTypes.BLOCK_COMMENT || element == VcElementTypes.LINE_COMMENT
