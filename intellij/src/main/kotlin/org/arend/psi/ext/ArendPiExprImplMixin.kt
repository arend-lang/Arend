package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import org.arend.psi.ArendPiExpr
import org.arend.psi.ArendTypeTele

abstract class ArendPiExprImplMixin(node: ASTNode) : ArendExprImplMixin(node), ArendPiExpr, Abstract.ParametersHolder {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitPi(this, typeTeleList, expr, params)

    override fun getParameters(): List<ArendTypeTele> = typeTeleList
}
