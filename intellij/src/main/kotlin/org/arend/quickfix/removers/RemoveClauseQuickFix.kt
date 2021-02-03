package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.ArendClause
import org.arend.psi.ArendElementTypes.PIPE
import org.arend.psi.deleteChildRangeWithNotification
import org.arend.psi.deleteWithNotification
import org.arend.psi.findPrevSibling

class RemoveClauseQuickFix (private val clauseRef: SmartPsiElementPointer<ArendClause>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.pattern"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = clauseRef.element != null

    override fun getText(): String = "Remove redundant clause"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val clause = clauseRef.element ?: return
        doRemoveClause(clause)
    }

    companion object {
        fun doRemoveClause(clause: PsiElement) {
            val prevSibling = clause.findPrevSibling()
            if (prevSibling != null && prevSibling.node.elementType == PIPE) clause.parent.deleteChildRangeWithNotification(prevSibling, clause) else
                clause.deleteWithNotification()
        }
    }
}