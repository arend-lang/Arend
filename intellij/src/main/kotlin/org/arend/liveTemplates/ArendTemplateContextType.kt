package org.arend.liveTemplates

import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import org.arend.codeInsight.completion.ArendKeywords
import org.arend.psi.ArendElementTypes.CASE_KW
import org.arend.psi.ArendElementTypes.FUNC_KW
import org.arend.psi.ArendFile
import org.arend.search.ArendWordScanner
import org.jetbrains.annotations.Nls

abstract class ArendTemplateContextType(@Nls presentableName: String) : TemplateContextType(presentableName) {

    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        val file = templateActionContext.file as? ArendFile ?: return false
        val element = file.findElementAt(templateActionContext.startOffset) ?: return false
        return isInContext(element)
    }

    protected open fun isInContext(element: PsiElement): Boolean = element !is PsiComment

    /** Checks if [keyword] may be written at the beginning of the template */
    protected fun keywordFits(templateActionContext: TemplateActionContext, keyword: String): Boolean {
        val file = templateActionContext.file.originalFile
        val text = file.viewProvider.contents
        var start = minOf(templateActionContext.startOffset, text.length)
        while (start > 0 && ArendWordScanner.isArendIdentifierPart(text[start - 1])) start--
        if (start > 0 && text[start - 1] == '\\') start--
        return ArendKeywords.complete(file, start, start, useTail = false).any { it.keyword == keyword }
    }

    class Everywhere : ArendTemplateContextType("Arend")

    class Statement : ArendTemplateContextType("Statement") {
        override fun isInContext(templateActionContext: TemplateActionContext) =
            super.isInContext(templateActionContext) && keywordFits(templateActionContext, FUNC_KW.toString())
    }

    class Expression : ArendTemplateContextType("Expression") {
        override fun isInContext(templateActionContext: TemplateActionContext) =
            super.isInContext(templateActionContext) && keywordFits(templateActionContext, CASE_KW.toString())
    }
}
