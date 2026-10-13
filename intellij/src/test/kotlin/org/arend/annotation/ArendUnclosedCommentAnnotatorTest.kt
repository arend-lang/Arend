package org.arend.annotation

import com.intellij.lang.annotation.HighlightSeverity
import org.arend.ArendTestBase
import org.arend.util.ArendBundle

class ArendUnclosedCommentAnnotatorTest : ArendTestBase() {
    // The offsets of the reported unclosed comments
    private fun unclosedComments(code: String): List<Int> {
        InlineFile(code)
        val message = ArendBundle.message("arend.comment.unclosed")
        return myFixture.doHighlighting(HighlightSeverity.ERROR).filter { it.description == message }.map { it.startOffset }
    }

    fun `test closed comments`() {
        assertEquals(emptyList<Int>(), unclosedComments("{- a -}\n\\func f => {- b {- c -} -} 0 {- | d -} {--}"))
    }

    fun `test comment closed at the end of the file`() {
        assertEquals(emptyList<Int>(), unclosedComments("\\func f => 0 {- a -}"))
    }

    fun `test opening inside a line comment`() {
        assertEquals(emptyList<Int>(), unclosedComments("\\func f => 0 -- {- a\n-- | \$A^{-1}\$\n\\func g => 1"))
    }

    fun `test implicit negative argument`() {
        val code = "\\func f {n : Int} => 0\n\\func g => f {-1}"
        assertEquals(listOf(code.indexOf("{-1}")), unclosedComments(code))
    }

    fun `test unbalanced nested comment`() {
        val code = "\\func f => 0 {- a {- b -}\n\\func g => 1"
        assertEquals(listOf(code.indexOf("{- a")), unclosedComments(code))
    }

    fun `test nested comment in a doc comment`() {
        assertEquals(listOf(0), unclosedComments("{- | \$A^{-1}\$ -}\n\\func f => 0"))
    }
}
