package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.firstRelevantChild
import org.arend.term.abs.AbstractExpressionVisitor
import java.math.BigInteger


class ArendUniverseExpr(node: ASTNode) : ArendExpr(node) {
    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val child = firstRelevantChild ?: error ("Incorrect expression: universeExpr")
        return if (child is ArendAppExpr) child.accept(visitor, params) else visitor.visitUniverse(this, BigInteger.ZERO, BigInteger.valueOf(-1), null, params)
    }
}