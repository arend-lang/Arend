package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict
import org.arend.term.abs.Abstract

class ArendSuperClass(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.ReferenceExpression {
    val longName: ArendLongName
        get() = childOfTypeStrict()

    override fun getData() = this

    override fun getReferent() = longName.referent

    override fun getLevels(): List<ArendLevelExpr>? = childOfType<ArendLevelArgs>()?.levelExprList
}