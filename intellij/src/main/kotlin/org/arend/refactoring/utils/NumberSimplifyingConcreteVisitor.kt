package org.arend.refactoring.utils

import org.arend.prelude.Prelude
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.SubstConcreteVisitor
import java.math.BigInteger

class NumberSimplifyingConcreteVisitor: SubstConcreteVisitor(null) {
    override fun visitPattern(pattern: Concrete.Pattern?): Concrete.Pattern? {
        val n = pattern?.let{ isNaturalNumber(it) }
        if (n != null) {
            val result = Concrete.NumberPattern(null, n, null)
            result.isExplicit = pattern.isExplicit
            return result
        }
        return super.visitPattern(pattern)
    }

    override fun visitApp(expr: Concrete.AppExpression?, ignored: Void?): Concrete.Expression? {
        val n = expr?.let{ isNaturalNumber(it) }
        if (n != null) return Concrete.NumericLiteral(null, BigInteger.valueOf(n.toLong()))
        return super.visitApp(expr, ignored)
    }

    companion object {
        private fun isNaturalNumber(expr: Concrete.Expression): Int? {
            if (expr is Concrete.AppExpression && expr.function is Concrete.ReferenceExpression &&
                (expr.function as Concrete.ReferenceExpression).referent == Prelude.ZERO.referable) return 0
            if (expr is Concrete.AppExpression && expr.function is Concrete.ReferenceExpression &&
                (expr.function as Concrete.ReferenceExpression).referent == Prelude.SUC.referable && expr.arguments.size == 1)
                return isNaturalNumber(expr.arguments[0].expression)?.let { it + 1 }
            return null
        }

        private fun isNaturalNumber(expr: Concrete.Pattern): Int? {
            if (expr is Concrete.ConstructorPattern && expr.constructor == Prelude.ZERO.ref ||
                expr is Concrete.ConstructorPattern && expr.constructor == Prelude.FIN_ZERO.ref) return 0
            if (expr is Concrete.ConstructorPattern && expr.constructor == Prelude.SUC.ref &&
                expr.patterns.size == 1) {
                return isNaturalNumber(expr.patterns[0])?.let { it + 1 }
            }
            return null
        }
    }
}