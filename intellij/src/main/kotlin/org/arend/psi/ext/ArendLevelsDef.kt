package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildrenOfType
import org.arend.term.abs.Abstract

class ArendLevelsDef(node: ASTNode) : ArendCompositeElementImpl(node), Abstract.LevelParameters {
    override fun getData() = this

    override fun getReferables(): List<ArendLevelIdentifier> = getChildrenOfType()
}