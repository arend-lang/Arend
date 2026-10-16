package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiComment
import com.intellij.psi.util.elementType
import org.arend.codeInsight.completion.ArendReplCompletionContributor.Companion.getReplCommand
import org.arend.psi.ArendElementTypes.NUMBER
import org.arend.psi.ArendElementTypes.REPL_COMMAND
import org.arend.psi.ArendFile
import org.arend.repl.CommandHandler
import org.arend.search.ArendWordScanner

class ArendCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val file = parameters.originalFile
        val text = file.viewProvider.contents

        if (parameters.position.elementType == REPL_COMMAND) {
            addKeywords(result, completedWord(parameters, result), CommandHandler.INSTANCE.commandMap.keys.map { KeywordItem(it, KeywordInsertion.SPACE) })
        }

        if (file is ArendFile && file.isRepl && CommandHandler.INSTANCE.commandMap.contains(getReplCommand(file))) return
        if (parameters.position is PsiComment) return

        val word = completedWord(parameters, result)
        val start = parameters.offset - word.length
        if (start > 0 && text[start - 1] == '.') return // after a field access

        addKeywords(result, word, ArendKeywords.complete(file, start, parameters.offset))

        // The rest of a truncated universe after its level: \1 completes to \1-Type
        val position = parameters.originalPosition
        val parent = position?.parent
        if (parent != null && parent.text == "\\" && parent.nextSibling?.node?.elementType == NUMBER) {
            addKeywords(result, "", listOf(KeywordItem("-Type", KeywordInsertion.PLAIN)))
        }
        if (position?.text?.matches(TRUNCATION_LEVEL_PREFIX) == true) {
            addKeywords(result, word.replace(TRUNCATION_LEVEL, ""), listOf(KeywordItem("Type", KeywordInsertion.PLAIN)))
        }
    }

    companion object {
        const val KEYWORD_PRIORITY = 0.0

        private val TRUNCATION_LEVEL = Regex("\\\\[0-9]+-?")
        private val TRUNCATION_LEVEL_PREFIX = Regex("\\\\[0-9]+(-(T(y(pe?)?)?)?)?")

        /** The completed word: an identifier, possibly preceded by a backslash */
        private fun completedWord(parameters: CompletionParameters, result: CompletionResultSet): String {
            var word = result.prefixMatcher.prefix
            val lastInvalidIndex = word.indexOfLast { !ArendWordScanner.isArendIdentifierPart(it) }
            if (lastInvalidIndex >= 0) word = word.substring(lastInvalidIndex + 1)
            val pos = parameters.offset - word.length - 1
            val text = parameters.originalFile.viewProvider.contents
            if (pos >= 0 && pos < text.length && text[pos] == '\\') word = "\\" + word
            return word
        }

        private fun addKeywords(result: CompletionResultSet, prefix: String, items: List<KeywordItem>) {
            if (items.isEmpty()) return
            val matcher = result.withPrefixMatcher(object : PlainPrefixMatcher(prefix) {
                override fun prefixMatches(name: String): Boolean = isStartMatch(name)
            })
            for (item in items) {
                matcher.addElement(PrioritizedLookupElement.withPriority(lookupElement(item), KEYWORD_PRIORITY))
            }
        }

        fun lookupElement(item: KeywordItem): LookupElement {
            var element = LookupElementBuilder.create(item.lookupString)
            if (item.insertion == KeywordInsertion.BRACES) element = element.withPresentableText(item.keyword)
            return element.withInsertHandler(insertHandler(item.insertion))
        }

        private fun insertHandler(insertion: KeywordInsertion) = InsertHandler<LookupElement> { context, _ ->
            val document = context.document
            when (insertion) {
                KeywordInsertion.PLAIN -> {}
                KeywordInsertion.SPACE -> document.insertString(context.tailOffset, " ")
                KeywordInsertion.BRACES -> {
                    context.commitDocument()
                    context.editor.caretModel.moveToOffset(context.tailOffset - 1)
                    return@InsertHandler
                }
                KeywordInsertion.TRUNCATION_LEVEL -> {
                    // Replaces n with 1 and selects it, so that the keyword is highlighted correctly
                    document.insertString(context.tailOffset, " ")
                    context.commitDocument()
                    document.replaceString(context.startOffset + 1, context.startOffset + 2, "1")
                    context.editor.caretModel.moveToOffset(context.startOffset + 1)
                    context.editor.selectionModel.setSelection(context.startOffset + 1, context.startOffset + 2)
                    return@InsertHandler
                }
            }
            context.commitDocument()
            context.editor.caretModel.moveToOffset(context.tailOffset)
        }
    }
}
