package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ScopeFactory
import org.arend.typechecking.instance.provider.InstanceProvider
import org.arend.typechecking.instance.provider.InstanceProviderSet
import org.arend.typechecking.provider.ConcreteProvider
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.moduleScopeProvider
import org.arend.resolving.ArendReferableConverter


class PsiInstanceProviderSet(private val concreteProvider: ConcreteProvider) : InstanceProviderSet() {
    override fun get(referable: TCReferable): InstanceProvider? {
        val result = super.get(referable)
        if (result != null) {
            return result
        }

        val psiElement = PsiLocatedReferable.fromReferable(referable) ?: return null
        return runReadAction {
            val file = psiElement.containingFile as? ArendFile ?: return@runReadAction null
            if (collectInstances(file, CachingScope.make(ScopeFactory.parentScopeForGroup(file, file.moduleScopeProvider, true)), concreteProvider, ArendReferableConverter)) super.get(referable) else null
        }
    }
}