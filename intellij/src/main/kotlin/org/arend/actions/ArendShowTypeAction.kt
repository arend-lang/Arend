package org.arend.actions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.core.expr.*
import org.arend.psi.ArendTypeTele
import org.arend.psi.ext.ArendLamExprImplMixin
import org.arend.psi.ext.ArendLetExprImplMixin
import org.arend.psi.ext.ArendPiExprImplMixin
import org.arend.psi.ext.ArendSigmaExprImplMixin
import org.arend.refactoring.SubExprException
import org.arend.refactoring.correspondedSubExpr
import org.arend.refactoring.prettyPopupExpr
import org.arend.refactoring.rangeOfConcrete
import org.arend.term.concrete.Concrete
import org.arend.typechecking.subexpr.FindBinding

class ArendShowTypeAction : ArendPopupAction() {
    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) = try {
            doInvoke(editor, file, project)
        } catch (t: SubExprException) {
            displayHint { showErrorHint(editor, "Failed to obtain type because ${t.message}") }
        }
    }

    @Throws(SubExprException::class)
    private fun ArendPopupHandler.doInvoke(editor: Editor, file: PsiFile, project: Project) {
        val selected = EditorUtil.getSelectionInAnyMode(editor)
        val (subCore, subExpr, subPsi) = correspondedSubExpr(selected, file, project)
        fun select(range: TextRange) =
                editor.selectionModel.setSelection(range.startOffset, range.endOffset)
        fun hint(e: Expression?) = e?.let { displayHint { showInformationHint(editor, prettyPopupExpr(project, it)) } }
                ?: throw SubExprException("failed to synthesize type from given expr")
        fun default() {
            select(rangeOfConcrete(subExpr))
            hint(subCore.type)
        }
        if (subPsi is ArendLamExprImplMixin
                && subExpr is Concrete.LamExpression
                && subCore is LamExpression) {
            val param = subPsi.parameters
                    .firstOrNull { selected in it.textRange }
                    ?.identifierOrUnknownList
                    ?.firstOrNull { selected in it.textRange }
                    ?.defIdentifier
                    ?: run { default(); return }
            select(param.textRange)
            hint(FindBinding.visitLam(param, subExpr, subCore)?.typeExpr)
        } else if (subPsi is ArendPiExprImplMixin
                && subExpr is Concrete.PiExpression
                && subCore is PiExpression) {
            val param = typedTele(subPsi.parameters, selected)
                    ?: run { default(); return }
            select(param.textRange)
            hint(FindBinding.visitPi(param, subExpr, subCore).typeExpr)
        } else if (subPsi is ArendSigmaExprImplMixin
                && subExpr is Concrete.SigmaExpression
                && subCore is SigmaExpression) {
            val param = typedTele(subPsi.parameters, selected)
                    ?: run { default(); return }
            select(param.textRange)
            hint(FindBinding.visitSigma(param, subExpr, subCore).typeExpr)
        } else if (subPsi is ArendLetExprImplMixin
                && subExpr is Concrete.LetExpression
                && subCore is LetExpression) {
            val param = subPsi.letClauses
                    .asSequence()
                    .mapNotNull { it.defIdentifier }
                    .firstOrNull { selected in it.textRange }
                    ?: run { default(); return }
            select(param.textRange)
            hint(FindBinding.visitLet(param, subExpr, subCore))
        } else default()
    }

    private fun typedTele(p: List<ArendTypeTele>, selected: TextRange) = p
            .firstOrNull { selected in it.textRange }
            ?.typedExpr
            ?.identifierOrUnknownList
            ?.firstOrNull { selected in it.textRange }
            ?.defIdentifier
}