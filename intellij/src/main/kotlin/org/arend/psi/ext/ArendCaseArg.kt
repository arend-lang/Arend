package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.arend.ext.core.context.BindingVariance
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract


class ArendCaseArg(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.CaseArgument {
    val colon: PsiElement?
        get() = findChildByType(COLON)

    val colonPlus: PsiElement?
        get() = findChildByType(COLON_PLUS)

    val asKw: PsiElement?
        get() = findChildByType(AS_KW)

    val elimKw: PsiElement?
        get() = findChildByType(ELIM_KW)

    override fun getApplyHoleData(): PsiElement? = findChildByType(APPLY_HOLE)

    override fun getExpression(): ArendExpr? = childOfType()

    override fun getReferable(): ArendDefIdentifier? = childOfType()

    override fun getType(): ArendExpr? = (colon ?: colonPlus)?.findNextSibling() as? ArendExpr

    override fun getEliminatedReference(): ArendRefIdentifier? = childOfType()

    override fun getVariance(): BindingVariance =
        if (colonPlus != null) BindingVariance.COVARIANT else BindingVariance.INVARIANT
}