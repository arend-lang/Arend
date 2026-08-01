package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.psi.ArendElementTypes.DOT_COLON
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType

class ArendTypedExpr(node: ASTNode) : ArendCompositeElementImpl(node) {
    val identifierOrUnknownList: List<ArendIdentifierOrUnknown>
        get() = getChildrenOfType()

    val type: ArendExpr?
        get() = childOfType()

    val isDotted: Boolean
        get() = findChildByType<PsiElement>(DOT_COLON) != null
}