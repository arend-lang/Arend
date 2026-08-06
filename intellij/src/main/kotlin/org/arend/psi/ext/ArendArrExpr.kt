package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.ext.core.context.BindingVariance
import org.arend.psi.ArendElementTypes.ARROW_PLUS
import org.arend.psi.ArendElementTypes.ARROW_MINUS
import org.arend.psi.childOfType
import org.arend.term.abs.AbstractExpressionVisitor


class ArendArrExpr(node: ASTNode) : ArendExpr(node) {
    val domain: ArendExpr?
        get() = childOfType()

    val codomain: ArendExpr?
        get() = childOfType(1)

    val arrowPlus: PsiElement?
        get() = findChildByType(ARROW_PLUS)

    val arrowMinus: PsiElement?
        get() = findChildByType(ARROW_MINUS)

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val domain = domain
        val variance = if (arrowPlus != null) BindingVariance.COVARIANT else if (arrowMinus != null) BindingVariance.CONTRAVARIANT else null
        return visitor.visitPi(this, if (domain == null) emptyList() else listOf(domain), variance, codomain, params)
    }
}