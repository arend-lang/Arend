package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractExpressionVisitor
import java.math.BigInteger


private fun parseBigInteger(text: String): BigInteger? =
    if (text.isEmpty()) null else BigInteger(text, 10)

private fun <P, R> acceptSet(data: ArendCompositeElement, setElem: PsiElement, levelExpr: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitUniverse(data, parseBigInteger(setElem.text.substring("\\Set".length)), BigInteger.ZERO, levelExpr, params)

private fun <P, R> acceptUniverse(data: ArendCompositeElement, universeElem: PsiElement, levelExpr: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitUniverse(data, parseBigInteger(universeElem.text.substring("\\Type".length)), null, levelExpr, params)

private fun <P, R> acceptCatUniverse(data: ArendCompositeElement, catElem: PsiElement, levelExpr: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
    visitor.visitCatUniverse(data, parseBigInteger(catElem.text.substring("\\Cat".length)), levelExpr, params)

private fun <P, R> acceptTruncated(data: ArendCompositeElement, truncatedElem: PsiElement, levelExpr: Abstract.LevelExpression?, visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
    val uniText = truncatedElem.text
    val index = uniText.indexOf('T')
    val hLevelNum = if (index > 0 && uniText[0] == '\\') parseBigInteger(uniText.substring(1, index - 1)) else null
    val pLevelNum = if (hLevelNum != null) parseBigInteger(uniText.substring(index + "Type".length)) else null
    return visitor.visitUniverse(data, pLevelNum, hLevelNum, levelExpr, params)
}


class ArendSetUniverseAppExpr(node: ASTNode) : ArendAppExpr(node) {
    val atomLevelExpr: ArendAtomLevelExpr?
        get() = childOfType()

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptSet(this, notNullChild(firstRelevantChild), atomLevelExpr, visitor, params)
}

class ArendCatUniverseAppExpr(node: ASTNode) : ArendAppExpr(node) {
    val atomLevelExpr: ArendAtomLevelExpr?
        get() = childOfType()

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptCatUniverse(this, notNullChild(firstRelevantChild), atomLevelExpr, visitor, params)
}

class ArendTruncatedUniverseAppExpr(node: ASTNode) : ArendAppExpr(node) {
    val atomLevelExpr: ArendAtomLevelExpr?
        get() = childOfType()

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptTruncated(this, notNullChild(firstRelevantChild), atomLevelExpr, visitor, params)
}

class ArendUniverseAppExpr(node: ASTNode) : ArendAppExpr(node) {
    val atomLevelExpr: ArendAtomLevelExpr?
        get() = childOfType()

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R =
        acceptUniverse(this, notNullChild(firstRelevantChild), atomLevelExpr, visitor, params)
}

class ArendUniverseAtom(node: ASTNode) : ArendExpr(node), ArendArgument {
    override fun isExplicit(): Boolean = true

    override fun isVariable() = false

    override fun getExpression(): ArendExpr = this

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val child = firstRelevantChild
        return when (child.elementType) {
            SET -> acceptSet(this, child!!, null, visitor, params)
            CAT_UNIVERSE -> acceptCatUniverse(this, child!!, null, visitor, params)
            UNIVERSE -> acceptUniverse(this, child!!, null, visitor, params)
            TRUNCATED_UNIVERSE -> acceptTruncated(this, child!!, null, visitor, params)
            else -> error("Incorrect expression: universe")
        }
    }
}
