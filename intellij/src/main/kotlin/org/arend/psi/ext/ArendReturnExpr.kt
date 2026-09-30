package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import org.arend.psi.ArendElementTypes
import org.arend.psi.childOfType

class ArendReturnExpr(node: ASTNode) : ArendCompositeElementImpl(node) {
    val type: ArendExpr?
        get() = childOfType()

    val typeLevel: ArendExpr?
        get() = childOfType(1)

    val levelKw: PsiElement?
        get() = findChildByType<PsiElement>(LEVEL_KW_SET)

    val isLevelPlus: Boolean
        get() = findChildByType<PsiElement>(ArendElementTypes.LEVEL_PLUS_KW) != null

    companion object {
        private val LEVEL_KW_SET = TokenSet.create(ArendElementTypes.LEVEL_KW, ArendElementTypes.LEVEL_PLUS_KW)
    }
}