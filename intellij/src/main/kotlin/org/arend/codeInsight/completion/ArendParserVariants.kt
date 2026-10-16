package org.arend.codeInsight.completion

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.lang.parser.GeneratedParserUtilBase.ErrorState
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILazyParseableElementType
import org.arend.ArendLanguage
import org.arend.lexer.ArendLexerAdapter
import org.arend.parser.ArendParser
import org.arend.parser.ArendParserDefinition
import org.arend.psi.stubs.ArendFileStub

/** A grammar rule which was being parsed when the parser asked for a token */
class RuleFrame(val type: IElementType, val offset: Int)

/**
 * A token which the parser would accept at the completion offset.
 *
 * [rules] are the enclosing grammar rules, the innermost first; only public rules are listed.
 * [afterError] is set when the token is asked for after the parser has already reported an error at the offset,
 * that is, while it recovers from an error, so the token would not make the text correct.
 */
class ParserVariant(val text: String, val rules: List<RuleFrame>, val afterError: Boolean) {
    fun hasRule(vararg types: IElementType) = rules.any { it.type in types }

    /** The innermost rule of one of [types] */
    fun innermost(vararg types: IElementType) = rules.firstOrNull { it.type in types }

    /** The rule which encloses [rule] directly */
    fun outer(rule: RuleFrame) = rules.getOrNull(rules.indexOf(rule) + 1)
}

/** The result of parsing a text: the tokens which the parser tried at the completion offset and the tree it built */
class ParserVariants(val variants: List<ParserVariant>, val tree: ASTNode) {
    /** The node built for [rule] */
    fun node(rule: RuleFrame): ASTNode? {
        var node: ASTNode? = tree
        while (node != null) {
            if (node.elementType == rule.type && node.startOffset == rule.offset) return node
            node = children(node).lastOrNull { it.startOffset <= rule.offset }
        }
        return null
    }
}

/**
 * The children of [node] except lazily parsed ones, that is, documentation comments.
 * Such a comment is parsed in the context of the PSI of its parent, which the trees built here do not have.
 */
private fun children(node: ASTNode) = node.getChildren(null).filter { it.elementType !is ILazyParseableElementType }

object ArendParserVariants {
    private const val PUNCTUATION_MARK = "~"

    /** Parses [text] and returns the tokens which the parser tries at [offset] */
    fun collect(project: Project, text: CharSequence, offset: Int): ParserVariants {
        val state = VariantCollector(offset)
        val tree = parse(project, text, state)
        return ParserVariants(state.variants, tree)
    }

    /**
     * Parses [text] and returns the offsets of the errors.
     * The tree built by [collect] does not fit, since the parser skips some checks of the next token in the completion mode.
     */
    fun errorOffsets(text: CharSequence): List<Int> {
        val result = ArrayList<Int>()
        fun visit(node: ASTNode) {
            if (node.elementType == TokenType.ERROR_ELEMENT) result.add(node.startOffset)
            for (child in children(node)) visit(child)
        }
        visit(parse(null, text, null))
        return result
    }

    private fun parse(project: Project?, text: CharSequence, state: GeneratedParserUtilBase.CompletionState?): ASTNode {
        val builder = PsiBuilderFactory.getInstance().createBuilder(ArendParserDefinition(), ArendLexerAdapter(), text)
        if (project != null && state != null) {
            // The parser takes the completion state from the file it parses
            val stateHolder = PsiFileFactory.getInstance(project).createFileFromText("variants.ard", ArendLanguage.INSTANCE, "")
            stateHolder.putUserData(GeneratedParserUtilBase.COMPLETION_STATE_KEY, state)
            builder.putUserData(FileContextUtil.CONTAINING_FILE_KEY, stateHolder)
        }
        return ArendParser().parse(ArendFileStub.Type, builder)
    }

    private class VariantCollector(offset: Int) : GeneratedParserUtilBase.CompletionState(offset) {
        val variants = ArrayList<ParserVariant>()

        private var afterError = false

        // The parser drops one-character variants, but punctuation is needed to compare grammar positions
        override fun convertItem(o: Any?): String? {
            // A sequence of tokens is expected; only the first one is at the offset
            if (o is Array<*>) return o.firstOrNull()?.let { convertItem(it) }
            val text = super.convertItem(o)
            return if (o is IElementType && text?.length == 1) PUNCTUATION_MARK + text else text
        }

        override fun prefixMatches(builder: PsiBuilder, text: String) = builder.currentOffset == offset

        override fun addItem(builder: PsiBuilder, item: String) {
            val text = item.removePrefix(PUNCTUATION_MARK)
            val rules = ArrayList<RuleFrame>()
            var frame = ErrorState.get(builder).currentFrame
            while (frame != null) {
                // Once an error is reported at the offset, everything after it is error recovery
                if (frame.errorReportedAt != -1 && frame.errorReportedAt == builder.rawTokenIndex()) afterError = true
                val type = frame.elementType
                if (type != null) rules.add(RuleFrame(type, frame.offset))
                frame = frame.parentFrame
            }
            variants.add(ParserVariant(text, rules, afterError))
        }
    }
}
