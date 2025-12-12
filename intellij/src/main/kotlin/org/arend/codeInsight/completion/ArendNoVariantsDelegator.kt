package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.util.Consumer
import org.arend.ext.reference.DataContainer
import org.arend.ext.module.ModuleLocation
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.fragments.ArendExpressionCodeFragment
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.psi.stubs.index.ArendFileIndex
import org.arend.psi.stubs.index.ArendGotoClassIndex
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.RenameReferenceAction
import org.arend.refactoring.isVisible
import org.arend.resolving.ArendReferenceBase
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.group.AccessModifier

class ArendNoVariantsDelegator : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val originalProject = parameters.originalFile.project
        if (originalProject.isDisposed) {
            return
        }
        val tracker = object : Consumer<CompletionResult> {
            val variants = HashSet<PsiElement>()
            val nullPsiVariants = HashSet<String>()
            override fun consume(plainResult: CompletionResult) {
                result.passResult(plainResult)
                val elementPsi: PsiElement? = plainResult.lookupElement.psiElement
                val str = plainResult.lookupElement.lookupString
                if (elementPsi != null) {
                    if (!elementPsi.isValid || elementPsi.project.isDisposed) return
                    val elementIsWithinFileBeingEdited = elementPsi.containingFile == parameters.position.containingFile
                    if (!elementIsWithinFileBeingEdited) variants.add(elementPsi) else {
                        val originalPosition = parameters.position
                        if (!originalPosition.ancestors.contains(elementPsi))
                            variants.add(elementPsi)
                    }
                } else {
                    nullPsiVariants.add(str)
                }
            }
        }
        result.runRemainingContributors(parameters, tracker)
        val file = parameters.originalFile as? ArendFile ?: return
        val isTestFile = file.moduleLocation?.locationKind == ModuleLocation.LocationKind.TEST
        val refElementAtCaret = file.findElementAt(parameters.offset - 1)?.parent
        val parentPsi = refElementAtCaret?.parent
        val anchorReferable = refElementAtCaret?.ancestor<ReferableBase<*>>()?.tcReferable

        val isInsideValidExpr = refElementAtCaret is ArendRefIdentifier && parentPsi is ArendLiteral &&
                refElementAtCaret.prevSibling == null && RenameReferenceAction.isRenameSupported(refElementAtCaret)
        val isInsideValidNsCmd = refElementAtCaret is ArendRefIdentifier && parentPsi is ArendLongName &&
                refElementAtCaret.prevSibling == null && refElementAtCaret.topmostEquivalentSourceNode.parent is Abstract.NamespaceCommand
        val isClassExtension = parentPsi is ArendLongName && parentPsi.parent is ArendSuperClass

        val editor = parameters.editor
        val project = editor.project
        
        val bpm = object: BetterPrefixMatcher(result.prefixMatcher, Int.MIN_VALUE) {
            override fun prefixMatchesEx(name: String?): MatchingOutcome {
                if (name?.startsWith(myPrefix) == true) return MatchingOutcome.BETTER_MATCH
                return super.prefixMatchesEx(name)
            }
        }

        val noVariants = tracker.variants.isEmpty() && tracker.nullPsiVariants.isEmpty() || !parameters.isAutoPopup
        val isReplFile = file.isRepl
        if (!isReplFile && project != null && !project.isDisposed && (isInsideValidExpr || isInsideValidNsCmd || isClassExtension) && noVariants) {
            val consumer = { name: String, refs: List<PsiLocatedReferable>? ->
                if (bpm.prefixMatches(name)) {
                    val locatedReferables = refs?.filter { it is ArendFile || !isInsideValidNsCmd } ?: when {
                        isInsideValidExpr || isClassExtension ->
                            StubIndex.getElements(if (isClassExtension) ArendGotoClassIndex.KEY else ArendDefinitionIndex.KEY, name, project, ArendFileScope(project), PsiReferable::class.java).filterIsInstance<PsiLocatedReferable>()
                        else ->
                            StubIndex.getElements(ArendFileIndex.KEY, "$name.ard", project, ArendFileScope(project), ArendFile::class.java)
                    }
                    locatedReferables.filter { it.isValid && !it.project.isDisposed }.forEach {
                        val isInsideTest = (it.containingFile as? ArendFile)?.moduleLocation?.locationKind == ModuleLocation.LocationKind.TEST
                        val isImportAllowed = it.accessModifier != AccessModifier.PRIVATE && isVisible(it.containingFile as ArendFile, file)
                        if (!tracker.variants.contains(it) && !tracker.nullPsiVariants.contains(it.refName) && (isTestFile || !isInsideTest) && isImportAllowed)
                            ArendReferenceBase.createArendLookUpElement(null, it, parameters.originalFile, true, null, it !is ArendDefClass || !it.isRecord)?.let {
                                result.addElement(
                                    run { val oldHandler = it.insertHandler
                                        it.withInsertHandler { context, item ->
                                            if (context.project.isDisposed) return@withInsertHandler
                                            oldHandler?.handleInsert(context, item)
                                            val refIdentifier = context.file.findElementAt(context.tailOffset - 1)?.parent
                                            val locatedReferable = item.`object`
                                            if (refIdentifier is ArendReferenceElement && locatedReferable is PsiLocatedReferable && locatedReferable !is ArendFile) {
                                                if (!refIdentifier.isValid || refIdentifier.project.isDisposed || !locatedReferable.isValid || locatedReferable.project.isDisposed) return@withInsertHandler
                                                val file = refIdentifier.containingFile
                                                if (file is ArendExpressionCodeFragment) {
                                                    val targetReferable = (locatedReferable as? ReferableBase<*>)?.tcReferable
                                                    if (targetReferable != null && anchorReferable != null) {
                                                        val longName = file.getMultiResolver()?.calculateLongName(targetReferable, org.arend.server.RawAnchor(anchorReferable, refElementAtCaret))
                                                        if (longName != null) RenameReferenceAction(refIdentifier, longName.toList(), locatedReferable).execute(editor)
                                                    }
                                                } else {
                                                    val fix = ResolveReferenceAction.getProposedFix(locatedReferable, refIdentifier)
                                                    fix?.execute(editor)
                                                }
                                            } else if (locatedReferable is ArendFile) {
                                                if (context.project.isDisposed) return@withInsertHandler
                                                context.document.replaceString(context.startOffset, context.selectionEndOffset, locatedReferable.fullName)
                                                context.commitDocument()
                                            }
                                        }
                                    }
                                )
                            }
                    }
                }
            }

            val generatedMap = HashMap<String, ArrayList<PsiLocatedReferable>>()
            for (library in project.service<ArendServerService>().getLibraries(file.libraryName, withSelf = true, withPrelude = true)) {
                for (entry in library.generatedNames.entries) {
                    val psiRef = (entry.value as? DataContainer)?.data
                    if (psiRef is PsiLocatedReferable) {
                        generatedMap.computeIfAbsent(entry.key) { ArrayList() }.add(psiRef)
                    }
                }
            }
            for (entry in generatedMap.entries) {
                consumer.invoke(entry.key, entry.value)
            }

            // Collect keys first to avoid nested stub index operations which can lead to deadlocks
            val indexKey = if (isInsideValidNsCmd) ArendFileIndex.KEY else ArendDefinitionIndex.KEY
            val allKeys = ArrayList<String>()
            if (project.isDisposed) return
            StubIndex.getInstance().processAllKeys(indexKey, project) { name ->
                allKeys.add(name)
                true // If only a limited number (say N) of variants is needed, return false after N added lookUpElements
            }
            for (name in allKeys) {
                val withoutSuffix = name.removeSuffix(".ard")
                consumer.invoke(withoutSuffix, null)
            }
        } else {
            result.restartCompletionOnAnyPrefixChange()
        }

        super.fillCompletionVariants(parameters, result)
    }
}