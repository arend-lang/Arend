package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.psi.deleteWithWhitespaces
import org.arend.psi.ext.ArendDefData
import org.arend.util.ArendBundle

class RemoveTruncatedKeywordQuickFix(private val cause: SmartPsiElementPointer<ArendDefData>) : IntentionAction {

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun getText(): String = ArendBundle.message("arend.truncated.remove")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        cause.element?.truncatedKw?.deleteWithWhitespaces()
    }
}
