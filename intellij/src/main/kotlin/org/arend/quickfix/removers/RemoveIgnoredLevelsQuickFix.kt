package org.arend.quickfix.removers

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.elementType
import org.arend.psi.ArendElementTypes.DOT
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendAtomFieldsAcc
import org.arend.psi.ext.ArendFieldAcc
import org.arend.psi.prevElement
import org.arend.util.ArendBundle

class RemoveIgnoredLevelsQuickFix(private val cause: SmartPsiElementPointer<PsiElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.remove.levels")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = findLevelArgs() != null

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val fieldAcc = findLevelArgs() ?: return
        val dot = fieldAcc.prevElement?.takeIf { it.elementType == DOT }
        fieldAcc.delete()
        dot?.delete()
    }

    private fun findLevelArgs(): ArendFieldAcc? =
        cause.element?.ancestor<ArendAtomFieldsAcc>()?.fieldAccList?.firstOrNull { it.levels.isNotEmpty() }
}
