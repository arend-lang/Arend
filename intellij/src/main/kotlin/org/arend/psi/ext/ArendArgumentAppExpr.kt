package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.firstRelevantChild
import org.arend.psi.childOfTypeStrict
import org.arend.psi.getChildrenOfType


class ArendArgumentAppExpr(node: ASTNode) : ArendAppExpr(node) {
    val argumentList: List<ArendArgument>
        get() = getChildrenOfType()

    val atomFieldsAcc: ArendAtomFieldsAcc
        get() = childOfTypeStrict()

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val expr = firstRelevantChild as? ArendExpr ?: error("Incomplete expression: $this")
        val args = argumentList
        return if (args.isEmpty()) expr.accept(visitor, params) else visitor.visitBinOpSequence(this, expr, atomFieldsAcc.isVariable, args, params)
    }
}