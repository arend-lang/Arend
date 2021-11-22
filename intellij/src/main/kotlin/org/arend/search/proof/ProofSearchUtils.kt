package org.arend.search.proof

import com.intellij.ide.actions.searcheverywhere.FoundItemDescriptor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.castSafelyTo
import org.arend.psi.ArendExpr
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.PsiReferable
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.search.structural.ArendExpressionMatcher
import org.arend.search.structural.deconstructArendExpr
import org.arend.settings.ArendProjectSettings
import org.arend.term.abs.Abstract

fun fetchWeightedElements(
    project: Project,
    settings: ProofSearchUISettings,
    pattern: String,
): Sequence<FoundItemDescriptor<ProofSearchEntry>> = sequence {
    val parsedExpression = runReadAction {
        ArendPsiFactory(project).createExpressionMaybe(pattern)
    } ?: return@sequence
    val matcher = runReadAction { ArendExpressionMatcher(deconstructArendExpr(parsedExpression)) }
    val keys = runReadAction { StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, project) }
    for (definitionName in keys) {
        val list = mutableListOf<PsiReferable>()
        runReadAction {
            StubIndex.getInstance().processElements(
                ArendDefinitionIndex.KEY,
                definitionName,
                project,
                GlobalSearchScope.allScope(project),
                PsiReferable::class.java
            ) { def ->
                if (!settings.includeTestLocations) {
                    val file = PsiUtilCore.getVirtualFile(def)
                    if (file != null && TestSourcesFilter.isTestSources(file, project)) {
                        return@processElements true
                    }
                }
                if (!settings.includeNonProjectLocations) {
                    val file = PsiUtilCore.getVirtualFile(def)
                    if (file != null && !ProjectScope.getProjectScope(project).contains(file)) {
                        return@processElements true
                    }
                }
                val type = def.castSafelyTo<Abstract.FunctionDefinition>()?.resultType?.castSafelyTo<ArendExpr>()
                    ?: return@processElements true
                if (matcher.match(type)) {
                    list.add(def)
                }
                true
            }
        }
        for (def in list) {
            yield(FoundItemDescriptor(ProofSearchEntry(def, matcher.tree), 1))
        }
    }
}

@JvmInline
value class MoreElement(val sequence: Sequence<FoundItemDescriptor<ProofSearchEntry>>)

class ProofSearchUISettings private constructor(
    val includeTestLocations: Boolean,
    val includeNonProjectLocations: Boolean
) {
    constructor(project: Project) : this(
        project.service<ArendProjectSettings>().data.includeTestLocations,
        project.service<ArendProjectSettings>().data.includeNonProjectLocations
    )
}
