package org.arend.refactoring.move

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.*
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMemberViewDescriptor
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.containers.MultiMap
import com.intellij.util.containers.SortedList
import org.arend.codeInsight.*
import org.arend.codeInsight.ArendCodeInsightUtils.Companion.getExternalParameters
import org.arend.ext.module.LongName
import org.arend.ext.module.ModuleLocation
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.scope.DynamicScope
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.*
import org.arend.refactoring.changeSignature.*
import org.arend.refactoring.changeSignature.ArendChangeSignatureProcessor.Companion.getUsagesPreprocessor
import org.arend.server.ArendServer
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.server.impl.MultiFileReferenceResolver
import org.arend.term.abs.Abstract.ParametersHolder
import org.arend.term.concrete.Concrete
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.util.exprToConcrete1
import org.jetbrains.kotlin.psi.psiUtil.parameterIndex
import java.util.Collections.singletonList

class ArendMoveRefactoringProcessor(project: Project,
                                    private val multiFileReferenceResolver: MultiFileReferenceResolver,
                                    private val myMoveCallback: () -> Unit,
                                    private var myMembers: List<ArendGroup>,
                                    private val mySourceContainer: ArendGroup,
                                    private val myTargetContainer: ArendGroup,
                                    private val insertIntoDynamicPart: Boolean,
                                    private val myOpenInEditor: Boolean,
                                    private val myOptimizeImportsAfterMove: Boolean = true) : BaseRefactoringProcessor(project, myMoveCallback) {
    private val myReferableDescriptors = HashMap<PsiLocatedReferable, LocationDescriptor>()
    private val myMovedReferables = ArrayList<PsiLocatedReferable>()
    private val myServer = myProject.service<ArendServerService>().server

    override fun findUsages(): Array<UsageInfo> {
        val usagesList = ArrayList<UsageInfo>()
        val statCmdsToFix = HashMap<ArendStatCmd, PsiReference>()
        val containers = HashSet<ArendGroup>()
        val containerUsages = MultiMap<ArendGroup, ArendGroup>()

        myReferableDescriptors.clear()
        myMovedReferables.clear()

        for (psiReference in ReferencesSearch.search(mySourceContainer)) {
            val statCmd = isStatCmdUsage(psiReference, true)
            if (statCmd is ArendStatCmd) {
                statCmdsToFix[statCmd] = psiReference
                statCmd.ancestor<ArendGroup>()?.let{ containers.add(it) }
            }
        }

        for ((index, member) in myMembers.withIndex())
            for ((psi, descriptor) in collectInternalReferablesWithSelf(member, index)) {
                myReferableDescriptors[psi] = descriptor

                for (psiReference in ReferencesSearch.search(psi)) {
                    val referenceElement = psiReference.element
                    val referenceParent = referenceElement.parent
                    val cmdContainer = referenceElement.ancestors.filterIsInstance<ArendGroup>().toList().reversed().firstOrNull { containers.contains(it) }
                    if (cmdContainer != null) containerUsages.putValue(cmdContainer, member)

                    if (!isInMovedMember(psiReference.element)) {
                        val statCmd = isStatCmdUsage(psiReference, false)
                        val isUsageInHiding = referenceElement is ArendRefIdentifier && referenceParent is ArendScId
                        if (statCmd == null || isUsageInHiding || !statCmdsToFix.contains(statCmd))
                            usagesList.add(ArendUsageLocationInfo(psiReference, descriptor))
                    }
                }
            }

        for (statCmd in statCmdsToFix) {
            val statCmdContainer = statCmd.key.ancestor<ArendGroup>()
            val usedMembers = statCmdContainer?.let { containerUsages.get(it) }
            if ((usedMembers?.size ?: 0) > 0) {
                usagesList.add(ArendStatCmdUsageInfo(statCmd.key, statCmd.value))
            }
        }

        var usageInfos = usagesList.toTypedArray()
        usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos)
        return usageInfos
    }

    private fun collectInternalReferablesWithSelf(element: ArendGroup, index: Int): ArrayList<Pair<PsiLocatedReferable, LocationDescriptor>> {
        val result = ArrayList<Pair<PsiLocatedReferable, LocationDescriptor>>()
        result.add(Pair(element, LocationDescriptor(index, emptyList())))
        for (internalReferable in element.internalReferables)
            if (internalReferable.isVisible) {
                val path = ArrayList<Int>()
                var psi: PsiElement = internalReferable
                while (psi.parent != null && psi != element) {
                    val i = psi.parent.children.indexOf(psi)
                    path.add(0, i)
                    psi = psi.parent
                }
                result.add(Pair(internalReferable, LocationDescriptor(index, path)))
            }

        return result
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        var insertAnchor: PsiElement?
        val psiFactory = ArendPsiFactory(myProject)

        val sourceFile = mySourceContainer as? ArendFile ?: (mySourceContainer.containingFile as? ArendFile ?: return)
        val moduleLocationsToInvalidate = ArrayList<ModuleLocation>();
        sourceFile.moduleLocation?.let { moduleLocationsToInvalidate.add(it) }

        insertAnchor = if (myTargetContainer is ArendFile) {
            myTargetContainer.lastChild //null means that the target file is empty
        } else if (myTargetContainer is ArendDefClass && insertIntoDynamicPart) {
            if (myTargetContainer.lbrace == null && myTargetContainer.rbrace == null) surroundWithBraces(psiFactory, myTargetContainer)
            myTargetContainer.classStatList.lastOrNull() ?: myTargetContainer.lbrace!!
        } else {
            getAnchorInAssociatedModule(psiFactory, myTargetContainer)
        }

        //Perform ChangeSignatureRefactoring
        val thisVarNameMap = HashMap<PsiLocatedReferable, String>()
        run {
            val filesToProcess = HashSet<PsiFile>()
            for (usage in usages) usage.reference?.element?.containingFile?.let { filesToProcess.add(it) }
            filesToProcess.add(myTargetContainer.containingFile)

            for (member in myMembers) filesToProcess.add(member.containingFile)
            val documentManager = PsiDocumentManager.getInstance(myProject)
            for (file in filesToProcess) {
                if (file is ArendFile) file.moduleLocation?.let { moduleLocationsToInvalidate.add(it) }
                documentManager.getDocument(file)?.let { documentManager.doPostponedOperationsAndUnblockDocument(it) }
            }

            val (descriptors, changeSignatureUsages) = getUsagesToPreprocess(myServer, myMembers, insertIntoDynamicPart, myTargetContainer, thisVarNameMap, null, multiFileReferenceResolver)
            val fileChangeMap = LinkedHashMap<PsiFile, SortedList<Pair<TextRange, String>>>()
            val usagesPreprocessor = getUsagesPreprocessor(changeSignatureUsages, myProject, fileChangeMap,
                HashSet(), HashSet(), multiFileReferenceResolver) //TODO: properly initialize these parameters

            usagesPreprocessor.run()

            writeFileChangeMap(myProject, fileChangeMap)

            for (descriptor in descriptors)
                descriptor.fixEliminator()

            for (descriptor in descriptors) {
                val parameterInfo = descriptor.toParametersInfo()
                if (parameterInfo != null) {
                    val definition = descriptor.getAffectedDefinition()
                    val definitionName = definition?.name
                    if (definitionName != null && definition is PsiLocatedReferable && definition !is ArendConstructor) {
                        val changeInfo = ArendChangeInfo(parameterInfo, null, definitionName, definition, multiFileReferenceResolver)
                        changeInfo.modifySignature()
                    }
                }
            }
        }

        val updatedUsages = findUsages()

        //Replace \this literals with local `this` parameters
        for ((referable, thisVarName) in thisVarNameMap) {
            fun doSubstituteThisKwWithThisVar(psi: PsiElement, stopAtPsiLocatedReferable: Boolean = false) {
                if (psi is ArendWhere || psi is PsiLocatedReferable && stopAtPsiLocatedReferable) return
                if (psi is ArendAtom && psi.thisKw != null) {
                    val literal = psiFactory.createExpression(thisVarName).descendantOfType<ArendAtom>()!!
                    psi.replace(literal)
                } else for (c in psi.children) doSubstituteThisKwWithThisVar(c, true)
            }
            doSubstituteThisKwWithThisVar(referable)
        }

        //Resolve references in the elements that are to be moved. This is needed to distinguish between long names and field accesses
        sourceFile.moduleLocation?.let{
            myServer.getCheckerFor(singletonList(it)).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
        }

        //Memorize references in myMembers being moved
        val descriptorsOfAllMembersBeingMoved = HashMap<PsiLocatedReferable, LocationDescriptor>() //This set may be strictly larger than the set of myReferableDescriptors
        val bodiesRefsFixData = HashMap<LocationDescriptor, TargetReference>()
        val memberReferences = HashSet<ArendReferenceElement>()

        run {
            val usagesInMovedBodies = HashMap<PsiLocatedReferable, MutableSet<LocationDescriptor>>()
            val targetReferences = HashMap<PsiLocatedReferable, TargetReference>()

            for ((mIndex, m) in myMembers.withIndex())
                collectUsagesAndMembers(emptyList(), m, mIndex, usagesInMovedBodies, descriptorsOfAllMembersBeingMoved, memberReferences)

            for (referable in usagesInMovedBodies.keys)
                targetReferences[referable] = descriptorsOfAllMembersBeingMoved[referable]?.let { DescriptorTargetReference(it) }
                        ?: ReferableTargetReference(referable)

            for (usagePack in usagesInMovedBodies) for (usage in usagePack.value) {
                val referable = usagePack.key
                targetReferences[referable]?.let { bodiesRefsFixData[usage] = it }
            }
        }

        //Determine which moved members should be added to the "remainder" namespace command
        val remainderReferables = HashSet<LocationDescriptor>()
        for (usage in updatedUsages) if (usage is ArendUsageLocationInfo) {
            val referenceElement = usage.reference?.element

            if (referenceElement is ArendReferenceElement &&
                    mySourceContainer.textRange.contains(referenceElement.textOffset) && !memberReferences.contains(referenceElement)) { // Normal usage inside source container
                val member = referenceElement.reference?.resolve()
                val descriptor = (member as? PsiLocatedReferable)?.let{ descriptorsOfAllMembersBeingMoved[it] }
                if (descriptor != null) remainderReferables.add(descriptor)
                if (member is ArendConstructor) {
                    val containingDataType = member.parentOfType<ArendDefData>()
                    val dataTypeDescriptor = containingDataType?.let { descriptorsOfAllMembersBeingMoved[it] }
                    if (dataTypeDescriptor != null) remainderReferables.add(dataTypeDescriptor)
                }
            }
        }

        //Calculate original signatures of members being moved
        val movedReferablesMap = LinkedHashMap<LocationDescriptor, PsiLocatedReferable>()

        //Do move myMembers
        val holes = ArrayList<RelativePosition>()
        val newMemberList = ArrayList<ArendGroup>()

        for (member in myMembers) {
            val mStatementOrClassStat = member.parent
            val doc = (member as? ArendDefinition<*>)?.documentation
            val memberIsInDynamicPart = isInDynamicPart(mStatementOrClassStat) != null
            val docCopy = doc?.copy()

            val copyOfMemberStatement: PsiElement =
                    if (myTargetContainer is ArendDefClass && insertIntoDynamicPart) {
                        val mCopyClassStat = if (mStatementOrClassStat is ArendClassStat) mStatementOrClassStat.copy() else {
                            val classStatContainer = psiFactory.createClassStat()
                            classStatContainer.definition!!.replace(member.copy())
                            classStatContainer
                        }

                        insertAnchor!!.parent!!.addAfter(mCopyClassStat, insertAnchor)
                    } else {
                        val mCopyStatement = if (mStatementOrClassStat is ArendClassStat) {
                            val statementContainer = psiFactory.createFromText("\\func foo => {?}")?.descendantOfType<ArendStat>()
                            statementContainer!!.group!!.replace(member.copy())
                            statementContainer
                        } else mStatementOrClassStat.copy()

                        insertAnchor?.parent?.addAfter(mCopyStatement, insertAnchor)
                                ?: myTargetContainer.add(mCopyStatement)
                    }

            if (docCopy != null) {
                copyOfMemberStatement.parent?.addBefore(docCopy, copyOfMemberStatement)
            }

            val mCopy = copyOfMemberStatement.descendantOfType<ArendGroup>()!!
            newMemberList.add(mCopy)

            (doc?.prevSibling as? PsiWhiteSpace)?.delete()
            (mStatementOrClassStat.prevSibling as? PsiWhiteSpace)?.delete()
            mStatementOrClassStat.deleteAndGetPosition()?.let { if (!memberIsInDynamicPart) holes.add(it) }

            doc?.delete()
            insertAnchor = copyOfMemberStatement
        }

        var sourceContainerWhereBlockFreshlyCreated = false
        if (holes.isEmpty()) {
            sourceContainerWhereBlockFreshlyCreated = mySourceContainer.where == null
            val anchor = getAnchorInAssociatedModule(psiFactory, mySourceContainer)
            if (anchor != null) holes.add(RelativePosition(PositionKind.AFTER_ANCHOR, anchor))
        }

        myMembers = newMemberList

        //Create map from descriptors to actual psi elements of myMembers
        for (descriptor in descriptorsOfAllMembersBeingMoved.values) (locateChild(descriptor) as? PsiLocatedReferable)?.let {
            movedReferablesMap[descriptor] = it
        }

        // reset server for the 1st time
        resetServer(moduleLocationsToInvalidate)

        myMovedReferables.addAll(myReferableDescriptors.values.mapNotNull { movedReferablesMap[it] })
        val movedReferablesNamesSet = myMovedReferables.mapNotNull { it.name }.toSet()
        run {
            val referablesWithUsagesInSourceContainer = movedReferablesMap.values.intersect(remainderReferables.map { movedReferablesMap[it] }.toSet()).
                union(movedReferablesMap.values.filterIsInstance<ArendDefInstance>()).mapNotNull { it?.name }.toList()
            val movedReferablesUniqueNames = movedReferablesNamesSet.filter { name -> referablesWithUsagesInSourceContainer.filter { it == name }.size == 1 }
            val referablesWithUniqueNames = HashMap<String, PsiLocatedReferable>()
            for (entry in movedReferablesMap) {
                val name = entry.value.name
                if (name != null && movedReferablesUniqueNames.contains(name)) referablesWithUniqueNames[name] = entry.value
            }

            //Prepare the "remainder" namespace command (the one that is inserted in the position where one of the moved definitions has been placed)
            if (holes.isNotEmpty()) {
                val uppermostHole = holes.asSequence().sorted().first()
                var remainderAnchor: PsiElement? = uppermostHole.anchor

                if (uppermostHole.kind != PositionKind.INSIDE_EMPTY_ANCHOR) {
                    val next = remainderAnchor?.rightSibling<ArendCompositeElement>()
                    val prev = remainderAnchor?.leftSibling<ArendCompositeElement>()
                    if (next != null) remainderAnchor = next else
                        if (prev != null) remainderAnchor = prev
                }

                while (remainderAnchor !is ArendCompositeElement && remainderAnchor != null) remainderAnchor = remainderAnchor.parent

                if (remainderAnchor is ArendCompositeElement) {
                    val openedName = ResolveReferenceAction.getTargetLongName(remainderAnchor, multiFileReferenceResolver, myTargetContainer)

                    if (openedName != null && movedReferablesUniqueNames.isNotEmpty()) {
                        val openedName: List<String> = openedName.toList()

                        val renamings = movedReferablesUniqueNames.map { Pair(it, null as String?) }.sortedBy { it.second ?: it.first } // filter this list
                        val groupMember = if (uppermostHole.kind == PositionKind.INSIDE_EMPTY_ANCHOR) {
                            if (remainderAnchor.children.isNotEmpty()) remainderAnchor.firstChild else null
                        } else remainderAnchor

                        val nsIds = addIdToUsing(groupMember, myTargetContainer, LongName(openedName).toString(), renamings, psiFactory, uppermostHole).first
                        for (nsId in nsIds) {
                            val target = nsId.refIdentifier.reference?.resolve()
                            val name = nsId.refIdentifier.referenceName
                            if (target != referablesWithUniqueNames[name]) /* the reference that we added to the namespace command is corrupt, so we need to remove it right after it was added */
                                doRemoveRefFromStatCmd(nsId.refIdentifier)
                        }
                    }
                }
            }

            val where = mySourceContainer.where
            if (where != null && where.statList.isEmpty() && (sourceContainerWhereBlockFreshlyCreated || where.lbrace == null))  where.delete()
        }

        resetServer(moduleLocationsToInvalidate)

        //Fix usages of namespace commands
        for (usage in updatedUsages) if (usage is ArendStatCmdUsageInfo) {
            val statCmd = usage.command
            val statCmdStatement = statCmd.parent
            val renamings = ArrayList<Pair<String, String?>>()
            val nsIdToRemove = HashSet<ArendNsId>()

            for (memberName in movedReferablesNamesSet) {
                val importedName = getImportedNames(statCmd, memberName)

                for (name in importedName) {
                    val newName = if (name.first == memberName) null else name.first
                    renamings.add(Pair(memberName, newName))
                    val nsId = name.second
                    if (nsId != null) nsIdToRemove.add(nsId)
                }
            }

            val currentName: List<String>? = ResolveReferenceAction.getTargetLongName(statCmd, multiFileReferenceResolver, myTargetContainer )?.toList() //importData?.first?.split(".")

            if (renamings.isNotEmpty() && currentName != null) {
                val name = when {
                    currentName.isNotEmpty() -> LongName(currentName).toString()
                    myTargetContainer is ArendFile -> myTargetContainer.moduleLocation?.modulePath?.lastName ?: ""
                    else -> ""
                }

                if (myTargetContainer !is ArendFile && !myTargetContainer.textRange.contains(statCmdStatement.textRange))
                addIdToUsing(statCmdStatement, myTargetContainer, name, renamings, psiFactory, RelativePosition(PositionKind.AFTER_ANCHOR, statCmdStatement))
            }

            for (nsId in nsIdToRemove) doRemoveRefFromStatCmd(nsId.refIdentifier)
        }

        ResolveReferenceAction.flushNamespaceCommands(multiFileReferenceResolver);

        // Reset server for the 2nd time
        resetServer(moduleLocationsToInvalidate)

        //Prepare fix references of "normal" usages
        val sink = ArrayList<RenameReferenceAction>()
        for (usage in updatedUsages) if (usage is ArendUsageLocationInfo) {
            val referenceElement = usage.reference?.element
            val referenceParent = referenceElement?.parent

            if (referenceElement is ArendRefIdentifier && referenceParent is ArendScId && referenceElement.isValid) //Usage in the "hiding" list of a namespace which we simply delete
                doRemoveRefFromStatCmd(referenceElement)
            else if (referenceElement is ArendReferenceElement && referenceElement.isValid) {//Normal usage which we try to fix
                movedReferablesMap[usage.referableDescriptor]?.let {
                    ResolveReferenceAction.fixLongName(referenceElement, multiFileReferenceResolver, it, sink)
                }
            }
        }

        //Prepare fix references in the elements that have been moved
        for ((mIndex, m) in myMembers.withIndex()) restoreReferences(emptyList(), m, mIndex, bodiesRefsFixData, multiFileReferenceResolver, sink)

        //
        for (action in sink) action.execute(null)

        ResolveReferenceAction.flushNamespaceCommands(multiFileReferenceResolver)

        //Optimize imports
        if (myOptimizeImportsAfterMove) {
            val optimalStructure = getOptimalImportStructure(mySourceContainer)
            val (fileImports, optimalTree, _) = optimalStructure
            processRedundantImportedDefinitions(mySourceContainer, fileImports, optimalTree, importRemover)
        }

        //Invoke move callback
        myMoveCallback.invoke()

        //Execute open in editor action
        if (myOpenInEditor && myMembers.isNotEmpty()) {
            val item = myMembers.first()
            if (item.isValid) EditorHelper.openInEditor(item)
        }
    }

    private fun resetServer(modules: List<ModuleLocation>) {
        modules.forEach { module -> myServer.removeModule(module) }
        myServer.getCheckerFor(modules).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
    }

    private fun locateChild(element: PsiElement, childPath: List<Int>): PsiElement? {
        return if (childPath.isEmpty()) element else {
            val shorterPrefix = childPath.subList(1, childPath.size)
            val childElement = element.children[childPath[0]]
            locateChild(childElement, shorterPrefix)
        }
    }

    private fun locateChild(descriptor: LocationDescriptor): PsiElement? {
        val num = descriptor.groupNumber
        val group = if (num < myMembers.size) myMembers[num] else null
        return if (group != null) locateChild(group, descriptor.childPath) else null
    }

    private fun collectUsagesAndMembers(prefix: List<Int>, element: PsiElement, groupNumber: Int,
                                        usagesData: MutableMap<PsiLocatedReferable, MutableSet<LocationDescriptor>>,
                                        memberData: MutableMap<PsiLocatedReferable, LocationDescriptor>,
                                        memberReferences: MutableSet<ArendReferenceElement>) {
        when (element) {
            is ArendFieldDefIdentifier ->  {
                memberData[element] = LocationDescriptor(groupNumber, prefix)
                return
            }
            is ArendLongName, is ArendAtomFieldsAcc -> {
                val refList: List<Pair<ArendRefIdentifier, List<Int>>> = when (element) {
                    is ArendLongName -> element.refIdentifierList.withIndex().map { Pair(it.value, singletonList(it.index)) }
                    is ArendAtomFieldsAcc -> element.atom.literal?.refIdentifier?.let { atomRefId ->
                        val firstNull = element.fieldAccList.indexOfFirst { it.refIdentifier == null }
                        val subListBeforeNull = if (firstNull == -1) element.fieldAccList else element.fieldAccList.subList(0, firstNull)
                        singletonList(Pair(atomRefId, listOf(0, 0, 0))) +
                                subListBeforeNull.map { Pair(it.refIdentifier!!, listOf(element.children.indexOf(it), 0)) }
                    } ?: emptyList()
                    else -> emptyList()
                }

                val concrete = exprToConcrete1(element as ArendExpr).firstOrNull()
                if (concrete is Concrete.ReferenceExpression) {
                    val reference = refList.firstOrNull {
                        it.first.resolve() == concrete.referent
                    }
                    if (reference != null)
                        collectUsagesAndMembers(prefix + reference.second, reference.first,
                            groupNumber, usagesData, memberData, memberReferences)
                    return
                }
            }
            is ArendReferenceElement -> {
                if (!(element is ArendDefIdentifier && element.reference?.resolve() == null)) element.reference?.resolve()
                    .let {
                        if (it is PsiLocatedReferable) {
                            var set = usagesData[it]
                            if (set == null) {
                                set = HashSet()
                                usagesData[it] = set
                            }
                            set.add(LocationDescriptor(groupNumber, prefix))
                            memberReferences.add(element)
                        }
                    }
                return
            }
        }

        if (element is PsiLocatedReferable) memberData[element] = LocationDescriptor(groupNumber, prefix)
        element.children.mapIndexed { i, e -> collectUsagesAndMembers(prefix + singletonList(i), e, groupNumber, usagesData, memberData, memberReferences) }
    }

    private fun restoreReferences(prefix: List<Int>, element: PsiElement, groupIndex: Int,
                                  fixMap: HashMap<LocationDescriptor, TargetReference>,
                                  multiFileReferenceResolver: MultiFileReferenceResolver,
                                  sink: MutableList<RenameReferenceAction>) {
        if (element is ArendReferenceElement) {
            val descriptor = LocationDescriptor(groupIndex, prefix)
            val correctTarget = fixMap[descriptor]?.resolve()
            if (correctTarget != null && correctTarget !is ArendFile) {
                ResolveReferenceAction.fixLongName(element, multiFileReferenceResolver, correctTarget, sink)
            }
        } else element.children.mapIndexed { i, e -> restoreReferences(prefix + singletonList(i), e, groupIndex, fixMap, multiFileReferenceResolver, sink) }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()
        val usages = refUsages.get()

        if (mySourceContainer != myTargetContainer) {
            val localGroup = HashSet(myTargetContainer.statements.mapNotNull { it.group })
            localGroup.addAll(myTargetContainer.dynamicSubgroups)

            val localNamesMap = HashMap<String, ArendGroup>()
            for (psi in localGroup) {
                localNamesMap[psi.refName] = psi
                val aliasName = psi.aliasName
                if (aliasName != null) localNamesMap[aliasName] = psi
            }

            for (member in myMembers) {
                val text = member.refName
                val psi = localNamesMap[text]
                if (psi != null) conflicts.put(psi, singletonList("Name clash with one of the members of the target module ($text)"))
            }
        }

        return showConflicts(conflicts, usages)
    }

    override fun getCommandName(): String = MoveMembersImpl.getRefactoringName()

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
            MoveMemberViewDescriptor(PsiUtilCore.toPsiElementArray(myMembers.map { it }))

    private fun isInMovedMember(element: PsiElement): Boolean = myMembers.any { PsiTreeUtil.isAncestor(it, element, false) }

    private fun isStatCmdUsage(reference: PsiReference, insideLongNameOnly: Boolean): ArendStatCmd? {
        val parent = reference.element.parent
        if ((parent is ArendNsId || parent is ArendScId) && !insideLongNameOnly) return parent.ancestor<ArendStatCmd>()
        if (parent is ArendLongName) {
            val grandparent = parent.parent
            if (grandparent is ArendStatCmd) return grandparent
        }
        return null
    }

    data class LocationDescriptor(val groupNumber: Int, val childPath: List<Int>)

    class ArendUsageLocationInfo(reference: PsiReference, val referableDescriptor: LocationDescriptor) : UsageInfo(reference)

    class ArendStatCmdUsageInfo(val command: ArendStatCmd, reference: PsiReference) : UsageInfo(reference)

    private interface TargetReference {
        fun resolve(): PsiLocatedReferable?
    }

    private class ReferableTargetReference(private val myReferable: PsiLocatedReferable) : TargetReference {
        override fun resolve() = myReferable
    }

    private inner class DescriptorTargetReference(val myDescriptor: LocationDescriptor) : TargetReference {
        private var myCachedResult: PsiLocatedReferable? = null

        override fun resolve(): PsiLocatedReferable? {
            if (myCachedResult != null) return myCachedResult
            myCachedResult = locateChild(myDescriptor) as? PsiLocatedReferable
            return myCachedResult
        }
    }

    companion object {
        fun getUsagesToPreprocess(server: ArendServer,
                                  myMembers: List<ArendGroup>,
                                  insertIntoDynamicPart: Boolean,
                                  myTargetContainer: ArendGroup,
                                  thisVarNameMap: MutableMap<PsiLocatedReferable, String>,
                                  unreliableReferables: MutableSet<PsiLocatedReferable>? = null,
                                  multiFileReferenceResolver: MultiFileReferenceResolver):
                Pair<ArrayList<ChangeSignatureRefactoringDescriptor>, List<ArendUsageInfo>> {
            val membersBeingMoved = LinkedHashSet<PsiLocatedReferable>()
            val descriptors = ArrayList<ChangeSignatureRefactoringDescriptor>()

            for (member in myMembers) membersBeingMoved.addAll(member.descendantsOfType<PsiLocatedReferable>())

            val usagesInMembersBeingMoved = ArrayList<ArendUsageInfo>()
            val membersClasses = HashMap<PsiLocatedReferable, ArendDefClass>()

            for (referable in membersBeingMoved.sortedBy { -it.textLength })
                if (referable is ParametersHolder && referable !is ArendClassField && referable !is ArendFieldDefIdentifier) {
                    val (oldSignature, isReliable) = ArendCodeInsightUtils.getParameterList(referable, false, null)
                    if (!isReliable) unreliableReferables?.add(referable)
                    if (oldSignature != null) {
                        val externalParameters = ArrayList<ParameterDescriptor>()
                        val internalParameters = ArrayList<ParameterDescriptor>()
                        for (p in oldSignature) if (p.isExternal()) externalParameters.add(p) else if (!p.isThis()) internalParameters.add(p)

                        val oldThisParameter = if (oldSignature.isNotEmpty() && oldSignature[0].isThis()) oldSignature[0] else null
                        val oldThisParameterClass = oldThisParameter?.getThisDefClass()

                        val newThisParameter = when {
                            oldThisParameterClass != null && membersBeingMoved.contains(oldThisParameterClass) -> DefaultParameterDescriptorFactory.createThisParameter(oldThisParameterClass)
                            myTargetContainer is ArendDefClass && insertIntoDynamicPart -> DefaultParameterDescriptorFactory.createThisParameter(myTargetContainer)
                            else -> ArendCodeInsightUtils.getThisParameter(myTargetContainer)
                        }
                        val newThisParameterClass = newThisParameter?.getThisDefClass()

                        val unmovedMembersThatRequireLocalSignatureModification = HashSet<PsiLocatedReferable>()
                        val thisPrefix = ArrayList<ParameterDescriptor>()
                        val internalizedPart = ArrayList<ParameterDescriptor>()

                        if (oldThisParameterClass != null) {
                            fun collectClassMembers(group: ArendGroup, sink: MutableSet<PsiLocatedReferable>) {
                                if (group is ArendDefClass) {
                                    ArendCodeInsightUtils.ensureIsTypechecked(group)
                                    val classReferable = group.tcReferable
                                    val scope = DynamicScope(
                                        server.typingInfo.getDynamicScopeProvider(classReferable),
                                        server.typingInfo,
                                        DynamicScope.Extent.WITH_DYNAMIC
                                    )
                                    for (element in scope.elements)
                                        if (element is TCDefReferable)
                                        (element.data as? PsiLocatedReferable)?.let { sink.add(it) }
                                }
                            }

                            val newSuperclasses = newThisParameterClass?.superClassList?.map {
                                it.longName.refIdentifierList.lastOrNull()?.reference?.resolve() as? ArendDefClass?
                            }?.toSet()
                            val oldSuperclasses = oldThisParameterClass.superClassList.map {
                                it.longName.refIdentifierList.lastOrNull()?.reference?.resolve() as? ArendDefClass?
                            }

                            when {
                                oldThisParameterClass == newThisParameterClass -> // Class unchanged
                                    thisPrefix.add(DefaultParameterDescriptorFactory.createThisParameter(oldThisParameter))
                                newSuperclasses?.contains(oldThisParameterClass) == true -> { // Move into descendant
                                    thisPrefix.add(DefaultParameterDescriptorFactory.createThisParameter(newThisParameterClass))
                                }
                                newThisParameterClass != null && oldSuperclasses.contains(newThisParameterClass) -> { // Move into ancestor
                                    thisPrefix.add(DefaultParameterDescriptorFactory.createThisParameter(oldThisParameter))
                                    collectClassMembers(oldThisParameterClass, unmovedMembersThatRequireLocalSignatureModification)
                                    val ancestorLevelParameters = HashSet<PsiLocatedReferable>()
                                    collectClassMembers(newThisParameterClass, ancestorLevelParameters)
                                    unmovedMembersThatRequireLocalSignatureModification.removeAll(ancestorLevelParameters)
                                }
                                else -> { // Move into an unrelated class or static context
                                    if (newThisParameterClass != null) thisPrefix.add(DefaultParameterDescriptorFactory.createThisParameter(newThisParameterClass))

                                    val freshName = StringRenamer().generateFreshName(VariableImpl("this"), getAllBindings(referable).map { VariableImpl(it) }.toList())
                                    thisVarNameMap[referable] = freshName

                                    val classNameGetter: () -> String = {
                                        ResolveReferenceAction.getTargetLongName(referable, multiFileReferenceResolver, oldThisParameterClass)?.toString() ?: ""

                                        /* getTargetName(oldThisParameterClass, referable)?.let { (targetName, namespaceCommand) ->
                                            namespaceCommand?.execute()
                                            targetName
                                        } ?: "" */
                                    }

                                    val internalizedThisDescriptor = if (referable is ArendConstructor) {
                                        DefaultParameterDescriptorFactory.createDataParameter(oldThisParameter, null, freshName, classNameGetter, null)
                                    } else {
                                        DefaultParameterDescriptorFactory.createNewParameter(false, oldThisParameter, null, freshName, classNameGetter)
                                    }
                                    internalizedPart.add(internalizedThisDescriptor)

                                    collectClassMembers(oldThisParameterClass, unmovedMembersThatRequireLocalSignatureModification)
                                }
                            }
                        }

                        val externalParametersPart = ArrayList<ParameterDescriptor>()
                        if (externalParameters.isNotEmpty()) for (p in externalParameters) {
                            val group = p.getExternalScope()!!
                            val groupChildren = group.descendantsOfType<PsiLocatedReferable>().toList()
                            for (child in groupChildren) if (!membersBeingMoved.contains(child)) {
                                val childExternalParameters = getExternalParameters(child)
                                if (childExternalParameters?.any { eP -> eP.getReferable() == p.getReferable()} == true)
                                    unmovedMembersThatRequireLocalSignatureModification.add(child)
                            }

                            val included = myTargetContainer.ancestors.filterIsInstance<ArendGroup>().contains(p.getExternalScope())
                            val newParameter = DefaultParameterDescriptorFactory.createNewParameter(p.isExplicit, p, if (included) p.getExternalScope() else null, null, p.typeGetter)
                            if (included) {
                                externalParametersPart.add(newParameter)
                            } else
                                internalizedPart.add(newParameter)
                        }

                        val newSignature = thisPrefix + externalParametersPart + internalizedPart + DefaultParameterDescriptorFactory.identityTransform(internalParameters)

                        unmovedMembersThatRequireLocalSignatureModification.removeAll(membersBeingMoved)
                        for (unmovedMember in unmovedMembersThatRequireLocalSignatureModification)
                            if (unmovedMember is ParametersHolder) {
                                val (parameters, isReliableUnmovedMember) = ArendCodeInsightUtils.getParameterList(unmovedMember, false, null)
                                if (!isReliableUnmovedMember) unreliableReferables?.add(unmovedMember)

                                if (parameters != null) {
                                    val descriptor = ChangeSignatureRefactoringDescriptor(
                                        unmovedMember,
                                        parameters,
                                        DefaultParameterDescriptorFactory.identityTransform(parameters),
                                        null,
                                        MoveRefactoringSignatureContext(thisVarNameMap, membersClasses)
                                    )
                                    val localUsages =
                                        ReferencesSearch.search(unmovedMember, LocalSearchScope(referable)).toList()
                                            .map { ArendUsageInfo(it.element, descriptor) }
                                    usagesInMembersBeingMoved.addAll(localUsages)
                                }
                            }

                        newThisParameterClass?.let { membersClasses[referable] = it }
                        descriptors.add(ChangeSignatureRefactoringDescriptor(referable as PsiReferable, oldSignature, newSignature, null,
                            MoveRefactoringSignatureContext(thisVarNameMap, membersClasses)))
                    }
                }
            val globalUsagesOfMembersBeingMoved = descriptors.map { descriptor ->
                val affectedDefinition = descriptor.getAffectedDefinition()
                if (affectedDefinition?.isValid == true) ReferencesSearch.search(affectedDefinition).map {
                    ArendUsageInfo(it.element, descriptor)
                } else emptyList()
            }.flatten()

            return Pair(descriptors, (globalUsagesOfMembersBeingMoved + usagesInMembersBeingMoved).sorted())
        }
    }
}