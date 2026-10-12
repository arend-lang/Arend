package org.arend.arc

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.parentOfType
import org.arend.ext.module.ModuleLocation
import org.arend.ext.reference.DataContainer
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.*
import org.arend.psi.ext.ArendLongName
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiReferable
import org.arend.term.prettyprint.NameAnnotator
import org.arend.term.prettyprint.NameAnnotator.Kind
import org.arend.util.findInternalLibrary
import java.util.IdentityHashMap

// The text of an .arc view, and what its names stand for
class PrintedView(val text: String, val links: ArcLinks = ArcLinks.EMPTY)

/**
 * What the names in the text of an .arc view stand for, as its printer knows them (see [NameAnnotator]): the
 * referable of each name that refers to something, and where each binder and definition of the text is declared.
 * A name is linked to what it was printed from, so that links do not depend on the text resolving as the sources do.
 */
class ArcLinks(annotations: List<NameAnnotator.Annotation>, val textLength: Int) {
    private val references = annotations.filter { it.kind == Kind.REFERENCE }.sortedBy { it.start }
    // Locals are compared by identity, as are the referables of definitions, which the server keeps
    private val declarations = IdentityHashMap<Referable, Int>().apply {
        for (annotation in annotations) {
            if (annotation.kind == Kind.DECLARATION) putIfAbsent(annotation.referable, annotation.start)
        }
    }

    // The reference whose name contains offset
    fun referenceAt(offset: Int): NameAnnotator.Annotation? {
        var low = 0
        var high = references.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            val annotation = references[middle]
            when {
                offset < annotation.start -> high = middle - 1
                offset >= annotation.end -> low = middle + 1
                else -> return annotation
            }
        }
        return null
    }

    fun declarationOf(referable: Referable): Int? = declarations[referable]

    companion object {
        val EMPTY = ArcLinks(emptyList(), 0)
    }
}

/**
 * The references of .arc views, for [ArendReferenceElement]: a local is linked to its binder, and a definition to where
 * the view of its module shows it, or to its source if there is no view of its module (Prelude, external libraries) or
 * the view does not show it.
 */
object ArcReferences {
    private fun view(element: PsiElement): ArcVirtualFile? = element.containingFile?.originalFile?.virtualFile as? ArcVirtualFile

    // The PSI of the binder or definition declared at offset, if the PSI is of the text the links are of
    private fun declarationAt(file: PsiFile, links: ArcLinks, offset: Int): PsiReferable? =
        if (file.textLength != links.textLength) null else file.findElementAt(offset)?.parentOfType<PsiReferable>(withSelf = true)

    private fun referableOf(element: ArendReferenceElement, view: ArcVirtualFile): Referable? {
        val range = element.textRange
        val annotation = view.links.referenceAt(range.startOffset) ?: return null
        if (range.endOffset == annotation.end) return annotation.referable
        // A prefix of a long name names a parent of the referable
        val identifiers = (element.parent as? ArendLongName)?.refIdentifierList ?: return null
        val index = identifiers.indexOf(element)
        if (index < 0) return null
        var referable: Referable? = annotation.referable
        repeat(identifiers.size - 1 - index) { referable = (referable as? LocatedReferable)?.locatedReferableParent }
        return referable
    }

    // What the reference stands for: the referable it was printed from, or a local referable of the PSI of its binder
    fun resolve(element: ArendReferenceElement): Referable? {
        val view = view(element) ?: return null
        val referable = referableOf(element, view) ?: return null
        if (referable is GlobalReferable) return referable
        val binder = view.links.declarationOf(referable)?.let { declarationAt(element.containingFile, view.links, it) } ?: return null
        return DataLocalReferable(binder, referable.refName)
    }

    fun resolvePsi(element: ArendReferenceElement): PsiElement? {
        val view = view(element) ?: return null
        val referable = referableOf(element, view) ?: return null
        view.links.declarationOf(referable)?.let { offset -> return declarationAt(element.containingFile, view.links, offset) }
        return if (referable is GlobalReferable) target(view.project, referable) else null
    }

    private fun viewOf(project: Project, location: ModuleLocation?): ArcVirtualFile? {
        if (location == null || location.locationKind != ModuleLocation.LocationKind.SOURCE) return null
        if (project.findInternalLibrary(location.libraryName) !is ArendModuleConfigService) return null
        return ArcFileSystem.getInstance().findFile(project, location.libraryName, location.modulePath)
    }

    // A definition or module of another view
    private fun target(project: Project, referable: GlobalReferable): PsiElement? {
        val view = viewOf(project, (referable as? LocatedReferable)?.location) ?: return sourceOf(referable)
        val file = PsiManager.getInstance(project).findFile(view) ?: return sourceOf(referable)
        if (referable is ModuleReferable) return file
        if (!view.hasText) return ArcPendingTarget(view, referable, file)
        return view.links.declarationOf(referable)?.let { declarationAt(file, view.links, it) } ?: sourceOf(referable)
    }

    fun sourceOf(referable: Referable): PsiElement? {
        (referable.abstractReferable as? PsiElement)?.let { return it }
        val data = (referable as? DataContainer)?.data
        return (data as? SmartPsiElementPointer<*>)?.element ?: data as? PsiElement
    }
}

/**
 * A definition in a view that is not printed yet: navigating to it shows the view, which prints it, and goes to the
 * definition once it is printed.
 */
class ArcPendingTarget(private val view: ArcVirtualFile, private val referable: GlobalReferable, private val file: PsiFile) : FakePsiElement() {
    override fun getParent(): PsiElement = file

    override fun getName(): String = referable.refName

    override fun canNavigate() = true

    override fun navigate(requestFocus: Boolean) {
        view.project.service<ArcViewService>().navigateWhenPrinted(view, referable, requestFocus)
    }
}
