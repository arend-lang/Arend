package org.arend.search.proof

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.TestSourcesFilter
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.elementType
import com.intellij.util.SmartList
import org.arend.documentation.ArendKeyword.Companion.AREND_KEYWORDS
import org.arend.ext.reference.DataContainer
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.scope.EmptyScope
import org.arend.proof.ArendExpressionMatcher
import org.arend.proof.ProofSearchQuery
import org.arend.proof.Utils.getSignatures
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.refactoring.psiOfConcrete
import org.arend.refactoring.rangeOfConcrete
import org.arend.search.collectSearchScopes
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.settings.ArendProjectSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.util.caching

data class ProofSearchEntry(val def: ReferableBase<*>, val signature: RenderingInfo)

/**
 * @return null as an element of the sequence, if the search couldn't find any matching result for a long time.
 * It can be used for an interruption check, so nulls can be safely skipped while retrieving the results if you don't care
 * about performance.
 */
fun generateProofSearchResults(
    project: Project,
    pattern: String,
): Sequence<ProofSearchEntry?> = sequence {
    val settings = ProofSearchUISettings(project)
    val query = (ProofSearchQuery.fromString(pattern) as? ProofSearchQuery.ParsingResult.OK<ProofSearchQuery>)?.value
        ?: return@sequence
    val matcher = ArendExpressionMatcher(query)

    val listedIdentifiers = query.getAllIdentifiers()
    val server = project.service<ArendServerService>().server

    val keys = DumbService.getInstance(project).runReadActionInSmartMode(Computable {
        StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, project)
    })

    val searchScope = if (listedIdentifiers.isNotEmpty()) {
        val scopes = collectSearchScopes(listedIdentifiers, GlobalSearchScope.allScope(project).isSearchInLibraries, project)
        runReadAction { scopes.map { GlobalSearchScope.fileScope(project, it) }.reduce(GlobalSearchScope::union) }
    } else {
        GlobalSearchScope.allScope(project)
    }

    var idleCounter = 0

    for (definitionName in keys) {
        val list = SmartList<ProofSearchEntry>()
        runReadAction {
            StubIndex.getInstance().processElements(ArendDefinitionIndex.KEY, definitionName, project, searchScope, PsiReferable::class.java) { def ->
                if (!settings.checkAllowed(def)) return@processElements true
                if (def !is ReferableBase<*>) return@processElements true

                val signatures = getSignatures(getTcDefReferable(def)?.let { server.getResolvedDefinition(it) }?.definition)
                    ?: return@processElements true
                for (signature in signatures) {
                    val psi = (signature.first.data as? LocatedReferableImpl)?.data as? ReferableBase<*> ?: continue
                    val parameters = signature.second
                    val codomain = signature.third
                    val info = lazy(LazyThreadSafetyMode.NONE) {
                        RenderingInfo(parameters.map(::gatherHighlightingData), gatherHighlightingData(codomain))
                    }
                    val scope = def.tcReferable?.let { server.getReferableScope(it) } ?: EmptyScope.INSTANCE

                    val result = matcher.match(parameters, codomain, scope)
                        ?: return@processElements true
                    val parameterResults = result.inPattern
                    val codomainResults = result.inCodomain

                    val parameterRangesRegistry = mutableMapOf<Int, List<TextRange>>()
                    val parameterExpressionRegistry = mutableMapOf<Int, Concrete.Expression>()
                    val rangeComputer = caching { e : Concrete.Expression -> rangeOfConcrete(e) }
                    for (result in parameterResults) {
                        val parameterConcrete = result.proj1
                        val ranges = result.proj2
                        val index = parameters.indexOf(parameterConcrete)
                        val existing = parameterRangesRegistry.getOrDefault(index, emptyList())
                        parameterRangesRegistry[index] = existing + ranges.map { rangeComputer(it).shiftLeft(rangeComputer(parameterConcrete).startOffset) }
                        parameterExpressionRegistry[index] = parameterConcrete
                    }
                    val rangeOfCodomain = rangeComputer(codomain)
                    val codomainRange = codomainResults.map { rangeComputer(it).shiftLeft(rangeOfCodomain.startOffset) }

                    val text = psi.containingFile.text
                    list.add(ProofSearchEntry(psi,
                        info.value.copy(
                            parameters = info.value.parameters.mapIndexedNotNull { index, data -> data.takeIf { index in parameterRangesRegistry && index in parameterExpressionRegistry }
                                ?.copy(typeRep = text.substring(rangeComputer(parameterExpressionRegistry[index]!!).startOffset, rangeComputer(parameterExpressionRegistry[index]!!).endOffset), match = parameterRangesRegistry[index]!!) },
                            codomain = info.value.codomain.copy(typeRep = text.substring(rangeOfCodomain.startOffset, rangeOfCodomain.endOffset), match = codomainRange))))
                }
                true
            }
        }
        if (list.isNotEmpty()) {
            for (def in list) {
                yield(def)
            }
        } else {
            idleCounter += 1
            if (idleCounter >= 50) {
                idleCounter = 0
                yield(null)
            }
        }
    }
}

private fun Any?.getPsi() : PsiElement? {
    if (this is PsiElement) return this
    if (this is DataContainer) return data as? PsiElement
    return null
}

data class RenderingInfo(val parameters: List<ProofSearchHighlightingData>, val codomain: ProofSearchHighlightingData)
data class ProofSearchHighlightingData(val typeRep: String, val keywords: List<TextRange>, val match: List<TextRange>)

private fun getTcDefReferable(globalReferable: ReferableBase<*>): TCDefReferable? {
    globalReferable.tcReferable?.let { return it }

    val project = globalReferable.project
    val arendServer = project.service<ArendServerService>().server
    val targetFile = globalReferable.containingFile as? ArendFile
    val targetFileLocation = targetFile?.moduleLocation
    if (targetFileLocation != null) {
        val requester = ArendServerRequesterImpl(project)
        requester.doUpdateModule(arendServer, targetFileLocation, targetFile)
        //TODO: This operation may be slow
        arendServer.getCheckerFor(listOf(targetFileLocation)).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
    }
    return globalReferable.tcReferable
}

private fun gatherHighlightingData(expr: Concrete.Expression) : ProofSearchHighlightingData {
    return (expr.getPsi() as? ArendExpr)?.let(::getHighlightingData) ?: basicHighlightingData(expr)
}

private fun basicHighlightingData(concrete: Concrete.Expression): ProofSearchHighlightingData =
    ProofSearchHighlightingData(concrete.toString(), emptyList(), emptyList())

private fun getHighlightingData(psiType: ArendExpr): ProofSearchHighlightingData {
    val keywords = mutableListOf<TextRange>()
    psiType.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (AREND_KEYWORDS.contains(element.elementType)) {
                keywords.add(element.textRange)
            }
            super.visitElement(element)
        }
    })
    val baseTextOffset = psiType.textOffset
    return ProofSearchHighlightingData(
        psiType.text,
        keywords.map { TextRange(it.startOffset - baseTextOffset, it.endOffset - baseTextOffset) },
        emptyList()
    )
}

sealed interface ProofSearchUIEntry

data class MoreElement(val alreadyProcessed: Int, val sequence: Sequence<ProofSearchEntry?>) : ProofSearchUIEntry

@JvmInline
value class DefElement(val entry: ProofSearchEntry): ProofSearchUIEntry

class ProofSearchUISettings(private val project: Project) {

    private val includeTestLocations: Boolean = project.service<ArendProjectSettings>().data.includeTestLocations

    private val includeNonProjectLocations: Boolean = project.service<ArendProjectSettings>().data.includeNonProjectLocations

    private val truncateResults: Boolean = project.service<ArendProjectSettings>().data.truncateSearchResults

    fun checkAllowed(element: PsiElement): Boolean {
        if (includeNonProjectLocations && includeTestLocations) {
            return true
        }
        val file = PsiUtilCore.getVirtualFile(element) ?: return true
        return (includeTestLocations || !TestSourcesFilter.isTestSources(file, project))
                && (includeNonProjectLocations || ProjectScope.getProjectScope(project).contains(file))
    }

    fun shouldLimitSearch() : Boolean = truncateResults
}

fun getCompleteModuleLocation(def: ReferableBase<*>): String? = def.tcReferable?.refFullName?.module?.modulePath?.toString() ?: "???"