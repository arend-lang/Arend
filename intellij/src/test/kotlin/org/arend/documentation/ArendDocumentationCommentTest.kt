package org.arend.documentation

import com.intellij.psi.PsiComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import org.arend.ArendTestBase
import org.arend.parser.ParserMixin.DOC_LATEX_CODE
import org.arend.psi.childrenWithLeaves
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.ext.PsiReferable
import org.intellij.lang.annotations.Language
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class ArendDocumentationCommentTest : ArendTestBase() {

    private fun getArendComment(@Language("Arend") code: String): ArendDocComment? {
        val psiFile = InlineFile(code).psiFile
        val comments = psiFile.children.filterIsInstance<ArendDocComment>()
        return comments.getOrNull(0)
    }

    private fun getHtml(@Language("Arend") code: String, withLatexCode: Boolean): String {
        val comment = getArendComment(code)
        assertNotNull(comment)

        val ref = comment!!.owner as? PsiReferable?
        val doc = ref?.documentation
        assertNotNull(ref)
        assertNotNull(doc)

        val hasLatexCode = hasLatexCode(doc!!)
        assertEquals(withLatexCode, hasLatexCode)

        val docCommentInfo = ArendDocCommentInfo(hasLatexCode = hasLatexCode, wasPrevRow = false, suggestedFont = ArendDocumentationGenerator.DEFAULT_FONT)
        val stringBuilder = StringBuilder()
        stringBuilder.generateDocComments(ref, doc, true, docCommentInfo)

        return stringBuilder.toString()
    }

    fun testSimpleComment() {
        val code = "{- |\n" +
                " - a" +
                " -}" +
                "\\data Empty"

        val html = getHtml(code, false)
        val document = Jsoup.parse(html)

        val rows = document.getElementsByClass("row")
        assertEquals(1, rows.size)

        assertEquals("a", rows[0].text())
        assertEquals(0, rows[0].select("img").size)
    }

    fun testCommentWithLatex() {
        val code = "{- |\n" +
                " -   1. a \$\\sqrt{9}\$\n" +
                " -   2. b\n" +
                " -              * bb\n" +
                " -}" +
                "\\data Empty"

        val html = getHtml(code, true)
        val document = Jsoup.parse(html)

        val rows = document.getElementsByClass("row")
        assertEquals(4, rows.size)
        assertTrue(document.getElementsByTag("ol").text().contains("a b bb"))
        assertEquals(2, rows.flatMap { it.select("img") }.size)
    }

    private fun getCommentHtml(@Language("Arend") code: String, commentText: String): Document {
        val comment = PsiTreeUtil.collectElements(InlineFile(code).psiFile) { it is PsiComment && it.text == commentText }.singleOrNull()
        assertNotNull(comment)
        val html = ArendDocumentationGenerator.generateDoc(comment!!, null, suggestedFont = 15, withOpenInBrowserLink = false, showNotification = false)
        assertNotNull(html)
        return Jsoup.parse(html!!)
    }

    fun testDocCommentWithoutDefinition() {
        val document = getCommentHtml("\\func f => 0\n\n-- | new \$\\sqrt{2}\$", "-- | new \$\\sqrt{2}\$")
        assertEquals("new", document.body().text())
        assertEquals(1, document.select("img").size)
    }

    fun testFieldDocComment() {
        val document = getCommentHtml("\\record R {\n  -- | the field \$x\$\n  | x : Nat\n}", "-- | the field \$x\$")
        assertEquals("the field", document.body().text())
        assertEquals(1, document.select("img").size)
    }

    fun testLineComment() {
        val document = getCommentHtml("-- plain \$\\sqrt{2}\$ and **bold**\n\\func f => 0", "-- plain \$\\sqrt{2}\$ and **bold**")
        assertEquals("plain and bold", document.body().text())
        assertEquals("bold", document.select("b").text())
        assertEquals(1, document.select("img").size)
    }

    fun testBlockComment() {
        val text = "{- first \$a\$\n   second \$\\frac{1}{2}\$ -}"
        val document = getCommentHtml("$text\n\\func f => 0", text)
        assertEquals("first second", document.body().text())
        assertEquals(2, document.select("img").size)
    }

    fun testCommentOffsets() {
        val code = "\\func f => 0\n  {- a \$x\$ -} -- b \$y\$\n"
        for (comment in PsiTreeUtil.collectElements(InlineFile(code).psiFile) { it is PsiComment }) {
            val (doc, shift) = readAsDocComment(comment as PsiComment)!!
            val latex = doc.childrenWithLeaves.single { it.elementType == DOC_LATEX_CODE }
            assertEquals(code.indexOf(latex.text, comment.textRange.startOffset), latex.textOffset + shift)
        }
    }
}
