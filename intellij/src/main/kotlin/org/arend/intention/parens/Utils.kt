package org.arend.intention.parens

import org.arend.intention.binOp.BinOpIntentionUtil
import org.arend.naming.binOp.MetaBinOpParser
import org.arend.naming.reference.GlobalReferable
import org.arend.psi.ancestor
import org.arend.psi.descendantOfType
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.ext.ArendAtomFieldsAcc
import org.arend.psi.ext.ArendNewExpr
import org.arend.psi.ext.ArendTuple
import org.arend.refactoring.rangeOfConcrete
import org.arend.refactoring.unwrapParens
import org.arend.term.concrete.Concrete
import org.arend.typechecking.order.PartialComparator

internal fun checkParens(expression: Concrete.Expression, parentBinOpApp: Concrete.AppExpression, queue: MutableList<Concrete.AppExpression>): Boolean {
    val binOp = when (expression) {
        is Concrete.HoleExpression -> findBinOpInParens(expression)
        is Concrete.AppExpression -> findBinOpInParens(expression)
        else -> null
    }
    if (binOp != null) {
        if (doesNotNeedParens(binOp, parentBinOpApp)) {
            return true
        }
        queue.add(binOp)
    }
    return false
}

internal fun findBinOpInParens(expression: Concrete.HoleExpression): Concrete.AppExpression? {
    val tuple = (expression.data as? ArendAtomFieldsAcc)?.descendantOfType<ArendTuple>() ?: return null
    val appExprPsi = unwrapAppExprInParens(tuple) ?: return null
    return BinOpIntentionUtil.toConcreteBinOpInfixApp(appExprPsi)
}

internal fun findBinOpInParens(expression: Concrete.AppExpression): Concrete.AppExpression? {
    val tuple = (expression.data as? ArendArgumentAppExpr)?.ancestor<ArendTuple>() ?: return null
    val appExprPsi = unwrapAppExprInParens(tuple) ?: return null
    return BinOpIntentionUtil.toConcreteBinOpInfixApp(appExprPsi)
}

internal fun unwrapAppExprInParens(tuple: ArendTuple): ArendArgumentAppExpr? {
    val expr = unwrapParens(tuple) ?: return null
    return (expr as? ArendNewExpr)?.appExpr as? ArendArgumentAppExpr ?: return null
}

internal fun doesNotNeedParens(childBinOp: Concrete.AppExpression, parentBinOp: Concrete.AppExpression): Boolean {
    val childPrecedence = getPrecedence(childBinOp.function) ?: return false
    val parentPrecedence = getPrecedence(parentBinOp.function) ?: return false
    val childIsLeftArg = rangeOfConcrete(childBinOp).endOffset < rangeOfConcrete(parentBinOp.function).startOffset
    return if (childIsLeftArg)
        MetaBinOpParser.comparePrecedence(childPrecedence, parentPrecedence) == PartialComparator.Result.GREATER
    else MetaBinOpParser.comparePrecedence(parentPrecedence, childPrecedence) == PartialComparator.Result.LESS
}

internal fun getPrecedence(function: Concrete.Expression) =
    ((function as? Concrete.ReferenceExpression)?.referent as? GlobalReferable)?.precedence