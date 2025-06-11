package org.arend.intention.parens

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.intention.BaseArendIntention
import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.intention.binOp.BinOpSeqProcessor
import org.arend.intention.binOp.CaretHelper
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.surroundingTupleExpr
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle

class RemoveClarifyingParensIntention : BaseArendIntention(ArendBundle.message("arend.expression.removeClarifyingParentheses")) {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        editor ?: return false
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return false
        val binOpSeqPsi = binOp.parentOfType<ArendArgumentAppExpr>() ?: return false
        val binOpSeq = BinOpIntentionUtil.toConcreteBinOpInfixApp(binOpSeqPsi) ?: return false
        val parentBinOp = getParentBinOpSkippingParens(binOpSeq) ?: binOpSeq
        return hasClarifyingParens(parentBinOp)
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        editor ?: return
        val binOp = BinOpIntentionUtil.findBinOp(element) ?: return
        val binOpSeqPsi = binOp.parentOfType<ArendArgumentAppExpr>() ?: return
        val binOpSeq = BinOpIntentionUtil.toConcreteBinOpInfixApp(binOpSeqPsi) ?: return
        val parentBinOp = getParentBinOpSkippingParens(binOpSeq) ?: binOpSeq
        RemoveClarifyingParensProcessor().run(project, editor, binOp, parentBinOp)
    }
}

private fun getParentBinOpSkippingParens(binOp: Concrete.AppExpression): Concrete.AppExpression? {
    val tuple =  (binOp.data as? ArendArgumentAppExpr)?.let{ parentParensExpression(it) } ?: return null
    val parentAppExprPsi = parentArgumentAppExpr(tuple) ?: return null
    if (parentAppExprPsi.argumentList.isEmpty()) {
        return null
    }
    val parentAppExpr = BinOpIntentionUtil.toConcreteBinOpInfixApp(parentAppExprPsi) ?: return null
    return getParentBinOpSkippingParens(parentAppExpr) ?: parentAppExpr
}

private fun parentParensExpression(appExpr: ArendArgumentAppExpr): ArendTuple? =
        surroundingTupleExpr(appExpr)
                ?.let { if (it.colon == null) it.parent as? ArendTuple else null }
                ?.takeIf { it.tupleExprList.size == 1 }

private fun parentArgumentAppExpr(tuple: ArendTuple): ArendArgumentAppExpr? =
        tuple.parentOfType<ArendAtomFieldsAcc>()?.let { parentArgumentAppExpr(it) }

private fun hasClarifyingParens(binOpSeq: Concrete.AppExpression): Boolean {
    if ((binOpSeq.data as? PsiElement)?.textContains('(') == false) {
        return false
    }
    var clarifyingParensFound = false
    val queue = mutableListOf(binOpSeq)
    while (!clarifyingParensFound && queue.isNotEmpty()) {
        val parentBinOpApp = queue.removeFirst()
        for (arg in parentBinOpApp.arguments) {
            if (!arg.isExplicit) {
                continue
            }
            val expression = arg.expression
            if (expression is Concrete.HoleExpression && checkParens(expression, parentBinOpApp, queue)) {
              clarifyingParensFound = true
              break
            }
            if (expression is Concrete.AppExpression && BinOpIntentionUtil.isBinOpInfixApp(expression)) {
                val queueSize = queue.size
                if (checkParens(expression, parentBinOpApp, queue)) {
                    clarifyingParensFound = true
                    break
                } else if (queueSize == queue.size) {
                    queue.add(expression)
                }
            }
        }
    }
    return clarifyingParensFound
}

class RemoveClarifyingParensProcessor : BinOpSeqProcessor() {
    override fun mapArgument(arg: Concrete.Argument,
                             parentBinOp: Concrete.AppExpression,
                             editor: Editor,
                             caretHelper: CaretHelper): String? {
        if (!arg.isExplicit) {
            return implicitArgumentText(arg, editor)
        }
        val expression = arg.expression
        if (expression is Concrete.HoleExpression || (expression is Concrete.AppExpression && BinOpIntentionUtil.isBinOpInfixApp(expression))) {
            val binOp = when (expression) {
                is Concrete.HoleExpression -> findBinOpInParens(expression)
                is Concrete.AppExpression -> findBinOpInParens(expression)
                else -> null
            }
            if (binOp != null) {
                val binOpText = mapBinOp(binOp, editor, caretHelper)
                return if (doesNotNeedParens(binOp, parentBinOp)) binOpText else "($binOpText)"
            }
            if (expression is Concrete.AppExpression) {
                return mapBinOp(expression, editor, caretHelper)
            }
        }
        return text(arg.expression, editor)
    }
}