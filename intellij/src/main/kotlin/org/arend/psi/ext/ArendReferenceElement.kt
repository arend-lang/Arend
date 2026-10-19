package org.arend.psi.ext

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.arend.arc.ArcFile
import org.arend.arc.ArcReferences
import org.arend.naming.reference.*
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.term.abs.AbstractReference
import org.arend.typechecking.computation.UnstoppableCancellationIndicator

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
        // The module of an .arc view is not resolved by the server: its printer knows what the names stand for
        if (containingFile is ArcFile) return ArcReferences.resolve(this)

        val cached = cachedReferable
        if (cached != null && toPsi(cached)?.isValid != false) {
            return if (cached == TCDefReferable.NULL_REFERABLE) null else cached
        }
        if (cached != null) putUserData(ArendReferenceKey, null)

        val module = referenceModule ?: return null
        project.service<ArendServerService>().server.getCheckerFor(listOf(module)).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
        return cachedOrNull
    }

    fun resolvePsi(): PsiElement? =
        if (containingFile is ArcFile) ArcReferences.resolvePsi(this) else resolve()?.let { toPsi(it) }

    companion object {
        private fun toPsi(referable: Referable): PsiElement? = when (val ref = referable.abstractReferable) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            is DataModuleReferable -> ref.data as? PsiElement
            else -> null
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
