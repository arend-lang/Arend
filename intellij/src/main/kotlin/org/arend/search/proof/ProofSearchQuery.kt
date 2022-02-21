package org.arend.search.proof

import com.intellij.util.SmartList
import java.util.regex.Pattern

data class ProofSearchQuery(val parameters: List<ProofSearchJointPattern>, val codomain: ProofSearchJointPattern) {
    companion object {
        fun fromString(pattern: String) : ParsingResult<ProofSearchQuery> = patternToQuery(pattern)
    }
    fun getAllIdentifiers() = (parameters + codomain).flatMap(ProofSearchJointPattern::getAllIdentifiers)

    override fun toString(): String {
        return parameters.joinToString(" -> ") { it.toString() } + (if (parameters.isEmpty()) "" else " --> ") + codomain.toString()
    }
}

@JvmInline
value class ProofSearchJointPattern(val patterns: List<PatternTree>) {
    fun getAllIdentifiers() = patterns.flatMap(PatternTree::getAllIdentifiers)

    override fun toString(): String {
        return patterns.joinToString("  \\and  ", "<", ">") { it.toString() }
    }
}

sealed interface PatternTree {
    enum class Implicitness {
        IMPLICIT, EXPLICIT;

        fun compactLBrace(): String = when (this) {
            IMPLICIT -> "{"
            EXPLICIT -> ""
        }

        fun compactRBrace(): String = when (this) {
            IMPLICIT -> "}"
            EXPLICIT -> ""
        }

        fun toBoolean(): Boolean = this == EXPLICIT
    }

    @JvmInline
    value class BranchingNode(val subNodes: List<Pair<PatternTree, Implicitness>>) : PatternTree {
        override fun getAllIdentifiers(): List<String> = subNodes.flatMap { it.first.getAllIdentifiers() }

        override fun toString(): String =
            subNodes.joinToString(" ", "[", "]", transform = { "${it.second.compactLBrace()}${it.first}${it.second.compactRBrace()}" })
    }

    @JvmInline
    value class LeafNode(val referenceName: List<String>) : PatternTree {
        override fun getAllIdentifiers(): List<String> = listOf(referenceName.last())

        override fun toString(): String = referenceName.joinToString(".")
    }

    object Wildcard : PatternTree {
        override fun getAllIdentifiers(): List<String> = emptyList()

        override fun toString(): String = "_"
    }

    fun getAllIdentifiers() : List<String>
}

private typealias Token = String

private fun patternToQuery(pattern: String): ParsingResult<ProofSearchQuery> {
    val tokens: List<Token> =
        Pattern.compile("""[^(){}\p{Space}]+|\(|\)|\{|}""").toRegex().findAll(pattern).map { it.value }.toList()
    return parseTokens(tokens)
}

sealed interface ParsingResult<out T> {

    data class OK<T>(val value: T) : ParsingResult<T>

    data class Error<T>(val message: String, val offset: Int) : ParsingResult<T> {
        @Suppress("UNCHECKED_CAST")
        fun <R> cast(): Error<R> = this as Error<R>
    }

    fun <U> bind(f: (T) -> ParsingResult<U>): ParsingResult<U> = when (this) {
        is OK -> f(value)
        is Error -> cast()
    }

    fun <U> map(f: (T) -> U): ParsingResult<U> = bind { OK(f(it)) }
}

private fun parseTokens(tokens: List<Token>): ParsingResult<ProofSearchQuery> {

    val braceMatchingResult = computeBraceMatching(tokens)
    if (braceMatchingResult is ParsingResult.Error) return braceMatchingResult.cast()
    val braceMatcher = (braceMatchingResult as ParsingResult.OK).value

    fun doParseTokens(position: Int, limit: Int): ParsingResult<PatternTree> {
        if (position == limit) return ParsingResult.Error("Unexpected end of input", position)
        val nodes: MutableList<Pair<PatternTree, PatternTree.Implicitness>> = mutableListOf()
        var currentPosition = position

        while (currentPosition != limit) {
            when (val token = tokens[currentPosition]) {
                "_" -> nodes.add(PatternTree.Wildcard to PatternTree.Implicitness.EXPLICIT)
                "(" -> {
                    val closingBrace = braceMatcher[currentPosition]
                    when (val result = doParseTokens(currentPosition + 1, closingBrace)) {
                        is ParsingResult.OK -> {
                            nodes.add(result.value to PatternTree.Implicitness.EXPLICIT)
                            currentPosition = closingBrace
                        }
                        is ParsingResult.Error -> return result
                    }
                }
                ")" -> return ParsingResult.Error("Unexpected ')'", currentPosition)
                "{" -> {
                    val closingBrace = braceMatcher[currentPosition]
                    when (val result = doParseTokens(currentPosition + 1, closingBrace)) {
                        is ParsingResult.OK -> {
                            nodes.add(result.value to PatternTree.Implicitness.IMPLICIT)
                            currentPosition = closingBrace
                        }
                        is ParsingResult.Error -> return result
                    }
                }
                "}" -> return ParsingResult.Error("Unexpected '}'", currentPosition)
                else -> nodes.add(PatternTree.LeafNode(token.split(".")) to PatternTree.Implicitness.EXPLICIT)
            }
            currentPosition += 1
        }
        return if (nodes.size == 1 && nodes[0].second != PatternTree.Implicitness.IMPLICIT) {
            ParsingResult.OK(nodes[0].first)
        } else {
            ParsingResult.OK(PatternTree.BranchingNode(nodes))
        }
    }

    val disjunctionBoundaries = tokens.split("->")

    val rawPatterns = disjunctionBoundaries.map { (start, end) ->
        tokens
            .subList(start, end)
            .split("\\and")
            .map { doParseTokens(start + it.first, start + it.second) }
            .swap()
            .map(::ProofSearchJointPattern)
    }
        .swap()
    return rawPatterns.map {
        ProofSearchQuery(it.subList(0, it.lastIndex), it.last())
    }
}

private fun <T> List<T>.split(t: T): List<Pair<Int, Int>> {
    val newList = SmartList<Pair<Int, Int>>(SmartList())
    var firstIndex = 0
    for (idx in this.indices) {
        if (this[idx] == t) {
            newList.add(firstIndex to idx)
            firstIndex = idx + 1
        }
    }
    newList.add(firstIndex to this.size)
    return newList
}

private fun <T> List<ParsingResult<T>>.swap() : ParsingResult<List<T>> {
    return fold(ParsingResult.OK(SmartList<T>()) as ParsingResult<SmartList<T>>) { collector, t -> collector.bind { list -> t.map { e -> list.also { it.add(e) } } }}
}

private fun computeBraceMatching(tokens: List<Token>): ParsingResult<Array<Int>> {
    val array = Array(tokens.size) { 0 }
    val stack = mutableListOf<Pair<Char, Int>>()
    for (idx in tokens.indices) {
        when (tokens[idx]) {
            "->" -> if (stack.isNotEmpty()) return ParsingResult.Error("'->' is allowed only on top level", idx)
            "\\and" -> if (stack.size > 1) return ParsingResult.Error("'\\and' is allowed only in clauses", idx)
            "(" -> stack.add(')' to idx)
            "{" -> stack.add('}' to idx)
            ")" -> {
                val (topBrace, topIndex) = stack.removeLastOrNull() ?: return ParsingResult.Error("Unexpected ')'", idx)
                if (topBrace != ')') return ParsingResult.Error("Unexpected ')'", idx)
                array[topIndex] = idx
            }
            "}" -> {
                val (topBrace, topIndex) = stack.removeLastOrNull() ?: return ParsingResult.Error("Unexpected '}'", idx)
                if (topBrace != '}') return ParsingResult.Error("Unexpected '}'", idx)
                array[topIndex] = idx
            }
        }
    }
    return if (stack.isNotEmpty()) {
        ParsingResult.Error("Could not find a matching brace", stack.first().second)
    } else {
        ParsingResult.OK(array)
    }
}