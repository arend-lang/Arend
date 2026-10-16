package org.arend.codeInsight.completion

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import org.arend.lexer.ArendLexerAdapter
import org.arend.psi.AREND_COMMENTS
import org.arend.psi.AREND_WHITE_SPACES
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ArendFile

/** How a keyword is inserted */
enum class KeywordInsertion { PLAIN, SPACE, BRACES, TRUNCATION_LEVEL }

class KeywordItem(val keyword: String, val insertion: KeywordInsertion) {
    val lookupString: String
        get() = if (insertion == KeywordInsertion.BRACES) "$keyword {}" else keyword
}

/**
 * Keyword completion driven by the grammar.
 *
 * The parser itself tells which tokens it accepts at the caret (see [ArendParserVariants]),
 * so the grammar is the only description of where a keyword may appear.
 * On top of that, [isAllowed] lists the restrictions which the grammar does not express,
 * and [conflictsWithTail] drops the keywords which do not fit the text after the caret.
 */
object ArendKeywords {
    /**
     * Returns the keywords which may be inserted in [file] in place of the text between [start] and [caret].
     * The text after the caret is taken into account only if [useTail] is set.
     */
    fun complete(file: PsiFile, start: Int, caret: Int, useTail: Boolean = true): List<KeywordItem> {
        val context = KeywordContext(file, start, caret, useTail)
        val parsed = ArendParserVariants.collect(file.project, context.parsedText(SENTINEL), context.offset)
        val items = LinkedHashMap<String, KeywordItem>()
        for (variant in parsed.variants) {
            val keyword = keywordText(variant.text) ?: continue
            if (!context.isAllowed(variant, parsed)) continue
            val item = context.item(variant, keyword)
            if (item.lookupString in items || context.conflictsWithTail(item, variant, parsed)) continue
            items[item.lookupString] = item
        }
        return items.values.toList()
    }

    /** Inserted after the text before the caret when there is nothing after it, so that the parser does not stop at the end of the file */
    private const val SENTINEL = "}"

    private const val TRUNCATION_LEVEL_TYPE = "\\n-Type"

    private val UNIVERSES = mapOf(UNIVERSE.toString() to "\\Type", SET.toString() to "\\Set",
        CAT_UNIVERSE.toString() to "\\Cat", TRUNCATED_UNIVERSE.toString() to TRUNCATION_LEVEL_TYPE)

    private val UNIVERSE_KEYWORDS = UNIVERSES.values.toSet() + listOf(PROP_KW.toString(), SET_KW.toString())

    private fun keywordText(token: String) = if (token.startsWith("\\")) token else UNIVERSES[token]

    private val SKIPPED_TOKENS = TokenSet.orSet(AREND_WHITE_SPACES, AREND_COMMENTS)

    /** The rules whose parameters may be eliminated */
    private val ELIMINATING_RULES = arrayOf(DEF_FUNCTION, DEF_DATA, DEF_INSTANCE, CONSTRUCTOR, CO_CLAUSE_DEF)

    private class Token(val type: IElementType, val start: Int, val end: Int)

    private class KeywordContext(val file: PsiFile, start: Int, caret: Int, useTail: Boolean) {
        val project: Project = file.project

        /** Expressions are parsed as REPL lines in REPL files and code fragments */
        val expressionsOnly = file is PsiCodeFragment || (file as? ArendFile)?.isRepl == true

        /** The text before the completed word, starting at a top level statement */
        val before: CharSequence

        val tail: CharSequence

        private val separator: String

        /** The completion offset in the parsed text */
        val offset: Int

        private val beforeTokens: List<Token>

        /** The last token before the completed word */
        val prevToken: IElementType?

        /** The first token after the caret */
        val nextToken: Token?

        /** The number of line breaks between [prevToken] and the completed word */
        val lineBreaks: Int

        /** Checks if a part of the completed word is already typed */
        private val wordTyped = start < caret

        init {
            val text = file.viewProvider.contents
            val anchor = if (expressionsOnly) 0 else statementStart(file.node, text, start)
            before = text.subSequence(anchor, start)
            // The rest of the completed word is replaced with the keyword; the names of keywords consist of letters
            var tailStart = caret
            if (wordTyped) while (tailStart < text.length && text[tailStart].isLetterOrDigit()) tailStart++
            tail = if (useTail) text.subSequence(tailStart, minOf(text.length, tailStart + MAX_TAIL_LENGTH)) else ""
            separator = if (before.isEmpty() || before.last().isWhitespace()) "" else " "
            offset = before.length + separator.length

            beforeTokens = tokens(before)
            val prev = beforeTokens.lastOrNull()
            prevToken = prev?.type
            lineBreaks = if (prev == null) 1 else before.subSequence(prev.end, before.length).count { it == '\n' }
            // The text after the caret is lexed in its context: for instance, a colon at the beginning of the text is a REPL command
            nextToken = tokens(parsedText(tail)).firstOrNull { it.start >= offset }?.let { Token(it.type, it.start - offset, it.end - offset) }
        }

        fun parsedText(insertion: CharSequence) = buildString {
            append(before)
            append(separator)
            append(insertion)
        }

        private fun tokens(text: CharSequence): List<Token> {
            val lexer = ArendLexerAdapter()
            lexer.start(text)
            val result = ArrayList<Token>()
            while (true) {
                val type = lexer.tokenType ?: break
                if (!SKIPPED_TOKENS.contains(type)) result.add(Token(type, lexer.tokenStart, lexer.tokenEnd))
                lexer.advance()
            }
            return result
        }

        /** Checks if the variant begins a new statement */
        private fun beginsStatement(variant: ParserVariant) =
            variant.rules.any { (it.type == STAT || it.type == CLASS_STAT) && it.offset == offset }

        fun isAllowed(variant: ParserVariant, parsed: ParserVariants): Boolean {
            if (variant.hasRule(REPL_LINE) != expressionsOnly) return false

            if (beginsStatement(variant)) {
                if (expressionsOnly) return false
                // A statement begins on a new line; after an error, only after an empty line
                if (variant.afterError) {
                    if (lineBreaks < 2) return false
                } else if (lineBreaks < 1 && prevToken != LBRACE) return false
                // A statement cannot follow an unfinished type or definition
                if (prevToken in UNFINISHED) return false
                if (splitsStatement(variant)) return false
                // Class fields go before the other statements
                if (nextToken?.type == PIPE && variant.innermost(CLASS_STAT)?.offset == offset &&
                    (variant.rules.any { (it.type == DEFINITION || it.type == DEF_MODULE) && it.offset == offset } || variant.text == USE_KW.toString())) return false
            } else if (variant.afterError || lineBreaks >= 2) return false

            // \in is followed by an expression
            if (prevToken == IN_KW && !beginsExpression(variant)) return false
            if (isUniverseArgument(variant)) return false
            // { followed by a new line begins the body of a class rather than its implicit field
            if (prevToken == LBRACE && lineBreaks > 0 && variant.innermost(FIELD_TELE) != null) return false
            if (isIncompleteInstance(variant, parsed)) return false

            val token = variant.text
            return when {
                keywordText(token) in UNIVERSE_KEYWORDS -> prevToken !in NO_UNIVERSES_AFTER && !isLevelOf(variant, parsed)
                // \where cannot be inserted in the middle of the definition
                token == WHERE_KW.toString() -> prevToken !in MODIFIERS && !continuesOwner(variant)
                // \in ends the clauses of \let, so the expression of the last clause cannot continue after it
                token == IN_KW.toString() -> wordTyped || nextVariant?.innermost(LET_CLAUSE, LET_EXPR)?.type != LET_CLAUSE
                // A field begins with | or \field
                (token == CLASSIFYING_KW.toString() || token == COERCE_KW.toString()) &&
                        variant.innermost(CLASS_FIELD, FIELD_TELE)?.type == CLASS_FIELD && prevToken !in FIELD_STARTS -> false
                token == IMPORT_KW.toString() -> !variant.hasRule(WHERE, DEF_CLASS)
                token == USE_KW.toString() -> variant.innermost(WHERE, CLASS_STAT)?.type == WHERE &&
                        nextToken.let { it == null || it.type == COERCE_KW || it.type == LEVEL_KW || it.type !in DEFINITION_KEYWORDS }
                prevToken == USE_KW -> token == LEVEL_KW.toString() ||
                        token == COERCE_KW.toString() && whereOwner(variant)?.type.let { it == DEF_DATA || it == DEF_CLASS }
                // \coerce and \level begin definitions only after \use
                variant.innermost(FUNCTION_KW) != null && (token == COERCE_KW.toString() || token == LEVEL_KW.toString()) -> false
                // The precedence of a \use function is meaningless
                variant.innermost(PREC)?.let { variant.outer(it)?.type } == DEF_FUNCTION && (prevToken == COERCE_KW || prevToken == LEVEL_KW) -> false
                token == THIS_KW.toString() -> variant.hasRule(CLASS_STAT, CLASS_IMPLEMENT)
                token == ELIM_KW.toString() || token == WITH_KW.toString() && variant.innermost(ELIM) != null ->
                    variant.innermost(*ELIMINATING_RULES)?.let { parsed.node(it) }?.let { hasParameters(it) } ?: true
                token == COWITH_KW.toString() ->
                    variant.innermost(DEF_FUNCTION, DEF_INSTANCE, CO_CLAUSE_DEF)?.let { parsed.node(it) }?.let { hasResultType(it) } ?: true
                token == CLASSIFYING_KW.toString() -> variant.innermost(DEF_CLASS)?.let { parsed.node(it) }?.let { mayHaveClassifyingField(it) } ?: true
                token == COERCE_KW.toString() && variant.innermost(CONSTRUCTOR)?.let { variant.outer(it)?.type } == CONSTRUCTOR_CLAUSE -> false
                token == PRIVATE_KW.toString() || token == PROTECTED_KW.toString() -> prevToken != FIELD_KW && prevToken != PROPERTY_KW
                // Parameters of \Pi cannot be strict or properties, parameters of \Sigma cannot be strict
                token == STRICT_KW.toString() || token == PROPERTY_KW.toString() && variant.innermost(TYPE_TELE, NAME_TELE) != null -> {
                    val owner = variant.innermost(TYPE_TELE, NAME_TELE)?.let { variant.outer(it)?.type }
                    owner != PI_EXPR && !(owner == SIGMA_EXPR && token == STRICT_KW.toString())
                }
                else -> true
            }
        }

        /** Checks if a statement which begins with the variant would split the statement which the text after the caret continues */
        private fun splitsStatement(variant: ParserVariant): Boolean {
            // When a word is being typed, the text after it is not the continuation of the previous statement
            if (wordTyped) return false
            val next = nextVariant?.takeIf { !it.afterError } ?: return false
            val statement = variant.rules.first { (it.type == STAT || it.type == CLASS_STAT) && it.offset == offset }
            val container = variant.outer(statement)
            return next.rules.any { rule ->
                (rule.type == STAT || rule.type == CLASS_STAT) && rule.offset < offset &&
                        next.outer(rule).let { it?.type == container?.type && it?.offset == container?.offset }
            }
        }

        /** Checks if the text after the caret continues the definition which \where of the variant would end */
        private fun continuesOwner(variant: ParserVariant): Boolean {
            if (wordTyped) return false
            val owner = whereOwner(variant) ?: return false
            val next = nextVariant?.takeIf { !it.afterError } ?: return false
            return next.rules.any { it.type == owner.type && it.offset == owner.offset } && next.innermost(WHERE)?.let { next.outer(it) }?.offset != owner.offset
        }

        /** Checks if the variant is an argument of a universe, which cannot be applied */
        private fun isUniverseArgument(variant: ParserVariant): Boolean {
            val argument = variant.innermost(ARGUMENT, WITH_BODY) ?: return false
            val application = variant.innermost(NEW_EXPR)?.takeIf { variant.rules.indexOf(it) > variant.rules.indexOf(argument) } ?: return false
            return beforeTokens.firstOrNull { it.start == application.offset }?.type in UNIVERSE_TOKENS
        }

        private fun beginsExpression(variant: ParserVariant) =
            variant.rules.any { it.offset == offset && it.type in EXPRESSIONS }

        /** Checks if the variant is an argument of \level in a result type */
        private fun isLevelOf(variant: ParserVariant, parsed: ParserVariants): Boolean {
            val rule = variant.innermost(RETURN_EXPR, TUPLE_EXPR)?.takeIf { it.type == RETURN_EXPR } ?: return false
            val returnExpr = parsed.node(rule) ?: return false
            return returnExpr.firstChildNode?.elementType.let { it == LEVEL_KW || it == LEVEL_PLUS_KW }
        }

        private fun whereOwner(variant: ParserVariant) = variant.innermost(WHERE)?.let { variant.outer(it) }

        /** An instance without a type can be followed only by its parameters and its type */
        private fun isIncompleteInstance(variant: ParserVariant, parsed: ParserVariants): Boolean {
            val instance = variant.innermost(DEF_INSTANCE) ?: return false
            val part = variant.rules.getOrNull(variant.rules.indexOf(instance) - 1) ?: return false
            if (part.type != INSTANCE_BODY && part.type != WHERE) return false
            return parsed.node(instance)?.findChildByType(RETURN_EXPR) == null
        }

        private fun hasParameters(node: ASTNode) =
            node.getChildren(TokenSet.create(NAME_TELE, TYPE_TELE)).isNotEmpty()

        private fun hasResultType(node: ASTNode): Boolean {
            val type = node.findChildByType(RETURN_EXPR) ?: return false
            return type.findChildByType(TokenSet.create(LEVEL_KW, LEVEL_PLUS_KW)) == null
        }

        /** A class may have only one classifying field; records have none */
        private fun mayHaveClassifyingField(defClass: ASTNode): Boolean {
            if (defClass.firstChildNode?.elementType != CLASS_KW) return false
            fun hasClassifying(node: ASTNode): Boolean = node.getChildren(null).any {
                it.elementType == CLASSIFYING_KW || it.elementType == NO_CLASSIFYING_KW ||
                        it.elementType in CLASS_PARTS && hasClassifying(it)
            }
            return !hasClassifying(defClass)
        }

        fun item(variant: ParserVariant, keyword: String): KeywordItem {
            val insertion = when {
                keyword == TRUNCATION_LEVEL_TYPE -> KeywordInsertion.TRUNCATION_LEVEL
                keyword in UNIVERSE_KEYWORDS -> KeywordInsertion.PLAIN
                keyword == WHERE_KW.toString() -> KeywordInsertion.BRACES
                keyword == WITH_KW.toString() -> {
                    val elim = variant.innermost(ELIM)
                    if (elim == null || variant.outer(elim)?.type == CO_CLAUSE_BODY) KeywordInsertion.BRACES else KeywordInsertion.PLAIN
                }
                keyword == COWITH_KW.toString() -> KeywordInsertion.PLAIN
                else -> KeywordInsertion.SPACE
            }
            // \truncated is always followed by \data
            if (keyword == TRUNCATED_KW.toString() && nextToken?.type != DATA_KW) return KeywordItem("$TRUNCATED_KW $DATA_KW", insertion)
            return KeywordItem(keyword, insertion)
        }

        /** The token after the caret and the rules in which the parser reads it */
        private val nextVariant: ParserVariant? by lazy {
            val next = nextToken ?: return@lazy null
            val text = next.type.toString()
            ArendParserVariants.collect(project, parsedText(tail), offset + next.start).variants.lastOrNull { it.text == text }
        }

        /**
         * Checks if [item] does not fit the text after the caret.
         *
         * This is the case when the next token belongs to the same grammar rule as the keyword,
         * but takes a position in it which precedes the position of the keyword or coincides with it,
         * and the text gets an error when the keyword is inserted.
         */
        fun conflictsWithTail(item: KeywordItem, variant: ParserVariant, parsed: ParserVariants): Boolean {
            val next = nextToken ?: return false
            val nextVariant = nextVariant ?: return false
            val owner = variant.rules.firstOrNull { rule ->
                rule.offset < offset && nextVariant.rules.any { it.type == rule.type && it.offset == rule.offset }
            } ?: return false
            val keywordPartRule = variant.rules.getOrNull(variant.rules.indexOf(owner) - 1)
            val keywordPart = keywordPartRule?.type
            val nextPart = nextVariant.rules.getOrNull(nextVariant.rules.indexOfFirst { it.type == owner.type && it.offset == owner.offset } - 1)?.type

            val precedes = if (keywordPartRule != null && keywordPartRule.offset < offset && keywordPart == nextPart) {
                // The keyword continues an element which began before the caret, and the next token begins another one
                false
            } else if (keywordPart != null && keywordPart == nextPart) {
                // A statement may be followed by another one, unless it is the only statement of \where without braces
                keywordPart != STAT && keywordPart != CLASS_STAT || owner.type == WHERE && prevToken == WHERE_KW
            } else if (keywordPart == null && nextPart == null && variant.text == nextVariant.text) {
                true
            } else {
                val nextIndex = parsed.variants.indexOfFirst { other ->
                    val index = other.rules.indexOfFirst { it.type == owner.type && it.offset == owner.offset }
                    index >= 0 && (if (nextPart == null) index == 0 && other.text == nextVariant.text else other.rules.getOrNull(index - 1)?.type == nextPart)
                }
                nextIndex >= 0 && nextIndex < parsed.variants.indexOf(variant)
            }
            if (!precedes) return false

            // The innermost construct which contains the next token and begins before the caret
            val construct = nextVariant.rules.first { it.offset < offset }
            val keyword = if (item.keyword == TRUNCATION_LEVEL_TYPE) "\\Type" else item.keyword
            return !nextTokenFits("$keyword ", next, owner, nextPart, construct) && !nextTokenFits("$keyword $DUMMY_NAME ", next, owner, nextPart, construct)
        }

        /**
         * Checks if [next] can follow [insertion] inserted at the caret.
         * It fits if the parser reads it without errors in the same [construct] as before, outside of the inserted one,
         * or inside it, but in the same part of [owner] as before.
         */
        private fun nextTokenFits(insertion: String, next: Token, owner: RuleFrame, nextPart: IElementType?, construct: RuleFrame): Boolean {
            val nextOffset = offset + insertion.length + next.start
            val nextText = next.type.toString()
            val text = parsedText(insertion) + tail
            val nextVariant = ArendParserVariants.collect(project, text, nextOffset).variants.lastOrNull { it.text == nextText }
            if (nextVariant == null) {
                val end = nextOffset + next.end - next.start
                return ArendParserVariants.errorOffsets(text).none { it in offset..end }
            }
            if (nextVariant.afterError || nextVariant.rules.none { it.type == construct.type && it.offset == construct.offset }) return false
            val ownerIndex = nextVariant.rules.indexOfFirst { it.type == owner.type && it.offset == owner.offset }
            if (ownerIndex < 0) return false
            if (nextVariant.rules.none { it.offset == offset }) return true
            return ownerIndex > 0 && nextVariant.rules[ownerIndex - 1].type == nextPart
        }
    }

    private val CLASS_PARTS = TokenSet.create(FIELD_TELE, CLASS_STAT, CLASS_FIELD)

    private val EXPRESSIONS = setOf(EXPR, NEW_EXPR, ARR_EXPR, PI_EXPR, SIGMA_EXPR, LAM_EXPR, LET_EXPR, CASE_EXPR, APP_EXPR, APP_PREFIX,
        ARGUMENT_APP_EXPR, UNIVERSE_APP_EXPR, SET_UNIVERSE_APP_EXPR, TRUNCATED_UNIVERSE_APP_EXPR, CAT_UNIVERSE_APP_EXPR)

    private val UNIVERSE_TOKENS = setOf(UNIVERSE, SET, TRUNCATED_UNIVERSE, CAT_UNIVERSE, PROP_KW, SET_KW)

    private val DEFINITION_KEYWORDS = setOf(FUNC_KW, SFUNC_KW, LEMMA_KW, TYPE_KW, AXIOM_KW, COERCE_KW, LEVEL_KW, TRUNCATED_KW, DATA_KW,
        CLASS_KW, RECORD_KW, META_KW, INSTANCE_KW, CONS_KW)

    private val FIELD_STARTS = setOf(PIPE, FIELD_KW, PROPERTY_KW, PRIVATE_KW, PROTECTED_KW)

    /** Tokens which modify the following definition or field */
    private val MODIFIERS = setOf(PRIVATE_KW, PROTECTED_KW, USE_KW, COERCE_KW, CLASSIFYING_KW, FIELD_KW, PROPERTY_KW, TRUNCATED_KW)

    /** Tokens which are usually continued by the next line */
    private val UNFINISHED = setOf(SIGMA_KW, SIGMA_PLUS_KW, COWITH_KW)

    /** A universe is meaningless as the argument of these keywords */
    private val NO_UNIVERSES_AFTER = setOf(NEW_KW, EVAL_KW, PEVAL_KW, LEVEL_KW, LEVEL_PLUS_KW)

    /** Completes a keyword which is followed by a name */
    private const val DUMMY_NAME = "x"

    private const val MAX_TAIL_LENGTH = 300

    /**
     * The beginning of the top level statement which contains [offset].
     * Error recovery may start a statement in the middle of an incomplete one, so only statements at the beginning of a line are taken.
     */
    private fun statementStart(file: ASTNode, text: CharSequence, offset: Int): Int =
        file.getChildren(TokenSet.create(STAT)).lastOrNull {
            it.startOffset < offset && (it.startOffset == 0 || text[it.startOffset - 1] == '\n')
        }?.startOffset ?: 0
}
