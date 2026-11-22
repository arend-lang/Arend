package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.getChildrenOfType

/**
 * The list of attributes of a definition: \attr a b.
 */
class ArendAttributes(node: ASTNode) : ArendSourceNodeImpl(node) {
    val longNameList: List<ArendLongName>
        get() = getChildrenOfType()
}
