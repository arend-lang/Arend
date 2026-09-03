package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import org.arend.formatting.block.SimpleArendBlock.Companion.oneSpaceWrap
import org.arend.term.concrete.Concrete
import org.arend.util.getBounds

class ArgumentAppExprBlock(val cExpr: Concrete.Expression, node: ASTNode, settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?, parentBlock: AbstractArendBlock?) :
        AbstractArendBlock(node, settings, wrap, alignment, myIndent, parentBlock) {
    override fun buildChildren(): MutableList<Block> {
        val children = myNode.getChildren(null).filter { it.elementType != TokenType.WHITE_SPACE }.toList()
        val result = transform(cExpr, children, Alignment.createAlignment(), Indent.getNoneIndent())
        return ArrayList(result.subBlocks)
    }

    override fun getSpacing(child1: Block?, child2: Block): Spacing = oneSpaceWrap

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        printChildAttributesContext(newChildIndex)

        if (newChildIndex > 0) {
            val child = subBlocks[newChildIndex-1]

            val isLast = newChildIndex == subBlocks.size
            val alignNeeded = if (isLast) subBlocks.filterNotNull().any { hasLfBefore(it) } else true

            val indent = if (child == null) Indent.getNoneIndent() else child.indent
            val align = if (alignNeeded) child?.alignment else getGrandParentAlignment()

            return ChildAttributes(indent, align)
        }

        return super.getChildAttributes(newChildIndex)
    }

    private fun transform(cExpr: Concrete.Expression, aaeBlocks: List<ASTNode>, align: Alignment?, indent: Indent): AbstractArendBlock {
        val cExprData = cExpr.data
        if (cExpr is Concrete.AppExpression) {
            val blocks = ArrayList<Block>()
            val fData = cExpr.function.data

            val sampleBlockList = ArrayList<Pair<Boolean, Int>>()
            sampleBlockList.addAll(cExpr.arguments.map {
                val data = it.expression.data
                Pair(false, (data as? PsiElement)?.node?.startOffset ?: -1) })
            if (fData is PsiElement) sampleBlockList.add(Pair(true, fData.node.startOffset))
            sampleBlockList.sortBy { it.second }

            val isPrefix = sampleBlockList.isNotEmpty() && sampleBlockList.any { it.second == sampleBlockList.first().second && it.first }
            fun isFirst(argument: Concrete.Argument): Boolean =
                sampleBlockList.size > 0 && ((argument.expression.data as? PsiElement)?.node?.startOffset ?: -1) == sampleBlockList[0].second

            var newAlign =  if (cExpr.arguments.size > 1) Alignment.createAlignment() else null
            val newIndent = if (isPrefix) Indent.getContinuationIndent() else Indent.getNoneIndent()

            blocks.addAll(cExpr.arguments.asSequence().mapNotNull {
                val myBounds = getBounds(it.expression, aaeBlocks)
                val aaeBlocksFiltered = aaeBlocks.filter { aaeBlock -> myBounds?.contains(aaeBlock.textRange) == true  }.asSequence().sortedBy{ aaeBlock -> aaeBlock.startOffset }.toList()
                val first = isFirst(it)
                if (aaeBlocksFiltered.isNotEmpty()) {
                    var leadingWhitespace = aaeBlocksFiltered.first().psi.prevSibling
                    if (leadingWhitespace is PsiComment) leadingWhitespace = leadingWhitespace.prevSibling
                    val hasLf = leadingWhitespace is PsiWhiteSpace && leadingWhitespace.textContains('\n')
                    if (hasLf) newAlign = Alignment.createAlignment()
                    transform(it.expression, aaeBlocksFiltered,
                        if (!first) newAlign else null,
                        if (!first) newIndent else Indent.getNoneIndent())
                } else null })


            if (fData is PsiElement) {
                // The function's data has to be lifted to the granularity the block tree works at: the
                // single child of this node that contains it. There may be none -- a meta resolver builds
                // its result through `ConcreteFactoryImpl(data)`, stamping one PSI element on a whole
                // synthesized subtree, so an application can carry the data of one of its own arguments
                // and thus span the entire node. `getBounds` has tolerated that since 354850db8 ("f may be
                // empty if neither of aaeBlocks corresponds"); this copy of the same lookup must too. The
                // lost-blocks search below then covers whatever child is left uncovered.
                val f = aaeBlocks.filter { it.textRange.contains(fData.node.textRange) }
                if (f.size == 1) {
                    val fBlock = createArendBlock(f.first(), null, null, if (isPrefix) Indent.getNoneIndent() else Indent.getNormalIndent())
                    if (!blocks.any { it.textRange.contains(fBlock.textRange) })
                        blocks.add(fBlock)
                } else {
                    LOG.warn("No single child block holds the function of $cExpr in ${node.elementType} at ${node.textRange}")
                }
            }

            blocks.sortBy { it.textRange.startOffset }

            // Dedicated search for "lost" blocks
            val lostBlocks = aaeBlocks.asSequence().sortedBy { it.startOffset }.toMutableList()
            for (block in blocks) {
                val toRemove = ArrayList<ASTNode>()
                for (aae in lostBlocks) {
                    if (block.textRange.contains(aae.textRange)) toRemove.add(aae)
                    if (aae.startOffset > block.textRange.endOffset) break
                }
                lostBlocks.removeAll(toRemove)
            }

            if (lostBlocks.isNotEmpty()) {
                for (lostBlock in lostBlocks) { // Remove BinOpParser blocks and replace them with containing lost blocks
                    val toRemove = ArrayList<Block>()
                    for (block in blocks) if (lostBlock.textRange.contains(block.textRange)) toRemove.add(block)
                    blocks.removeAll(toRemove.toSet())
                }


                for (lostBlock in lostBlocks) //Lost blocks that were not in BinOpParser output
                    blocks.add(createArendBlock(lostBlock, null, null, Indent.getNoneIndent()))
            }

            blocks.sortBy { it.textRange.startOffset }


            var segmentEnd = -1
            val oddBlocks = ArrayList<Block>()
            for (block in blocks) {
                if (block.textRange.endOffset == segmentEnd)
                    oddBlocks.add(block)
                if (block.textRange.endOffset < segmentEnd) {
                    // Intersecting children are the one thing the platform must not be handed: it garbles
                    // the text on reformat. Give up on the parsed structure for this node instead.
                    reportMalformedConcrete("blocks intersect")
                    return plainBlocks(aaeBlocks, align, indent)
                }
                segmentEnd = block.textRange.endOffset
            }
            blocks.removeAll(oddBlocks)

            return GroupBlock(settings, blocks, null, align, indent, this)
        } else if (cExpr is Concrete.LamExpression) { // we are dealing with right sections
            return GroupBlock(settings, aaeBlocks.map { createArendBlock(it, null, null, Indent.getNoneIndent()) }.toMutableList(), null, align, indent, this)
        } else if (cExprData is PsiElement) {
            val blocks = aaeBlocks.map { createArendBlock(it, null, align, indent) as Block }.toMutableList()

            if (aaeBlocks.size == 1)
                return blocks.first() as AbstractArendBlock else {
                return GroupBlock(settings, blocks, null, align, indent, this)
            }
        }
        reportMalformedConcrete("unexpected concrete expression ${cExpr.javaClass.simpleName}")
        return plainBlocks(aaeBlocks, align, indent)
    }

    /**
     * Formats [aaeBlocks] as they are, making no structural claim about them. Trivially satisfies what
     * the platform requires of a child list -- ordered, non-overlapping, inside the parent -- and is the
     * shape the right-section branch above already builds.
     */
    private fun plainBlocks(aaeBlocks: List<ASTNode>, align: Alignment?, indent: Indent): AbstractArendBlock {
        val blocks = aaeBlocks.map { createArendBlock(it, null, null, Indent.getNoneIndent()) as Block }.toMutableList()
        return if (blocks.size == 1) blocks.first() as AbstractArendBlock
        else GroupBlock(settings, blocks, null, align, indent, this)
    }

    /**
     * The concrete tree cannot be mapped onto this node's children at all. Unlike a function without a
     * single containing child, which is a supported consequence of how metas build concrete, this should
     * not happen -- so it is an error in tests, where it fails `ArendReformatTest` and the arend-lib
     * stress test, and only a log line for users, who get unaligned but otherwise correct formatting.
     */
    private fun reportMalformedConcrete(reason: String) {
        val message = "Cannot build blocks for ${node.elementType} at ${node.textRange}: $reason ($cExpr)"
        if (ApplicationManager.getApplication().isUnitTestMode) LOG.error(message) else LOG.warn(message)
    }
}

private val LOG = Logger.getInstance(ArgumentAppExprBlock::class.java)