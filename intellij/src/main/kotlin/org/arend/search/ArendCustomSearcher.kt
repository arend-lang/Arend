package org.arend.search

import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.impl.cache.impl.id.IdIndexEntry
import com.intellij.psi.impl.search.PsiSearchHelperImpl
import com.intellij.psi.search.*
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.util.Processors
import com.intellij.util.containers.mapSmartSet
import com.intellij.util.indexing.FileBasedIndex
import org.arend.error.DummyErrorReporter
import org.arend.ext.concrete.definition.ConcreteDefinition
import org.arend.ext.module.FullName
import org.arend.ext.module.ModuleLocation
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ArendFile
import org.arend.psi.ArendFileScope
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler.Util.isDefIdentifierFromNsId
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.term.concrete.Concrete
import org.arend.typechecking.ProgressCancellationIndicator
import org.arend.typechecking.computation.UnstoppableCancellationIndicator

class ArendCustomSearcher : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>() {
    override fun processQuery(parameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
        if (parameters.elementToSearch !is ArendCompositeElement) return

        var elementToSearch_var : PsiLocatedReferable? = null
        runReadAction {
            elementToSearch_var = when (val e = parameters.elementToSearch) {
                is ReferableBase<*> -> e
                is ArendAliasIdentifier -> e.parent?.parent as? ReferableBase<*>
                is ArendDefIdentifier ->
                    if (isDefIdentifierFromNsId(e)) ((e.parent as ArendNsId).refIdentifier.resolve as? ReferableBase<*>) else null
                else -> null
            }
        }
        val elementToSearch = elementToSearch_var ?: return
        val scope = parameters.scopeDeterminedByUser
        val project = parameters.project
        val tasks = ArrayList<Pair<String, SearchScope>>()

        val modules = LinkedHashSet<ModuleLocation>()

        runReadAction {
            val standardName = elementToSearch.refName
            val aliasName = elementToSearch.aliasName
            if (scope is GlobalSearchScope) {
                collectSearchScopes(listOf(standardName), scope.isSearchInLibraries, project).forEach {
                    val arendFile = PsiManager.getInstance(project).findFile(it) as? ArendFile
                    if (arendFile != null) {
                        tasks.add(standardName to LocalSearchScope(arendFile))
                        arendFile.moduleLocation?.let { module -> modules.add(module) }
                    }
                }
                if (aliasName != null) {
                    collectSearchScopes(listOf(aliasName), scope.isSearchInLibraries, project).forEach {
                        val arendFile = PsiManager.getInstance(project).findFile(it) as? ArendFile
                        if (arendFile != null) {
                            tasks.add(aliasName to LocalSearchScope(arendFile))
                            arendFile.moduleLocation?.let { module -> modules.add(module) }
                        }
                    }
                }
            } else if (aliasName != null) {
                tasks.add(Pair(aliasName, scope))
            }
            tasks.add(Pair(standardName, scope))
        }

        if (modules.isNotEmpty()) {
            val server = project.getService(ArendServerService::class.java).server
            val checker = server.getCheckerFor(modules.toList())
            val indicator = ProgressManager.getInstance().progressIndicator
            val progressReporter = object : ProgressReporter<ModuleLocation> {
                override fun beginProcessing(numberOfItems: Int) {
                    indicator?.isIndeterminate = false
                    indicator?.fraction = 0.0
                }

                override fun beginItem(item: ModuleLocation) {
                    indicator?.text = "Resolving $item"
                }

                override fun endItem(item: ModuleLocation) {
                    indicator?.fraction = indicator.fraction + 1.0 / modules.size
                }
            }

            checker.resolveModules(if (indicator == null) UnstoppableCancellationIndicator.INSTANCE else ProgressCancellationIndicator(indicator), progressReporter)

            if (elementToSearch is ArendClassField || elementToSearch is ArendFieldDefIdentifier) {
                // Collect the ambient PsiLocatedReferables for each usage of the class field
                val searchContext = (UsageSearchContext.IN_CODE.toInt() or UsageSearchContext.IN_FOREIGN_LANGUAGES.toInt()).toShort()
                val referablesToTypecheck = LinkedHashSet<FullName>()
                for (task in tasks) {
                    PsiSearchHelperImpl(project).processElementsWithWord({ element, offsetInElement ->
                        val refs = runReadAction {
                            PsiReferenceService.getService().getReferences(element, PsiReferenceService.Hints(elementToSearch, offsetInElement))
                        }
                        for (ref in refs) {
                            if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
                                val ambient = runReadAction { ref.element.ancestor<PsiLocatedReferable>() }
                                if (ambient is ReferableBase<*> && ambient.tcReferable?.typechecked == null) {
                                    val fullName = runReadAction { ambient.fullName }
                                    if (fullName.module != null) referablesToTypecheck.add(fullName)
                                }
                            }
                        }
                        true
                    }, task.second, task.first, searchContext, true)
                }
                // Typecheck the collected definitions
                if (referablesToTypecheck.isNotEmpty()) {
                    val tcIndicator = if (indicator == null) UnstoppableCancellationIndicator.INSTANCE else ProgressCancellationIndicator(indicator)
                    val totalCount = referablesToTypecheck.size
                    val tcProgressReporter = object : ProgressReporter<List<Concrete.ResolvableDefinition>> {
                        override fun beginProcessing(numberOfItems: Int) {
                            indicator?.isIndeterminate = false
                            indicator?.fraction = 0.0
                        }

                        override fun beginItem(item: List<Concrete.ResolvableDefinition>) {
                            indicator?.text = "Typechecking ${item.firstOrNull()?.data?.refName ?: ""}"
                        }

                        override fun endItem(item: List<Concrete.ResolvableDefinition>) {
                            for (i in item) {
                                referablesToTypecheck.remove(((i as? ConcreteDefinition)?.ref as? LocatedReferable)?.refFullName)
                            }
                            indicator?.fraction = 1 - (referablesToTypecheck.size.toDouble() / totalCount)
                        }
                    }
                    checker.typecheck(referablesToTypecheck.toList(), DummyErrorReporter.INSTANCE, tcIndicator, tcProgressReporter)
                }
            }
        }

        val searchContext = (UsageSearchContext.IN_CODE.toInt() or UsageSearchContext.IN_FOREIGN_LANGUAGES.toInt()).toShort()
        for (task in tasks) {
            parameters.optimizer.searchWord(task.first, task.second, searchContext, true, elementToSearch, object: RequestResultProcessor(){
                override fun processTextOccurrence(element: PsiElement, offsetInElement: Int, consumer: Processor<in PsiReference>): Boolean {
                    (element as? ArendNsId)?.defIdentifier?.let { dI ->
                        PsiSearchHelperImpl(project).processElementsWithWord({ element, _ ->
                            !(element is ArendRefIdentifier && element.reference?.isReferenceTo(elementToSearch) == true && !consumer.process(element.reference))
                        }, dI.useScope, dI.name, searchContext, true)
                    }
                    for (ref in PsiReferenceService.getService().getReferences(element, PsiReferenceService.Hints(elementToSearch, offsetInElement))) { // Copypasted from SingleTargetRequestResultProcessor
                        ProgressManager.checkCanceled()
                        if (ReferenceRange.containsOffsetInElement(ref, offsetInElement) && ref.isReferenceTo(elementToSearch)) {
                            if (!consumer.process(ref))
                                return false
                        }
                    }
                    return true
                }
            })
        }
    }
}

/**
 * Every returned file contains **all** of identifiers specified in [namesToSearch]
 */
fun collectSearchScopes(namesToSearch: List<String>, isSearchInLibraries: Boolean, project: Project): List<VirtualFile> =
    runReadAction {
        val fileBasedIndex = FileBasedIndex.getInstance()
        val fileSet = HashSet<VirtualFile>()
        fileBasedIndex.getFilesWithKey(
            IdIndex.NAME,
            namesToSearch.mapSmartSet { IdIndexEntry(it, true) },
            Processors.cancelableCollectProcessor(fileSet),
            ArendFileScope(project)
        )
        if (isSearchInLibraries) {
            project.service<ArendServerService>().preludeIfInitialized?.let {
                fileSet.add(it.virtualFile)
            }
        }
        fileSet.toList()
    }