package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.arend.psi.ArendElementTypes.*

open class GroupBlock(settings: CommonCodeStyleSettings?, private val blocks: MutableList<Block>, wrap: Wrap?, alignment: Alignment?, indent: Indent, parentBlock: AbstractArendBlock) :
        AbstractArendBlock(parentBlock.node, settings, wrap, alignment, indent, parentBlock) {
    override fun buildChildren(): MutableList<Block> = blocks

    override fun getTextRange(): TextRange {
        val f = blocks.first()
        val l = blocks.last()
        return TextRange(f.textRange.startOffset, l.textRange.endOffset)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        printChildAttributesContext(newChildIndex)
        when (node.elementType) {
            FUNCTION_CLAUSES, FUNCTION_BODY, INSTANCE_BODY -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
        }
        return if (newChildIndex == blocks.size) ChildAttributes(indent, alignment) else super.getChildAttributes(newChildIndex)
    }

    override fun toString(): String {
        var blockText = ""
        for (b in blocks) blockText += "$b; "
        return "$blockText $textRange"
    }

}