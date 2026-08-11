package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.firstRelevantChild
import org.arend.psi.childOfType
import org.arend.term.abs.AbstractLevelExpressionVisitor
import org.arend.term.abs.Abstract
import java.math.BigInteger


class ArendAtomLevelExpr(node: ASTNode) : ArendLevelExpr(node), Abstract.LevelExpression {
    override fun getData() = this

    val levelExpr: ArendLevelExpr?
        get() = childOfType()

    val refIdentifier: ArendRefIdentifier?
        get() = childOfType()

    val lparen: PsiElement?
        get() = findChildByType(LPAREN)

    override fun <P, R> accept(visitor: AbstractLevelExpressionVisitor<in P, out R>, params: P?): R {
        val child = firstRelevantChild
        return when (child.elementType) {
            NUMBER -> visitor.visitNumber(this, BigInteger(child!!.text, 10), params)
            REF_IDENTIFIER -> visitor.visitId(this, NamedUnresolvedReference(refIdentifier, child!!.text), params)
            else -> {
                levelExpr?.let { return it.accept(visitor, params) }
                visitor.visitError(this, params)
            }
        }
    }
}