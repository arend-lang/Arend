package org.arend.refactoring

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.psi.util.siblings
import org.arend.core.context.binding.Variable
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.ArendIPNameImplMixin
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.impl.ArendGroup
import org.arend.term.Fixity
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete
import org.arend.util.DefAndArgsInParsedBinopResult
import org.arend.util.getBounds
import org.arend.util.mapFirstNotNull
import java.util.Collections.singletonList

interface AbstractRefactoringAction {
    fun execute(editor: Editor?)
}

class ImportFileAction(private val importFile: ArendFile, private val currentFile: ArendFile, private val usingList: List<String>?) : AbstractRefactoringAction {
    override fun toString() = "Import file " + importFile.fullName

    private fun importFileCanBeFound(): Boolean {
        val modulePath = importFile.modulePath ?: return false
        val conf = currentFile.libraryConfig ?: return false
        return conf.availableConfigs.mapFirstNotNull { it.findArendFile(modulePath, true) } == importFile
    }

    fun isValid() = importFileCanBeFound() || isPrelude(importFile)

    override fun execute(editor: Editor?) {
        val factory = ArendPsiFactory(importFile.project)
        val modulePath = importFile.modulePath ?: return

        addStatCmd(factory,
                createStatCmdStatement(factory, modulePath.toString(), usingList?.map { Pair(it, null) }?.toList(), ArendPsiFactory.StatCmdKind.IMPORT),
                findPlaceForNsCmd(currentFile, modulePath))
    }
}

class AddIdToUsingAction(private val statCmd: ArendStatCmd, private val idList: List<Pair<String, String?>>) : AbstractRefactoringAction {
    val insertedNsIds = ArrayList<ArendNsId>()

    override fun toString(): String = "Add ${usingListToString(idList)} to the \"using\" list of the namespace command `${statCmd.text}`"

    private fun addId(id: String, newName: String?, factory: ArendPsiFactory, using: ArendNsUsing): ArendNsId? {
        val nsIds = using.nsIdList
        var anchor = using.lparen
        var needsCommaBefore = false

        for (nsId in nsIds) {
            val idRefName = nsId.refIdentifier.referenceName
            val idDefName = nsId.defIdentifier?.name
            if (idRefName <= id) {
                anchor = nsId
                needsCommaBefore = true
            }
            if (id == idRefName && newName == idDefName) return null
        }

        val nsIdStr = if (newName == null) id else "$id \\as $newName"
        val nsCmd = factory.createImportCommand("Dummy (a,$nsIdStr)", ArendPsiFactory.StatCmdKind.IMPORT).statCmd
        val newNsUsing = nsCmd!!.nsUsing!!
        val nsId = newNsUsing.nsIdList[1]!!
        val comma = nsId.prevSibling

        if (anchor == null) {
            anchor = using.usingKw ?: error("Can't find anchor within namespace command")
            anchor = anchor.parent.addAfter(newNsUsing.lparen!!, anchor)
            anchor.parent.addBefore(factory.createWhitespace(" "), anchor)
            anchor.parent.addAfter(newNsUsing.rparen!!, anchor)
        }

        if (anchor != null) {
            if (!needsCommaBefore && nsIds.isNotEmpty()) anchor.parent.addAfter(comma, anchor)
            val insertedId = anchor.parent.addAfterWithNotification(nsId, anchor)
            if (needsCommaBefore) anchor.parent.addAfter(comma, anchor)
            return insertedId as ArendNsId
        }

        return null
    }

    override fun execute(editor: Editor?) {
        val factory = ArendPsiFactory(statCmd.project)
        val insertAnchor = statCmd.longName

        val actualNsUsing: ArendNsUsing? = statCmd.nsUsing
                ?: if (idList.any { it.second != null } && insertAnchor != null) {
                    val newUsing = factory.createImportCommand("Dummy \\using ()", ArendPsiFactory.StatCmdKind.IMPORT).statCmd!!.nsUsing!!
                    val insertedUsing = insertAnchor.parent.addAfterWithNotification(newUsing, insertAnchor)
                    insertAnchor.parent.addAfter(factory.createWhitespace(" "), insertAnchor)
                    insertedUsing as ArendNsUsing
                } else null

        val actualIdList = if (actualNsUsing?.usingKw != null) idList.filter { it.second != null } else idList
        if (actualNsUsing != null) insertedNsIds.addAll(actualIdList.mapNotNull { addId(it.first, it.second, factory, actualNsUsing) })
    }
}

class RemoveRefFromStatCmdAction(private val statCmd: ArendStatCmd?, val id: ArendRefIdentifier, private val deleteEmptyCommands: Boolean = true) : AbstractRefactoringAction {
    override fun toString(): String {
        val listType = when (id.parent) {
            is ArendStatCmd -> "\"hiding\" list"
            /* ArendNsUsing */ else -> "\"using\" list"
        }
        val name = if (statCmd != null) statCmdName(statCmd) else "???"
        return "Remove " + id.referenceName + " from " + name + " import's $listType"
    }

    override fun execute(editor: Editor?) {
        val elementToRemove = if (id.parent is ArendNsId) id.parent else id
        val parent = elementToRemove.parent

        val prevSibling = elementToRemove.findPrevSibling()
        val nextSibling = elementToRemove.findNextSibling()

        elementToRemove.deleteWithNotification()

        if (prevSibling?.node?.elementType == ArendElementTypes.COMMA) {
            prevSibling?.delete()
        } else if (prevSibling?.node?.elementType == ArendElementTypes.LPAREN) {
            if (nextSibling?.node?.elementType == ArendElementTypes.COMMA) {
                nextSibling?.delete()
            }
        }

        if (parent is ArendStatCmd && parent.refIdentifierList.isEmpty()) { // This means that we are removing something from "hiding" list
            parent.lparen?.delete()
            parent.rparen?.delete()
            parent.hidingKw?.delete()
        }

        val statCmd = if (parent is ArendStatCmd) parent else {
            val grandParent = parent.parent
            if (grandParent is ArendStatCmd) grandParent else null
        }

        if (statCmd != null && statCmd.openKw != null && deleteEmptyCommands) { //Remove open command with null effect
            val nsUsing = statCmd.nsUsing
            if (nsUsing != null && nsUsing.usingKw == null && nsUsing.nsIdList.isEmpty()) {
                val statCmdStatement = statCmd.parent
                statCmdStatement.deleteWithNotification()
            }
        }
    }
}

class RenameReferenceAction constructor(private val element: ArendReferenceElement, private val id: List<String>) : AbstractRefactoringAction {
    private val needsModification = element.longName != id

    override fun toString(): String = "Rename " + element.text + " to " + LongName(id).toString()

    override fun execute(editor: Editor?) {
        val parent = element.parent
        val factory = ArendPsiFactory(element.project)

        when (element) {
            is ArendIPNameImplMixin -> if (parent is ArendLiteral) {
                if (!needsModification) return
                val argumentStr = buildString {
                    if (id.size > 1) {
                        append(LongName(id.dropLast(1)))
                        append(".")
                    }
                    if (element.fixity == Fixity.INFIX || element.fixity == Fixity.POSTFIX) append("`")
                    append(id.last())
                    if (element.fixity == Fixity.INFIX) append("`")

                }
                val replacementLiteral = factory.createExpression(argumentStr).childOfType<ArendLiteral>()
                if (replacementLiteral != null) parent.replaceWithNotification(replacementLiteral) as? ArendLiteral
            }
            else -> {
                val longNameStr = LongName(id).toString()
                val offset = element.textOffset
                val longName = factory.createLongName(longNameStr)
                when (parent) {
                    is ArendLongName -> if (needsModification) {
                        parent.addRangeAfterWithNotification(longName.firstChild, longName.lastChild, element)
                        parent.deleteChildRangeWithNotification(parent.firstChild, element)
                    }
                    is ArendPattern -> if (needsModification) element.replaceWithNotification(longName)
                }
                if (needsModification) editor?.caretModel?.moveToOffset(offset + longNameStr.length)
            }
        }
    }
}

fun isPrelude(file: ArendFile) = file.modulePath == Prelude.MODULE_PATH && file.containingDirectory == null

fun statCmdName(statCmd: ArendStatCmd) =
        (statCmd.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? ArendFile)?.modulePath?.toString()
                ?: "???"

fun usingListToString(usingList: List<Pair<String, String?>>?): String {
    if (usingList == null) return ""
    val buffer = StringBuffer()
    buffer.append("(")
    for ((m, entry) in usingList.withIndex()) {
        buffer.append(entry.first + (if (entry.second == null) "" else " \\as ${entry.second}"))
        if (m < usingList.size - 1) buffer.append(", ")
    }
    buffer.append(")")
    return buffer.toString()
}

fun findPlaceForNsCmd(currentFile: ArendFile, fileToImport: ModulePath): RelativePosition =
        if (currentFile.children.isEmpty()) RelativePosition(PositionKind.INSIDE_EMPTY_ANCHOR, currentFile) else {
            var anchor: PsiElement = currentFile.children[0]
            val fullName = fileToImport.toString()
            var after = false

            val currFileCommands = currentFile.namespaceCommands.filter { it.importKw != null }
            if (currFileCommands.isNotEmpty()) {
                val name = LongName(currFileCommands[0].path).toString()
                anchor = currFileCommands[0].parent
                if (fullName >= name)
                    after = true
            }

            if (after) for (nC in currFileCommands.drop(1)) {
                val name = LongName(nC.path).toString()
                if (fullName >= name)
                    anchor = nC.parent else break
            }

            RelativePosition(if (after) PositionKind.AFTER_ANCHOR else PositionKind.BEFORE_ANCHOR, anchor)
        }

fun createStatCmdStatement(factory: ArendPsiFactory, fullName: String, usingList: List<Pair<String, String?>>?, kind: ArendPsiFactory.StatCmdKind) =
        factory.createImportCommand(fullName + " " + usingListToString(usingList), kind)

fun addStatCmd(factory: ArendPsiFactory,
               commandStatement: ArendStatement,
               relativePosition: RelativePosition): PsiElement {
    val insertedStatement: PsiElement

    when (relativePosition.kind) {
        PositionKind.BEFORE_ANCHOR -> {
            insertedStatement = relativePosition.anchor.parent.addBeforeWithNotification(commandStatement, relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace("\n"), insertedStatement)
        }
        PositionKind.AFTER_ANCHOR -> {
            insertedStatement = relativePosition.anchor.parent.addAfterWithNotification(commandStatement, relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace("\n"), relativePosition.anchor)
            insertedStatement.parent.addAfter(factory.createWhitespace(" "), insertedStatement)
        }
        PositionKind.INSIDE_EMPTY_ANCHOR -> {
            insertedStatement = relativePosition.anchor.addWithNotification(commandStatement)
        }
    }
    return insertedStatement
}

fun addIdToUsing(groupMember: PsiElement?,
                 targetContainer: PsiElement,
                 targetContainerName: String,
                 renamings: List<Pair<String, String?>>,
                 factory: ArendPsiFactory,
                 relativePosition: RelativePosition): List<ArendNsId> {
    groupMember?.parent?.children?.filterIsInstance<ArendStatement>()?.map {
        val statCmd = it.statCmd
        if (statCmd != null/* && statCmd.nsUsing != null*/) {
            val ref = statCmd.longName?.refIdentifierList?.lastOrNull()
            if (ref != null) {
                val target = ref.reference?.resolve()
                if (target == targetContainer) {
                    val action = AddIdToUsingAction(statCmd, renamings)
                    action.execute(null)
                    return action.insertedNsIds
                }
            }
        }
    }

    if (targetContainerName.isNotEmpty()) {
        val insertedStatement = addStatCmd(factory,
                createStatCmdStatement(factory, targetContainerName, renamings, ArendPsiFactory.StatCmdKind.OPEN),
                relativePosition)
        val statCmd = insertedStatement.childOfType<ArendStatCmd>()
        return statCmd?.nsUsing?.nsIdList ?: emptyList()
    }
    return emptyList()
}

fun getImportedNames(namespaceCommand: ArendStatCmd, shortName: String?): List<Pair<String, ArendNsId?>> {
    if (shortName == null) return emptyList()

    val nsUsing = namespaceCommand.nsUsing
    val isHidden = namespaceCommand.refIdentifierList.any { it.referenceName == shortName }

    if (nsUsing != null) {
        val resultList = ArrayList<Pair<String, ArendNsId?>>()

        for (nsId in nsUsing.nsIdList) {
            if (nsId.refIdentifier.text == shortName) {
                val defIdentifier = nsId.defIdentifier
                resultList.add(Pair(defIdentifier?.textRepresentation() ?: shortName, nsId))
            }
        }

        return resultList
    }

    return if (isHidden) emptyList() else singletonList(Pair(shortName, null))
}

fun deleteSuperfluousPatternParentheses(atomPattern: ArendAtomPattern) {
    if (atomPattern.lparen != null && atomPattern.rparen != null) {
        val pattern = atomPattern.parent as? ArendPattern
        if (pattern != null) {
            val parentAtom = pattern.parent as? ArendAtomPattern
            if (parentAtom != null && parentAtom.lparen != null && parentAtom.rparen != null) {
                parentAtom.replaceWithNotification(atomPattern)
            }
        }
    }

}

fun moveCaretToEndOffset(editor: Editor?, anchor: PsiElement) {
    if (editor != null) {
        editor.caretModel.moveToOffset(anchor.textRange.endOffset)
        IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
    }
}

fun moveCaretToStartOffset(editor: Editor?, anchor: PsiElement) {
    if (editor != null) {
        editor.caretModel.moveToOffset(anchor.textRange.startOffset)
        IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
    }
}

fun getAllBindings(psi: PsiElement, stopAtWhere: Boolean = true): Set<String> {
    val result = mutableSetOf<String>()
    psi.accept(object : PsiRecursiveElementVisitor() {
        override fun visitElement(element: PsiElement) {
            if (element is ArendReferenceElement) result.add(element.referenceName)
            if (element is ArendWhere && stopAtWhere) return
            super.visitElement(element)
        }
    })
    return result
}

data class VariableImpl(private val varName: String) : Variable {
    override fun getName() = varName
}

fun getClassifyingField(classDef: ArendDefClass): ArendFieldDefIdentifier? {
    fun doGetClassifyingField(classDef: ArendDefClass, visitedClasses: MutableSet<ArendDefClass>): ArendFieldDefIdentifier? {
        if (!visitedClasses.add(classDef) || classDef.isRecord) return null

        for (ancestor in classDef.superClassReferences)
            if (ancestor is ArendDefClass)
                doGetClassifyingField(ancestor, visitedClasses)?.let { return it }

        classDef.fieldTeleList.firstOrNull { it.classifyingKw != null }?.fieldDefIdentifierList?.firstOrNull()?.let { return it }
        classDef.fieldTeleList.firstOrNull { it.isExplicit }?.fieldDefIdentifierList?.firstOrNull()?.let{ return it }

        return null
    }

    return doGetClassifyingField(classDef, HashSet())
}

fun surroundWithBraces(psiFactory: ArendPsiFactory, defClass: ArendDefClass) {
    val braces = psiFactory.createPairOfBraces()
    defClass.addAfter(braces.first, defClass.defIdentifier)
    defClass.addAfter(psiFactory.createWhitespace(" "), defClass.defIdentifier)
    defClass.addAfter(braces.second, defClass.lastChild)

    fun surroundWithClassStat(startChild: PsiElement, endChild: PsiElement) {
        val insertedClassStat = defClass.addAfterWithNotification(psiFactory.createClassStat(), endChild) as ArendClassStat
        val definition = insertedClassStat.definition!!
        insertedClassStat.addRangeAfter(startChild, endChild, definition)
        definition.delete()
        defClass.deleteChildRange(startChild, endChild)
    }

    var pipePosition: PsiElement? = null
    var currentChild: PsiElement? = defClass.firstChild
    while (currentChild != null) {
        val nextSibling = currentChild.nextSibling
        if (pipePosition != null && nextSibling != null &&
                (nextSibling.elementType == ArendElementTypes.RBRACE || nextSibling.elementType == ArendElementTypes.PIPE)) {
            surroundWithClassStat(pipePosition, currentChild)
            pipePosition = null
        }
        if (currentChild.elementType == ArendElementTypes.PIPE) pipePosition = currentChild

        currentChild = nextSibling
    }
}

fun getAnchorInAssociatedModule(psiFactory: ArendPsiFactory, myTargetContainer: ArendGroup): PsiElement? {
    val oldWhereImpl = myTargetContainer.where
    val actualWhereImpl = if (oldWhereImpl != null) oldWhereImpl else {
        val localAnchor = myTargetContainer.lastChild
        val insertedWhere = myTargetContainer.addAfterWithNotification(psiFactory.createWhere(), localAnchor) as ArendWhere
        myTargetContainer.addAfter(psiFactory.createWhitespace(" "), localAnchor)
        insertedWhere
    }

    if (actualWhereImpl.lbrace == null || actualWhereImpl.rbrace == null) {
        val braces = psiFactory.createPairOfBraces()
        if (actualWhereImpl.lbrace == null) {
            actualWhereImpl.addAfter(braces.first, actualWhereImpl.whereKw)
            actualWhereImpl.addAfter(psiFactory.createWhitespace(" "), actualWhereImpl.whereKw)
        }
        if (actualWhereImpl.rbrace == null) {
            actualWhereImpl.addAfter(braces.second, actualWhereImpl.lastChild)
        }
    }

    return actualWhereImpl.statementList.lastOrNull() ?: actualWhereImpl.lbrace
}

// Binop util method plus auxiliary stuff

fun resolveIfNeeded(referent: Referable, scope: Scope) =
        ExpressionResolveNameVisitor.resolve(referent, scope, true, null)?.underlyingReferable

fun getFirstExplicitParameter(definition: Referable?, defaultName: String): String {
    if (definition is Abstract.ParametersHolder) {
        val firstParameter = definition.parameters.firstOrNull { it.isExplicit }
        val firstReferable = firstParameter?.referableList?.firstOrNull()
        if (firstReferable is ArendDefIdentifier) return firstReferable.name ?: defaultName
    }
    return defaultName
}

fun addImplicitArgAfter(psiFactory: ArendPsiFactory, anchor: PsiElement, argument: String, infixMode: Boolean) {
    val thisArgument = psiFactory.createExpression("foo {$argument}").childOfType<ArendImplicitArgument>()
    if (thisArgument != null) {
        if (anchor is ArendAtomFieldsAcc || infixMode) {
            anchor.parent?.addAfterWithNotification(thisArgument, anchor)
            anchor.parent?.addAfterWithNotification(psiFactory.createWhitespace(" "), anchor)
        } else if (anchor is ArendAtomArgument) {
            val oldLiteral = anchor.atomFieldsAcc.atom.literal
            val tuple = psiFactory.createExpression("(${anchor.text} {$argument})").childOfType<ArendTuple>()
            if (oldLiteral != null && tuple != null) oldLiteral.replaceWithNotification(tuple)
        }
    }
}

fun surroundingTupleExpr(baseExpr: ArendExpr): ArendTupleExpr? =
        (baseExpr.parent as? ArendNewExpr)?.let { newExpr ->
            if (newExpr.appPrefix == null && newExpr.lbrace == null && newExpr.argumentList.isEmpty())
                newExpr.parent as? ArendTupleExpr else null
        }

fun transformPostfixToPrefix(psiFactory: ArendPsiFactory,
                             argumentOrFieldsAcc: PsiElement,
                             defArgsData: DefAndArgsInParsedBinopResult): ArendArgumentAppExpr? {
    val argumentAppExpr = argumentOrFieldsAcc.parent as ArendArgumentAppExpr
    val ipName = defArgsData.functionReferenceContainer as ArendIPName
    val nodes = argumentAppExpr.firstChild.siblings().map { it.node }.toList()
    val operatorConcrete = defArgsData.operatorConcrete.let { if (it is Concrete.LamExpression) it.body else it }
    val operatorRange = getBounds(operatorConcrete, nodes)!!
    val psiElements = nodes.filter { operatorRange.contains(it.textRange) }.map { it.psi }

    var resultingExpr = "${LongName(ipName.longName)} "
    val leadingElements = java.util.ArrayList<PsiElement>()
    val trailingElements = java.util.ArrayList<PsiElement>()
    var beforeLiteral = true
    for (element in psiElements) when {
        element == argumentOrFieldsAcc -> beforeLiteral = false
        beforeLiteral -> leadingElements.add(element)
        else -> trailingElements.add(element)
    }

    val requiresLeadingArgumentParentheses = leadingElements.filter { it !is PsiComment && it !is PsiWhiteSpace }.size > 1
    var leadingText = leadingElements.fold("", { acc, element -> acc + element.text }).trim()
    if (requiresLeadingArgumentParentheses) leadingText = "(${leadingText})"
    val trailingText = trailingElements.fold("", { acc, element -> acc + element.text }).trim()
    val isLambda = leadingElements.size == 0

    if (isLambda) {
        val defaultLambdaName = "_x"
        val baseName = if (operatorConcrete is Concrete.AppExpression) {
            val function = operatorConcrete.function as? Concrete.ReferenceExpression
            getFirstExplicitParameter(function?.referent, defaultLambdaName)
        } else defaultLambdaName

        val operatorBindings = HashSet<String>()
        for (psi in psiElements) operatorBindings.addAll(getAllBindings(psi))
        val lambdaVarName = StringRenamer().generateFreshName(VariableImpl(baseName), operatorBindings.map { VariableImpl(it) }.toList())
        resultingExpr = "(\\lam $lambdaVarName => $resultingExpr$lambdaVarName $trailingText)"
    } else resultingExpr += "$leadingText $trailingText"
    val result: ArendExpr? = when {
        psiElements.size == nodes.size -> {
            val tupleExpr = surroundingTupleExpr(argumentAppExpr)
            val appExpr = psiFactory.createExpression(resultingExpr.trim()).childOfType<ArendArgumentAppExpr>()!!
            if (tupleExpr != null && tupleExpr.colon == null && isLambda)
                argumentAppExpr.replaceWithNotification(appExpr.childOfType<ArendLamExpr>()!!) as? ArendExpr
            else
                argumentAppExpr.replaceWithNotification(appExpr) as? ArendArgumentAppExpr
        }
        operatorRange.contains(nodes.first().textRange) -> {
            val atomFieldsAcc = psiFactory.createExpression("(${resultingExpr.trim()}) foo").childOfType<ArendAtomFieldsAcc>()!!
            val insertedExpr = argumentAppExpr.addAfterWithNotification(atomFieldsAcc, psiElements.last())
            argumentAppExpr.deleteChildRangeWithNotification(psiElements.first(), psiElements.last())
            insertedExpr.childOfType<ArendArgumentAppExpr>()
        }
        else -> {
            val atom = psiFactory.createExpression("foo (${resultingExpr.trim()})").childOfType<ArendAtomArgument>()!!
            val insertedExpr = argumentAppExpr.addBeforeWithNotification(atom, psiElements.first())
            argumentAppExpr.deleteChildRangeWithNotification(psiElements.first(), psiElements.last())
            insertedExpr.childOfType<ArendArgumentAppExpr>()
        }
    }
    return if (isLambda) result?.childOfType(true) else result as ArendArgumentAppExpr
}

fun getPrec(psiElement: PsiElement?): ArendPrec? = when (psiElement) {
    is ArendFunctionalDefinition -> psiElement.getPrec()
    is ArendDefData -> psiElement.prec
    is ArendDefClass -> psiElement.prec
    is ArendConstructor -> psiElement.prec
    is ArendNsId -> psiElement.prec
    is ArendClassField -> psiElement.prec
    else -> null
}

fun isInfix(prec: ArendPrec): Boolean = prec.infixLeftKw != null || prec.infixNonKw != null || prec.infixRightKw != null

fun calculateOccupiedNames(occupiedNames: Collection<Variable>, parameterName: String?, nRecursiveBindings: Int) =
        if (nRecursiveBindings > 1 && parameterName != null && parameterName.isNotEmpty() && !Character.isDigit(parameterName.last()))
            occupiedNames.plus(VariableImpl(parameterName)) else occupiedNames