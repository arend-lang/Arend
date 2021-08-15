package org.arend.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parents
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.arend.core.context.binding.Binding
import org.arend.core.expr.Expression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.TCDefinition
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.rename.ArendGlobalReferableRenameHandler
import org.arend.refactoring.replaceExprSmart
import org.arend.refactoring.tryCorrespondedSubExpr
import org.arend.resolving.ArendReferableConverter
import org.arend.term.prettyprint.MinimizedRepresentation
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.typechecking.PsiInstanceProviderSet
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.util.ArendBundle
import org.arend.util.FreeVariablesWithDependenciesCollector
import org.arend.util.ParameterExplicitnessState
import java.util.*

class GenerateFunctionIntention : BaseIntentionAction() {

    override fun getText(): String = ArendBundle.message("arend.generate.function")
    override fun getFamilyName() = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        editor ?: return false
        file ?: return false
        if (!canModify(file) || !BaseArendIntention.canModify(file)) {
            return false
        }
        val selection = editor.getSelectionWithoutErrors() ?: return false
        return !selection.isEmpty ||
                file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) != null
    }

    private data class SelectionResult(
        val expectedType: Expression,
        val replaceablePsi: ArendCompositeElement,
        val identifier: String?,
        val body: Expression?
    )

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        editor ?: return
        file ?: return
        val selection = editor.getSelectionWithoutErrors() ?: return
        val selectionResult = if (selection.isEmpty) {
            extractGoalData(file, editor, project)
        } else {
            extractSelectionData(file, editor, project, selection)
        } ?: return
        val expressions = listOfNotNull(selectionResult.expectedType, selectionResult.body)
        val freeVariables = FreeVariablesWithDependenciesCollector.collectFreeVariables(expressions)
        performRefactoring(freeVariables, selectionResult, editor, project)
    }

    private fun getPrettyPrintConfig(context: ArendCompositeElement): PrettyPrinterConfig {
        val scope = context.scope.let(CachingScope::make)
        return object : PrettyPrinterConfigWithRenamer(scope?.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) }) {
            override fun getExpressionFlags(): EnumSet<PrettyPrinterFlag> = EnumSet.noneOf(PrettyPrinterFlag::class.java)
            override fun getNormalizationMode(): NormalizationMode = NormalizationMode.RNF
        }
    }

    private fun extractGoalData(file : PsiFile, editor: Editor, project: Project): SelectionResult? {
        val goal = file.findElementAt(editor.caretModel.offset)?.parentOfType<ArendGoal>(false) ?: return null
        val errorService = project.service<ErrorService>()
        val arendError = errorService.errors[goal.containingFile]?.firstOrNull { it.cause == goal }?.error as? GoalError
            ?: return null
        val goalType = (arendError as? GoalError)?.expectedType ?: return null
        val goalExpr = goal.expr?.let {
            tryCorrespondedSubExpr(it.textRange, file, project, editor)
        }?.subCore
        return SelectionResult(goalType, goal, goal.defIdentifier?.name, goalExpr)
    }

    private fun extractSelectionData(file : PsiFile, editor: Editor, project: Project, range : TextRange) : SelectionResult? {
        val subexprResult = tryCorrespondedSubExpr(range, file, project, editor) ?: return null
        val enclosingRange = rangeOfConcrete(subexprResult.subConcrete)
        val enclosingPsi =
            subexprResult
                .subPsi
                .parents(true)
                .filterIsInstance<ArendExpr>()
                .lastOrNull { enclosingRange.contains(it.textRange) }
                ?: subexprResult.subPsi
        return SelectionResult(subexprResult.subCore.type, enclosingPsi, null, subexprResult.subCore)
    }

    private fun performRefactoring(
        freeVariables: List<Pair<Binding, ParameterExplicitnessState>>, selection : SelectionResult,
        editor: Editor, project: Project
    ) {
        val enclosingFunctionDefinition = selection.replaceablePsi.parentOfType<ArendFunctionalDefinition>() ?: return
        val enclosingDefinitionReferable = selection.replaceablePsi.parentOfType<TCDefinition>()!!
        val (newFunctionCall, newFunctionDefinition) = buildRepresentations(
                enclosingDefinitionReferable, selection,
                enclosingFunctionDefinition,
                freeVariables,
        ) ?: return

        val globalOffsetOfNewDefinition =
            modifyDocument(editor, newFunctionCall, selection.replaceablePsi, newFunctionDefinition, enclosingFunctionDefinition, project)

        invokeRenamer(editor, globalOffsetOfNewDefinition, project)
    }

    private fun buildRepresentations(
            enclosingDefinitionReferable: TCDefinition,
            selection: SelectionResult,
            functionDefinition: ArendFunctionalDefinition,
            freeVariables: List<Pair<Binding, ParameterExplicitnessState>>,
    ): Pair<String, String>? {
        val newFunctionName = selection.identifier ?: functionDefinition.defIdentifier?.name?.let { "$it-lemma" } ?: return null

        val ppconfig = getPrettyPrintConfig(selection.replaceablePsi)

        val renamer = ppconfig.definitionRenamer

        val ip = PsiInstanceProviderSet().get(ArendReferableConverter.toDataLocatedReferable(enclosingDefinitionReferable)!!)
        val minimizedReturnType = MinimizedRepresentation.generateMinimizedRepresentation(selection.expectedType, ip, renamer)
        val explicitVariableNames = freeVariables.filter { it.second == ParameterExplicitnessState.EXPLICIT }
            .joinToString("") { " " + it.first.name }

        val parameters = freeVariables.joinToString("") { (binding, explicitness) ->
            " ${explicitness.openBrace}${binding.name} : ${binding.typeExpr.prettyPrint(ppconfig)}${explicitness.closingBrace}"
        }

        val actualBody = selection.body?.prettyPrint(ppconfig) ?: "{?}"
        val newFunctionCall = "$newFunctionName$explicitVariableNames"
        val newFunctionDefinition = "\\func $newFunctionName$parameters : $minimizedReturnType => $actualBody"
        return newFunctionCall to newFunctionDefinition
    }

    private fun modifyDocument(
        editor: Editor,
        newCall: String,
        replaceablePsi: ArendCompositeElement,
        newFunctionDefinition: String,
        oldFunction: PsiElement,
        project: Project
    ) : Int {
        val document = editor.document
        val startGoalOffset = replaceablePsi.startOffset
        val positionOfNewDefinition = oldFunction.endOffset - replaceablePsi.textLength + newCall.length + 4
        document.insertString(oldFunction.endOffset, "\n\n$newFunctionDefinition")
        val parenthesizedNewCall = replaceExprSmart(document, replaceablePsi, null, replaceablePsi.textRange, null, null, newCall, false)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return positionOfNewDefinition
        val callElementPointer =
            psiFile.findElementAt(startGoalOffset + 1)!!.let(SmartPointerManager::createPointer)
        val newDefinitionPointer =
            psiFile.findElementAt(positionOfNewDefinition)!!.let(SmartPointerManager::createPointer)
        CodeStyleManager.getInstance(project).reformatText(
            psiFile,
            listOf(
                TextRange(startGoalOffset, startGoalOffset + parenthesizedNewCall.length),
                TextRange(positionOfNewDefinition - 2, positionOfNewDefinition + newFunctionDefinition.length)
            )
        )
        editor.caretModel.moveToOffset(callElementPointer.element!!.startOffset)
        return newDefinitionPointer.element!!.startOffset
    }

    private fun invokeRenamer(editor: Editor, functionOffset: Int, project: Project) {
        val newFunctionDefinition =
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.findElementAt(functionOffset)
                ?.parentOfType<ArendDefFunction>() ?: return
        ArendGlobalReferableRenameHandler().doRename(newFunctionDefinition, editor, null)
    }
}
