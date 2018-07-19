package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.abs.AbstractExpressionVisitor
import org.vclang.psi.VcAtom
import java.math.BigInteger


abstract class VcAtomImplMixin(node: ASTNode) : VcExprImplMixin(node), VcAtom {
    override fun <P : Any?, R : Any?> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        literal?.let { return it.accept(visitor, params) }
        (number ?: negativeNumber)?.let { return visitor.visitNumericLiteral(this, BigInteger(it.text), if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params) }
        tuple?.let {
            val exprList = it.exprList
            return if (exprList.size == 1) exprList[0].accept(visitor, params) else visitor.visitTuple(this, it.exprList, if (visitor.visitErrors()) org.vclang.psi.ext.getErrorData(this) else null, params)
        }
        error("Incorrect expression: atom")
    }
}