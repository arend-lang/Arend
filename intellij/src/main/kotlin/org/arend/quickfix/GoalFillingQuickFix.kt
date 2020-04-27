package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.psi.ArendGoal
import org.arend.refactoring.replaceExprSmart

class GoalFillingQuickFix(private val element: ArendGoal) : IntentionAction {
    override fun invoke(project: Project, editor: Editor, file: PsiFile?) {
        val expr = element.expr ?: return
        replaceExprSmart(editor.document, element, null, element.textRange, expr, null, expr.text)
    }

    override fun startInWriteAction() = true

    override fun getFamilyName() = "arend.goal"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = element.isValid

    override fun getText() = "Fill goal"
}