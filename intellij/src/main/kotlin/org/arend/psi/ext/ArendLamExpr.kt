package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.ext.core.context.BindingVariance
import org.arend.psi.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor

class ArendLamExpr(node: ASTNode) : ArendExpr(node), Abstract.LamParametersHolder {
    val lamParamList: List<ArendLamParam>
        get() = getChildrenOfType()

    val body: ArendExpr?
        get() = childOfType()

    val fatArrow: PsiElement?
        get() = findChildByType(ArendElementTypes.FAT_ARROW)

    val fatArrowPlus: PsiElement?
        get() = findChildByType(ArendElementTypes.FAT_ARROW_PLUS)

    val fatArrowMinus: PsiElement?
        get() = findChildByType(ArendElementTypes.FAT_ARROW_MINUS)

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        visitor.visitLam(this, lamParamList, if (fatArrowPlus != null) BindingVariance.COVARIANT else if (fatArrowMinus != null) BindingVariance.CONTRAVARIANT else null, body, params)

    override fun getParameters() = lamParamList.filterIsInstance<ArendNameTele>()

    override fun getLamParameters() = lamParamList
}
