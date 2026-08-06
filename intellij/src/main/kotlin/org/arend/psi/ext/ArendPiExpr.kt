package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.ext.core.context.BindingVariance
import org.arend.psi.ArendElementTypes.ARROW_PLUS
import org.arend.psi.ArendElementTypes.ARROW_MINUS
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType

class ArendPiExpr(node: ASTNode) : ArendExpr(node), Abstract.ParametersHolder {
    val codomain: ArendExpr?
        get() = childOfType()

    val arrowPlus: PsiElement?
        get() = findChildByType(ARROW_PLUS)

    val arrowMinus: PsiElement?
        get() = findChildByType(ARROW_MINUS)

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitPi(this, parameters, if (arrowPlus != null) BindingVariance.COVARIANT else if (arrowMinus != null) BindingVariance.CONTRAVARIANT else null, codomain, params)

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()
}
