package org.arend.formatting.block

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.TokenType
import com.intellij.psi.TokenType.WHITE_SPACE
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.common.AbstractBlock
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import java.util.ArrayList

class SimpleArendBlock(node: ASTNode, settings: CommonCodeStyleSettings?, wrap: Wrap?, alignment: Alignment?, myIndent: Indent?) :
        AbstractArendBlock(node, settings, wrap, alignment, myIndent) {

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        if (myNode.psi is ArendFunctionClauses) {
            val spacing = SpacingImpl(0, 0, 1, false, false, false, 1, false, 1)
            if (child2 is SimpleArendBlock && child2.node.elementType == PIPE)
                return spacing
        }

        if (myNode.psi is ArendFunctionBody) {
            val spacingCRLF = SpacingImpl(1, 1, 0, false, true, true, 0, false, 1)
            if (child1 is AbstractArendBlock && child1.node.elementType == ArendElementTypes.FAT_ARROW) return spacingCRLF
            return super.getSpacing(child1, child2)
        }

        if (myNode.psi is ArendDefFunction) {
            val spacingFA = SpacingImpl(1, 1, 0, false, true, false, 0, false, 0)
            val spacingColon = SpacingImpl(1, 1, 0, false, true, true, 0, false, 0)
            if (child2 is AbstractArendBlock && child2.node.elementType == FUNCTION_BODY) {
                val child1node = (child1 as? AbstractArendBlock)?.node
                val child2node = (child2 as? AbstractArendBlock)?.node?.psi as? ArendFunctionBody
                if (child1node != null && child2node != null &&
                        child1node.elementType != LINE_COMMENT && child2node.fatArrow != null) return spacingFA
            } else if (child1 is AbstractArendBlock && child2 is AbstractArendBlock) {
                val child1et = child1.node.elementType
                val child2psi = child2.node.psi
                if (child1et == COLON && child2psi is ArendExpr) return spacingColon
            }
        }

        return null
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        printChildAttributesContext(newChildIndex)

        val nodePsi = node.psi

        if (node.elementType == STATEMENT) return ChildAttributes.DELEGATE_TO_PREV_CHILD

        if (node.elementType == TUPLE && subBlocks.size > 1 && newChildIndex == 1)
            return ChildAttributes(Indent.getNormalIndent(), null)

        if (node.elementType == CO_CLAUSE && subBlocks.size == newChildIndex)
            return ChildAttributes(indent, alignment)

        val prevChild = if (newChildIndex > 0 && newChildIndex - 1 < subBlocks.size) subBlocks[newChildIndex - 1] else null

        if (prevChild is AbstractArendBlock) {
            val prevET = prevChild.node.elementType

            if (nodePsi is ArendWhere) {
                if (prevET == STATEMENT && nodePsi.lbrace != null && nodePsi.rbrace != null) return ChildAttributes.DELEGATE_TO_PREV_CHILD
                if (prevET == WHERE_KW || prevET == LBRACE || prevET == TokenType.ERROR_ELEMENT) return ChildAttributes(Indent.getNormalIndent(), null)
            }

            // Definitions
            if (nodePsi is ArendDefClass) when (prevET) {
                DEF_IDENTIFIER, LONG_NAME -> return ChildAttributes(Indent.getNormalIndent(), null)
                WHERE -> return ChildAttributes(Indent.getNoneIndent(), null)
            }

            if (nodePsi is ArendDefData) when (prevET) {
                DEF_IDENTIFIER, UNIVERSE_EXPR, DATA_BODY -> return ChildAttributes(Indent.getNormalIndent(), null)
                WHERE -> return ChildAttributes(Indent.getNoneIndent(), null)
            }

            if (nodePsi is ArendDefFunction) return when (prevET) {
                FUNCTION_BODY -> ChildAttributes.DELEGATE_TO_PREV_CHILD
                WHERE -> ChildAttributes(Indent.getNoneIndent(), null)
                else -> ChildAttributes(Indent.getNormalIndent(), null)
            }

            if (nodePsi is ArendDefInstance) when (prevChild.node.psi) {
                is ArendExpr -> return ChildAttributes(Indent.getNormalIndent(), null)
                is ArendCoClauses -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                is ArendWhere -> return ChildAttributes(Indent.getNoneIndent(), null)
            }

            // Data and function bodies
            if (nodePsi is ArendDataBody && prevChild.node.psi is ArendElim)
                return ChildAttributes(Indent.getNormalIndent(), null)

            if (nodePsi is ArendFunctionBody) {
                val prevBlock = subBlocks[newChildIndex - 1]
                val indent = if (prevBlock is AbstractArendBlock) {
                    val eT = prevBlock.node.elementType
                    if (prevBlock.node.psi is ArendExpr) return ChildAttributes.DELEGATE_TO_PREV_CHILD
                    when (eT) {
                        FAT_ARROW, COWITH_KW, ELIM, TokenType.ERROR_ELEMENT -> Indent.getNormalIndent()
                        FUNCTION_CLAUSES, CO_CLAUSES -> return ChildAttributes.DELEGATE_TO_PREV_CHILD
                        else -> Indent.getNoneIndent()
                    }
                } else Indent.getNoneIndent()
                return ChildAttributes(indent, null)
            }

            val indent = when (prevET) {
                LBRACE -> Indent.getNormalIndent()
                else -> prevChild.indent
            }

            return ChildAttributes(indent, prevChild.alignment)
        }

        return super.getChildAttributes(newChildIndex)
    }

    override fun buildChildren(): MutableList<Block> {
        val blocks = ArrayList<Block>()
        var child: ASTNode? = myNode.firstChildNode
        val alignment = Alignment.createAlignment()
        val alignment2 = Alignment.createAlignment()
        val nodeET = myNode.elementType

        mainLoop@ while (child != null) {
            if (child.elementType != WHITE_SPACE) {
                val childPsi = child.psi

                val indent: Indent? =
                        if (childPsi is ArendExpr || childPsi is PsiErrorElement || child.elementType == LINE_COMMENT) when (nodeET) {
                            CO_CLAUSE, LET_EXPR, LET_CLAUSE, CLAUSE, FUNCTION_BODY -> Indent.getNormalIndent()
                            PI_EXPR, SIGMA_EXPR, LAM_EXPR -> Indent.getContinuationIndent()
                            else -> Indent.getNoneIndent()
                        } else if (nodeET == DEF_FUNCTION) {
                            val notFBodyWithClauses = if (childPsi is ArendFunctionBody) childPsi.fatArrow != null else true
                            if ((blocks.size > 0) && notFBodyWithClauses) Indent.getNormalIndent() else Indent.getNoneIndent()
                        } else when (child.elementType) {
                            CO_CLAUSE, STATEMENT, CONSTRUCTOR_CLAUSE, WHERE, TUPLE_EXPR, CLASS_STAT -> Indent.getNormalIndent()
                            else -> Indent.getNoneIndent()
                        }

                val wrap: Wrap? =
                        if (nodeET == FUNCTION_BODY && childPsi is ArendExpr) Wrap.createWrap(WrapType.NORMAL, false) else null

                val align = when (myNode.elementType) {
                    LET_EXPR -> when (child.elementType) {
                        LET_KW, IN_KW -> alignment2
                        LINE_COMMENT -> alignment
                        else -> null
                    }
                    else -> when (child.elementType) {
                        CO_CLAUSE -> alignment
                        NAME_TELE, TYPE_TELE -> alignment2
                        else -> null
                    }
                }

                if (child.elementType == PIPE) when (nodeET) {
                    FUNCTION_CLAUSES, LET_EXPR, DATA_BODY, CONSTRUCTOR, DEF_CLASS, CASE_EXPR -> {
                        val clauseGroup = findClauseGroup(child, null)
                        if (clauseGroup != null) {
                            child = clauseGroup.first.treeNext
                            blocks.add(GroupBlock(myNode, settings, clauseGroup.second, null, alignment, Indent.getNormalIndent()))
                            continue@mainLoop
                        }
                    }
                }

                blocks.add(createArendBlock(child, wrap, align, indent))
            }
            child = child.treeNext
        }
        return blocks
    }

    private fun findClauseGroup(child: ASTNode, childAlignment: Alignment?): Pair<ASTNode, MutableList<Block>>? {
        var currChild: ASTNode? = child
        val groupNodes = ArrayList<Block>()
        while (currChild != null) {
            if (currChild.elementType != WHITE_SPACE) groupNodes.add(createArendBlock(currChild, null, childAlignment, Indent.getNoneIndent()))
            when (currChild.elementType) {
                CLAUSE, LET_CLAUSE, CONSTRUCTOR, CLASS_FIELD -> return Pair(currChild, groupNodes)
            }
            currChild = currChild.treeNext
        }
        return null
    }
}