package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.ext.core.context.BindingVariance
import org.arend.psi.ArendElementTypes.COLON_PLUS
import org.arend.psi.childOfType
import org.arend.psi.getChildrenOfType

class ArendTypedExpr(node: ASTNode) : ArendCompositeElementImpl(node) {
    val identifierOrUnknownList: List<ArendIdentifierOrUnknown>
        get() = getChildrenOfType()

    val type: ArendExpr?
        get() = childOfType()

    val variance: BindingVariance
        get() = if (findChildByType<PsiElement>(COLON_PLUS) != null) BindingVariance.COVARIANT else BindingVariance.INVARIANT
}