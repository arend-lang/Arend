package org.arend.navigation

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.naming.reference.MetaReferable
import org.arend.prelude.Prelude
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendNamedElementIndex
import org.arend.server.ArendServerService

class ArendSymbolNavigationContributor : ArendNavigationContributorBase<PsiReferable>(
    ArendNamedElementIndex.KEY,
    PsiReferable::class.java
) {
    override fun getGeneratedItems(project: Project?): Map<String, List<PsiLocatedReferable>> {
        val service = project?.service<ArendServerService>()?.server
        service ?: return emptyMap()
        val result = hashMapOf<String, List<PsiLocatedReferable>>()
        if (Prelude.isInitialized()) {
            Prelude.forEach {
              def -> (def.referable.data as? PsiLocatedReferable)?.let { result[def.name] = listOf(it) }
            }
        }

        service.libraries.map { service.getLibrary(it) }.forEach { lib -> lib?.generatedNames?.forEach {
          entry -> run {
            val value = entry.value
            if (value is MetaReferable) {
              (value.data as? PsiLocatedReferable)?.let { result.put(entry.key, listOf(it)) }
            }
        } } }
        return result
    }
}
