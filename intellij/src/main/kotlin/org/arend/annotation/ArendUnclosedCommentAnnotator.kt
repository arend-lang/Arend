package org.arend.annotation

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.arend.parser.ParserMixin.DOC_COMMENT
import org.arend.psi.ArendElementTypes.BLOCK_COMMENT
import org.arend.util.ArendBundle

/**
 * Reports a block comment that is not closed. The lexer reads '{-' as the start of a comment wherever it is, also inside
 * a comment, so the comment extends to the end of the file. The CLI lexer reports the same error.
 */
class ArendUnclosedCommentAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element !is PsiComment || element.tokenType != BLOCK_COMMENT && element.tokenType != DOC_COMMENT) return
        val range = element.textRange
        // Only a comment that reaches the end of the file can be unclosed
        if (range.endOffset != holder.currentAnnotationSession.file.textLength || isClosedBlockComment(element.text)) return
        holder.newAnnotation(HighlightSeverity.ERROR, ArendBundle.message("arend.comment.unclosed"))
            .range(TextRange.from(range.startOffset, 2))
            .create()
    }
}

// Follows the BLOCK_COMMENT_INNER state of ArendLexer.flex
internal fun isClosedBlockComment(text: String): Boolean {
    if (!text.startsWith("{-")) return true
    var depth = 0
    var i = 2
    while (i + 1 < text.length) {
        val c = text[i]
        val next = text[i + 1]
        if (c == '{' && next == '-' || c == '-' && next == '}') {
            i += 2
            if (c == '{') {
                depth++
            } else if (depth-- == 0) {
                return true
            }
        } else {
            i++
        }
    }
    return false
}
