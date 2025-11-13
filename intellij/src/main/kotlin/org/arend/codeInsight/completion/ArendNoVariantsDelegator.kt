package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.descendantsOfType
import com.intellij.util.Consumer
import org.arend.ArendIcons.AREND
import org.arend.ArendIcons.DIRECTORY
import org.arend.error.DummyErrorReporter
import org.arend.ext.reference.DataContainer
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.naming.scope.ScopeFactory.isGlobalScopeVisible
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.psi.stubs.index.ArendFileIndex
import org.arend.psi.stubs.index.ArendGotoClassIndex
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.RenameReferenceAction
import org.arend.refactoring.isVisible
import org.arend.repl.CommandHandler
import org.arend.repl.Repl.getInScopeElements
import org.arend.repl.Repl.replModuleLocation
import org.arend.repl.action.CdCommand.PARENT_DIR
import org.arend.repl.action.ListModulesCommand.ALL_MODULES
import org.arend.repl.action.LoadLibraryCommand.CUR_DIR
import org.arend.resolving.ArendReferenceBase.Companion.createArendLookUpElement
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.group.AccessModifier
import org.arend.toolWindow.repl.ArendReplService
import org.arend.util.FileUtils
import org.arend.util.FileUtils.EXTENSION

class ArendNoVariantsDelegator : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val tracker = object : Consumer<CompletionResult> {
            val variants = HashSet<PsiElement>()
            val nullPsiVariants = HashSet<String>()
            override fun consume(plainResult: CompletionResult) {
                result.passResult(plainResult)
                val elementPsi: PsiElement? = plainResult.lookupElement.psiElement
                val str = plainResult.lookupElement.lookupString
                if (elementPsi != null) {
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
        val isReplFile = file.isRepl
        val refElementAtCaret = file.findElementAt(parameters.offset - 1)?.parent
        val parentPsi = refElementAtCaret?.parent
        val anchorReferable = refElementAtCaret?.ancestor<ReferableBase<*>>()?.tcReferable

        val isInsideValidExpr = refElementAtCaret is ArendRefIdentifier && parentPsi is ArendLiteral &&
                refElementAtCaret.prevSibling == null && isGlobalScopeVisible(refElementAtCaret.topmostEquivalentSourceNode)
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
        if (project != null && (isInsideValidExpr || isInsideValidNsCmd || isClassExtension || (isReplFile && CommandHandler.INSTANCE.commandMap.contains(getReplCommand(file)))) && noVariants) {
            val repl = project.service<ArendReplService>().getRepl()
            val server = repl?.getServer()
            val replScopeElements = server?.let { getInScopeElements(server, server.getRawGroup(replModuleLocation)?.statements ?: emptyList())
                .mapNotNull { (it as? LocatedReferableImpl)?.data as? PsiLocatedReferable } } ?: emptyList()
            if (isReplFile && isLoadUnloadOrImportReplCommand(file)) {
                val modulePath = (file.childOfType<ArendReplLine>()?.expr?.text ?: getModulePathFromImport(file))
                    ?.run {
                        if (endsWith('.')) this + "Foo" else this
                    } ?: "Foo"
                val replLineFile = ArendPsiFactory(project, replModuleLocation.libraryName).createFromText("\\import $modulePath")
                val replLineGroup = replLineFile?.let { ConcreteBuilder.convertGroup(it, it.moduleLocation, DummyErrorReporter.INSTANCE) }
                val ref = replLineFile?.descendantsOfType<ArendRefIdentifier>()?.lastOrNull()?.apply {
                    (containingFile as ArendFile?)?.generatedModuleLocation = replModuleLocation
                }
                val modulePaths = repl?.loadedModules ?: emptyList()
                val modules = mutableSetOf<ModulePath>()
                for (modulePath in modulePaths) {
                    if (modulePath == Prelude.MODULE_PATH || modulePath == replModuleLocation.modulePath) {
                        continue
                    }
                    var rootPath = ModulePath()
                    for (path in modulePath.toList()) {
                        rootPath = ModulePath(rootPath.toList() + path)
                        modules.add(rootPath)
                    }
                }

                if ((isLoadReplCommand(file) || isUnloadReplCommand(file)) && !modulePath.contains('.')) {
                    val lookupElement = LookupElementBuilder.create(ALL_MODULES)
                    result.addElement(lookupElement)
                }

                val isImportOrUnload = isImportOrUnloadReplCommand(file)
                ref?.let { server?.getCompletionVariants(replLineGroup, it) }?.forEach {
                    origElement -> run {
                        if (origElement.modulePath == Prelude.MODULE_PATH || origElement.modulePath == replModuleLocation.modulePath) {
                            return@run
                        }
                        if (isImportOrUnload && !modules.contains(origElement.modulePath)) {
                            return@run
                        }
                        createArendLookUpElement(origElement, origElement.abstractReferable, file, false, null, false)?.let {
                            result.addElement(it)
                        }
                    }
                }
            } else if (isReplFile && isModuleCommand(file)) {
                val lookupElement = LookupElementBuilder.create(ALL_MODULES)
                result.addElement(lookupElement)
            } else if (isReplFile && isCdOrLibCommand(file)) {
                val currentDir = repl?.pwd?.toFile()
                val files = currentDir?.listFiles()?.toList() ?: emptyList()
                result.addElement(
                    if (currentDir?.resolve(FileUtils.LIBRARY_CONFIG_FILE)?.exists() == true) {
                        LookupElementBuilder.create(CUR_DIR).withIcon(AREND)
                    } else {
                        LookupElementBuilder.create(CUR_DIR).withIcon(DIRECTORY)
                    }
                )
                result.addElement(LookupElementBuilder.create(PARENT_DIR).withIcon(DIRECTORY))
                for (curFile in files) {
                    if (curFile.isDirectory && !curFile.isHidden) {
                        val lookupElement = if (curFile.resolve(FileUtils.LIBRARY_CONFIG_FILE).exists()) {
                            LookupElementBuilder.create(curFile.name).withIcon(AREND)
                        } else {
                            LookupElementBuilder.create(curFile.name).withIcon(DIRECTORY)
                        }
                        result.addElement(lookupElement)
                    }
                }
            } else if (isReplFile && isUnlibCommand(file)) {
                val libraries = repl?.libraries ?: mutableListOf()
                for (library in libraries) {
                    if (library == Prelude.LIBRARY_NAME || library == replModuleLocation.libraryName) continue
                    val lookupElement = LookupElementBuilder.create(library).withIcon(AREND)
                    result.addElement(lookupElement)
                }
            } else {
                val consumer = { name: String, refs: List<PsiLocatedReferable>? ->
                    if (bpm.prefixMatches(name)) {
                        val locatedReferables = refs?.filter { it is ArendFile || !isInsideValidNsCmd } ?: when {
                            isInsideValidExpr || isClassExtension ->
                                StubIndex.getElements(if (isClassExtension) ArendGotoClassIndex.KEY else ArendDefinitionIndex.KEY, name, project,  ArendFileScope(project), PsiReferable::class.java).filterIsInstance<PsiLocatedReferable>()
                            else ->
                                StubIndex.getElements(ArendFileIndex.KEY, "$name.ard", project,  ArendFileScope(project), ArendFile::class.java)
                        }
                        val replReferables = replScopeElements.filter { it.name?.equals(name) == true }
                        (if (isReplFile) replReferables else locatedReferables).filter { it.isValid }.forEach {
                            val isInsideTest = (it.containingFile as? ArendFile)?.moduleLocation?.locationKind == ModuleLocation.LocationKind.TEST
                            val isImportAllowed = it.accessModifier != AccessModifier.PRIVATE && isVisible(it.containingFile as ArendFile, file)
                            if (isReplFile || (!tracker.variants.contains(it) && !tracker.nullPsiVariants.contains(it.refName) && (isTestFile || !isInsideTest) && isImportAllowed))
                                createArendLookUpElement(null, it, parameters.originalFile, true, null, it !is ArendDefClass || !it.isRecord)?.let {
                                    result.addElement(
                                        run { val oldHandler = it.insertHandler
                                            it.withInsertHandler { context, item ->
                                                oldHandler?.handleInsert(context, item)
                                                val refIdentifier = context.file.findElementAt(context.tailOffset - 1)?.parent
                                                val locatedReferable = item.`object`
                                                if (refIdentifier is ArendReferenceElement && locatedReferable is PsiLocatedReferable && locatedReferable !is ArendFile) {
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
                val libraries = if (isReplFile) {
                    server?.libraries?.mapNotNull { server.getLibrary(it) } ?: emptyList()
                } else {
                    project.service<ArendServerService>().getLibraries(file.libraryName, withSelf = true, withPrelude = true)
                }
                for (library in libraries) {
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

                StubIndex.getInstance().processAllKeys(if (isInsideValidNsCmd) ArendFileIndex.KEY else ArendDefinitionIndex.KEY, project) { name ->
                    val withoutSuffix = name.removeSuffix(EXTENSION)
                    consumer.invoke(withoutSuffix, null)
                    true // If only a limited number (say N) of variants is needed, return false after N added lookUpElements
                }
            }
        } else {
           result.restartCompletionOnAnyPrefixChange()
        }

        super.fillCompletionVariants(parameters, result)
    }

    private fun getModulePathFromImport(file: ArendFile): String? {
        val statCmd = file.childOfType<ArendStatCmd>() ?: return null
        return statCmd.longName?.text?.let { it + if (statCmd.text?.endsWith(".") == true) "." else "" }
    }

    companion object {
        fun getReplCommand(replFile: ArendFile): @NlsSafe String? {
            return replFile.childOfType<ArendReplLine>()?.replCommand?.text?.drop(1) ?: if (replFile.text.trim().startsWith("\\import")) "import" else null
        }

        private fun isUnloadReplCommand(replFile: ArendFile): Boolean {
            val replCommand = getReplCommand(replFile)
            return replCommand?.equals("unload") == true
        }

        private fun isImportOrUnloadReplCommand(replFile: ArendFile): Boolean {
            val replCommand = getReplCommand(replFile)
            return replCommand?.equals("import") == true || isUnloadReplCommand(replFile)
        }

        private fun isLoadReplCommand(replFile: ArendFile): Boolean {
            val replCommand = getReplCommand(replFile)
            return replCommand?.equals("load") == true
        }

        private fun isLoadUnloadOrImportReplCommand(replFile: ArendFile): Boolean {
            return isLoadReplCommand(replFile) || isImportOrUnloadReplCommand(replFile)
        }

        private fun isModuleCommand(replFile: ArendFile): Boolean {
            val replCommand = getReplCommand(replFile)
            return replCommand?.equals("modules") == true
        }

        private fun isCdOrLibCommand(replFile: ArendFile): Boolean {
            val replCommand = getReplCommand(replFile)
            return replCommand?.equals("cd") == true || replCommand?.equals("lib") == true
        }

        private fun isUnlibCommand(replFile: ArendFile): Boolean {
            val replCommand = getReplCommand(replFile)
            return replCommand?.equals("unlib") == true
        }
    }
}