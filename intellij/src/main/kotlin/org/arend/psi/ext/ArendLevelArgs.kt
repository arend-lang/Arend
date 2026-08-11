package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildrenOfType

class ArendLevelArgs(node: ASTNode) : ArendCompositeElementImpl(node) {
    val levelExprList: List<ArendLevelExpr>
        get() = getChildrenOfType()
}
