package org.arend.psi.ext

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.arend.naming.reference.*
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.term.abs.AbstractReference
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import java.util.Collections.singletonList

interface ArendReferenceElement : ArendReferenceContainer, AbstractReference {
    val rangeInElement: TextRange

    private object ArendReferenceKey : Key<Referable>("AREND_REFERENCE_KEY")

    val cachedReferable: Referable?
        get() = getUserData(ArendReferenceKey)

    val cachedOrNull: Referable?
        get() {
            val cached = getUserData(ArendReferenceKey)
            return if (cached == TCDefReferable.NULL_REFERABLE) null else cached
        }

    val isCachedErrorReference: Boolean
        get() = cachedReferable == TCDefReferable.NULL_REFERABLE

    fun putResolved(referable: Referable?) {
        putUserData(ArendReferenceKey, referable ?: TCDefReferable.NULL_REFERABLE)
    }

    fun resolve(): Referable? {
        val cached = cachedReferable
        if (cached != null) {
            return if (cached == TCDefReferable.NULL_REFERABLE) null else cached
        }

        val module = referenceModule ?: return null
        project.service<ArendServerService>().server.getCheckerFor(listOf(module)).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
        return cachedOrNull
    }

    fun resolvePsi(): PsiElement? {
        val statCmd = this.parent.parent as? ArendStatCmd
        val longName = this.parent as? ArendLongName
        if (longName != null && statCmd != null) {
            val fileName = this.text
            val refPath = longName.refIdentifierList.takeWhile { it != this }
            if (refPath.size < longName.refIdentifierList.size - 1) {
                val dirPath = (refPath.map { it.refName } + singletonList(fileName)).fold("", { a, b -> "$a/$b" })
                return getDirectoryFromIDEAIndex(fileName, dirPath, project)
            }
        }

        return when (val ref = resolve()?.abstractReferable) {
            is PsiElement -> ref
            is DataModuleReferable -> ref.data as? PsiElement
            else -> null
        }
    }

    companion object {
        fun getDirectoryFromIDEAIndex(dirName: String, dirPath : String, project: Project) : PsiDirectory? {
            val matches = FilenameIndex.getVirtualFilesByName(project, dirName, true, GlobalSearchScope.allScope(project))
            val goodMatches = matches.filter { (it.isDirectory) && it.path.endsWith(dirPath) }
            val virtualFile = goodMatches.firstOrNull()
            val psiManager = PsiManager.getInstance(project)
            return virtualFile?.let{ psiManager.findDirectory(virtualFile) }
        }

        fun cacheResolved(reference: UnresolvedReference, resolved: Referable) {
            val references = reference.getReferenceList()
            var referable = resolved
            for (i in reference.getPath().indices.reversed()) {
                if (i < references.size && references[i] != null) {
                    (references[i] as? ArendReferenceElement)?.putResolved(referable)
                }
                referable = (referable as? LocatedReferable)?.locatedReferableParent ?: break
            }
        }
    }
}
