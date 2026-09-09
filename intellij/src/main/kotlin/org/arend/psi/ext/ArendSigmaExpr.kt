package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.ext.core.context.BindingVariance
import org.arend.psi.ArendElementTypes.SIGMA_PLUS_KW
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.getChildrenOfType
import org.arend.psi.hasChildOfType

class ArendSigmaExpr(node: ASTNode) : ArendExpr(node), Abstract.ParametersHolder {
    val sigmaVariance: BindingVariance
        get() = if (hasChildOfType(SIGMA_PLUS_KW)) BindingVariance.COVARIANT else BindingVariance.INVARIANT

    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitSigma(this, parameters, sigmaVariance, params)

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()
}
