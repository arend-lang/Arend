package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.ext.typechecking.InteractiveGoalSolver
import org.arend.extImpl.definitionRenamer.CachingDefinitionRenamer
import org.arend.psi.ArendExpr
import org.arend.refactoring.PsiLocatedRenamer
import org.arend.refactoring.replaceExprSmart
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.DefinitionRenamerConcreteVisitor
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.arend.typechecking.visitor.CheckTypeVisitor
import org.arend.ui.impl.ArendEditorUI

class InteractiveGoalSolverQuickFix(private val element: ArendExpr, private val goal: GoalError, private val solver: InteractiveGoalSolver, private val action: (Document, Concrete.Expression, String) -> Unit) : IntentionAction {
    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        solver.solve(CheckTypeVisitor.loadTypecheckingContext(goal.typecheckingContext, project.service<TypeCheckingService>().typecheckerState, project.service<ErrorService>()), goal.causeSourceNode, goal.expectedType, ArendEditorUI(project, editor)) {
            if (it != null) {
                if (it !is Concrete.Expression) throw IllegalArgumentException()
                CommandProcessor.getInstance().executeCommand(project, {
                    invokeOnConcrete(it, editor)
                }, text, null, editor.document)
            }
        }
    }

    private fun invokeOnConcrete(concrete: Concrete.Expression, editor: Editor) {
        runReadAction {
            if (element.isValid && !editor.isDisposed) {
                val definitionRenamer = PsiLocatedRenamer(element)
                val text = concrete.accept(DefinitionRenamerConcreteVisitor(CachingDefinitionRenamer(definitionRenamer)), null).toString()
                ApplicationManager.getApplication().runWriteAction {
                    action(editor.document, concrete, text)
                    definitionRenamer.writeAllImportCommands()
                }
            }
        }
    }

    override fun startInWriteAction() = false

    override fun getFamilyName() = "arend.goal"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) =
        element.isValid && goal.typecheckingContext != null && solver.isApplicable(goal.causeSourceNode, goal.expectedType)

    override fun getElementToMakeWritable(currentFile: PsiFile) = currentFile

    override fun getText(): String = solver.shortDescription
}