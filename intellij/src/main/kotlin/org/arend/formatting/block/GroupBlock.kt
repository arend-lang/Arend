package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import java.util.ArrayList

class GroupBlock(myNode: ASTNode, private val blocks: List<Block>, wrap: Wrap?, alignment: Alignment?, indent: Indent) : AbstractArendBlock(myNode, wrap, alignment, indent) {
    override fun buildChildren(): MutableList<Block> {
        val result = ArrayList<Block>()
        result.addAll(blocks)
        return result
    }

    override fun getTextRange(): TextRange {
        val f = blocks.first()
        val l = blocks.last()
        return TextRange(f.textRange.startOffset, l.textRange.endOffset)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        System.out.println("GroupBlock.getChildAttributes($newChildIndex)")
        return if (newChildIndex == blocks.size) ChildAttributes(indent, alignment) else super.getChildAttributes(newChildIndex)
    }

    override fun toString(): String {
        var blockText = ""
        for (b in blocks) blockText += b.toString()+"; "
        return "$blockText $textRange"
    }
}