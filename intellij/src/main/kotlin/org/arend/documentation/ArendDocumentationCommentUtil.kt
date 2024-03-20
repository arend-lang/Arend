package org.arend.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.psi.PsiElement
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import org.arend.parser.ParserMixin.*
import org.arend.psi.childrenWithLeaves
import org.arend.psi.doc.ArendDocCodeBlock
import org.arend.psi.doc.ArendDocReference
import org.arend.psi.doc.ArendDocReferenceText
import org.arend.psi.ext.PsiReferable
import java.net.URL

internal data class ArendDocCommentInfo(var hasLatexCode: Boolean, var wasPrevRow: Boolean, var itemContextLastIndex: Int = -1)

internal fun hasLatexCode(doc: PsiElement) = doc.childrenWithLeaves.any { it.elementType == DOC_LATEX_CODE }

internal fun StringBuilder.generateDocComments(ref: PsiReferable, doc: PsiElement, full: Boolean, docCommentInfo: ArendDocCommentInfo) {
    var curIndex = 0
    val docElements = doc.childrenWithLeaves.toList()
    val context = mutableListOf<Pair<IElementType, Int?>>()

    while (curIndex <= docElements.lastIndex) {
        curIndex = processDocCommentElement(curIndex, docElements, ref, full, context, docCommentInfo)
    }
}

private fun StringBuilder.processDocCommentElement(
    index: Int,
    docElements: List<PsiElement>,
    ref: PsiReferable,
    full: Boolean,
    context: MutableList<Pair<IElementType, Int?>>,
    docCommentInfo: ArendDocCommentInfo
): Int {
    val docElement = docElements[index]
    val elementType = docElement.elementType
    when {
        elementType == DOC_LBRACKET -> append("[")
        elementType == DOC_RBRACKET -> append("]")
        elementType == DOC_TEXT -> {
            val nextElement = docElements.getOrNull(index + 1)
            val nextElementType = nextElement.elementType
            if (docElements.getOrNull(index + 2).elementType == DOC_NEWLINE) {
                if (nextElementType == DOC_HEADER_1) {
                    append("<h1>${docElement.text}</h1>")
                    return index + 2
                } else if (nextElementType == DOC_HEADER_2) {
                    append("<h2>${docElement.text}</h2>")
                    return index + 2
                }
            }
            html(docElement.text)
        }
        elementType == DOC_NEWLINE -> {
            if (!docCommentInfo.wasPrevRow) {
                docCommentInfo.wasPrevRow = true
            } else {
                if (context.isEmpty()) {
                    append("</div>")
                }
            }
            if (docElements.getOrNull(index + 1).elementType != DOC_END) {
                append("<div class=\"row\"> ")
            }
        }
        elementType == TokenType.WHITE_SPACE || elementType == DOC_TABS -> append(" ")
        elementType == DOC_CODE -> append("<code>${docElement.text.htmlEscape()}</code>")
        elementType == DOC_LATEX_CODE -> append(getHtmlLatexCode("image${counterLatexImages++}",
            docElement.text.replace(REGEX_AREND_DOC_NEW_LINE, " "),
            ref.project,
            docElement.textOffset,
            docElements.getOrNull(index - 1).elementType == DOC_NEWLINE_LATEX_CODE)
        )
        elementType == DOC_NEWLINE_LATEX_CODE -> {
            val prevElementType = docElements.getOrNull(index - 1).elementType
            if (prevElementType == DOC_TEXT || prevElementType == DOC_LATEX_CODE) {
                if (context.isEmpty()) {
                    append("</div><br><div class=\"row\"> ")
                } else {
                    append("<br>")
                }
            }
        }
        elementType == DOC_UNORDERED_LIST -> {
            return appendListItems(index, docElements, ref, full, context, docCommentInfo)
        }
        elementType == DOC_ORDERED_LIST -> {
            return appendListItems(index, docElements, ref, full, context, docCommentInfo)
        }
        elementType == DOC_BLOCKQUOTES -> {
            return appendBlockQuotes(index, docElements, ref, full, context, docCommentInfo)
        }
        elementType == DOC_ITALICS_CODE -> append("<i>${docElement.text}</i>")
        elementType == DOC_BOLD_CODE -> append("<b>${docElement.text}</b>")
        elementType == DOC_PARAGRAPH_SEP || elementType == DOC_LINEBREAK -> {
            if (context.isNotEmpty()) {
                append("<br>")
                return index + 1
            }
            if (docCommentInfo.wasPrevRow) {
                append("</div>")
            }
            docCommentInfo.wasPrevRow = false
            append("<br>")
            if (elementType == DOC_PARAGRAPH_SEP && !full) {
                append("<a href=\"${PSI_ELEMENT_PROTOCOL}$FULL_PREFIX${ref.refName}\">more...</a>")
                return docElements.lastIndex + 1
            }
        }
        docElement is ArendDocReference -> {
            val url = docElement.longName.text
            if (isValidUrl(url)) {
                append("<a href=\"$url\">${docElement.docReferenceText?.let { getReferenceText(it).joinToString() } ?: url}</a>")
                return index + 1
            }
            val longName = docElement.longName
            val link = longName.refIdentifierList.joinToString(".") { it.id.text.htmlEscape() }
            val isLink = longName.resolve is PsiReferable
            if (isLink) {
                append("<a href=\"${PSI_ELEMENT_PROTOCOL}$link\">")
            }

            append("<code>")
            val text = docElement.docReferenceText
            if (text != null) {
                getReferenceText(text).forEach { html(it) }
            } else {
                append(link)
            }
            append("</code>")

            if (isLink) {
                append("</a>")
            }
        }
        docElement is ArendDocCodeBlock -> {
            append("<pre>")
            for (child in docElement.childrenWithLeaves) {
                when (child.elementType) {
                    DOC_CODE_LINE -> html(child.text)
                    TokenType.WHITE_SPACE -> append("\n")
                }
            }
            append("</pre>")
        }
    }
    return index + 1
}

private fun isValidUrl(urlString: String): Boolean {
    return try {
        URL(urlString)
        true
    } catch (ex: Exception) {
        false
    }
}

private fun getReferenceText(text: ArendDocReferenceText): Sequence<String> {
    return text.childrenWithLeaves.filter { it.elementType == DOC_TEXT }.map { it.text }
}

val LIST_ELEMENT_TYPES = listOf(DOC_ORDERED_LIST, DOC_UNORDERED_LIST)
val CONTEXT_ELEMENTS = LIST_ELEMENT_TYPES + DOC_BLOCKQUOTES
val LIST_UNORDERED_ITEM_SYMBOLS = listOf("* ", "- ", "+ ")
val LIST_ORDERED_REGEX = "[0-9]+.".toRegex()


private fun StringBuilder.appendListItems(
    index: Int,
    docElements: List<PsiElement>,
    ref: PsiReferable,
    full: Boolean,
    context: MutableList<Pair<IElementType, Int?>>,
    docCommentInfo: ArendDocCommentInfo
): Int {
    val element = docElements[index]
    val elementType = element.elementType!!
    if (elementType == DOC_UNORDERED_LIST) {
        context.add(Pair(elementType, null))
        append("<ul>")
    } else {
        context.add(Pair(elementType, 1))
        append("<ol>")
    }
    append("<li class=\"row\">")
    if (docCommentInfo.hasLatexCode) {
        if (elementType == DOC_ORDERED_LIST) {
            append("1. ")
        } else {
            append("• ")
        }
    }
    docCommentInfo.itemContextLastIndex = context.lastIndex

    val closingTagHtml = "</li>"
    val defaultOpeningTagHtml = "<li class=\"row\">"
    val openingTagHtml = if (docCommentInfo.hasLatexCode && elementType == DOC_UNORDERED_LIST) {
        "$defaultOpeningTagHtml• "
    } else {
        defaultOpeningTagHtml
    }
    val newIndex = processBlock(index + 1, docElements, ref, full, context, docCommentInfo, openingTagHtml, closingTagHtml)

    if (elementType == DOC_UNORDERED_LIST) {
        append("</ul>")
    } else {
        append("</ol>")
    }
    return newIndex
}

private fun StringBuilder.appendBlockQuotes(
    index: Int,
    docElements: List<PsiElement>,
    ref: PsiReferable,
    full: Boolean,
    context: MutableList<Pair<IElementType, Int?>>,
    docCommentInfo: ArendDocCommentInfo
): Int {
    val element = docElements[index]
    context.add(Pair(element.elementType!!, null))
    append("<blockquote><p class=\"row\">")

    val newIndex = processBlock(index + 1, docElements, ref, full, context, docCommentInfo, " ", "")

    append("</p></blockquote>")
    return newIndex
}

private fun StringBuilder.processBlock(
    index: Int,
    docElements: List<PsiElement>,
    ref: PsiReferable,
    full: Boolean,
    context: MutableList<Pair<IElementType, Int?>>,
    docCommentInfo: ArendDocCommentInfo,
    openingTagHtml: String,
    closingTagHtml: String
): Int {
    var curIndex = index
    while (curIndex <= docElements.lastIndex) {
        val curElementType = docElements[curIndex].elementType
        if (curElementType == DOC_PARAGRAPH_SEP) {
            break
        } else if (curElementType == DOC_NEWLINE || curElementType == DOC_LINEBREAK) {
            if (curElementType == DOC_LINEBREAK) {
                append("<br>")
            }
            val resultContext = checkContext(curIndex, docElements, context)
            if (resultContext.first) {
                curIndex += resultContext.second
                if (docCommentInfo.itemContextLastIndex == context.lastIndex) {
                    append(closingTagHtml)
                }
                append(openingTagHtml)

                if (docCommentInfo.hasLatexCode && docElements.getOrNull(curIndex).elementType == DOC_ORDERED_LIST) {
                    context.last().second?.let {
                        append("${it + 1}. ")
                        context.removeLast()
                        context.add(Pair(DOC_ORDERED_LIST, it + 1))
                    }
                }
                docCommentInfo.itemContextLastIndex = context.lastIndex
            } else if (isNestedList(curIndex, docElements, context)) {
                append(closingTagHtml)
                curIndex = appendListItems(
                    curIndex + 2,
                    docElements,
                    ref,
                    full,
                    context,
                    docCommentInfo
                )
                continue
            } else {
                if (docCommentInfo.itemContextLastIndex == context.lastIndex) {
                    append(closingTagHtml)
                }
                break
            }
        } else {
            curIndex = processDocCommentElement(curIndex, docElements, ref, full, context, docCommentInfo)
            continue
        }
        curIndex++
    }
    context.removeLast()
    return curIndex
}

private fun checkContext(elementIndex: Int, docElements: List<PsiElement>, context: List<Pair<IElementType, Int?>>): Pair<Boolean, Int> {
    var contextIndex = 0
    var shiftDocElements = 1
    while (contextIndex <= context.lastIndex) {
        val contextElement = context[contextIndex]
        val element = docElements.getOrNull(elementIndex + shiftDocElements)
        val elementType = element?.elementType

        if (elementType == DOC_TABS) {
            var numberTabs = (element!!.text.length + AREND_DOC_COMMENT_TABS_SIZE - 1) / AREND_DOC_COMMENT_TABS_SIZE
            while (numberTabs > 0 && contextIndex < context.lastIndex) {
                if (!LIST_ELEMENT_TYPES.contains(context[contextIndex].first)) {
                    return Pair(false, -1)
                }
                contextIndex++
                numberTabs--
            }
            shiftDocElements++
            if (numberTabs == 0) {
                continue
            }
        }
        if (elementType != contextElement.first) {
            return Pair(false, -1)
        }
        contextIndex++
        shiftDocElements++
    }
    return Pair(true, shiftDocElements - 1)
}

private fun isNestedList(elementIndex: Int, docElements: List<PsiElement>, context: List<Pair<IElementType, Int?>>): Boolean {
    var firstNotEqualContextIndex = -1
    for (contextElement in context.withIndex()) {
        val elementType = docElements.getOrNull(elementIndex + contextElement.index + 1)?.elementType
        if (elementType != contextElement.value) {
            firstNotEqualContextIndex = contextElement.index
            break
        }
    }

    val whiteSpaceItem = docElements.getOrNull(elementIndex + 1)
    if (whiteSpaceItem.elementType != DOC_TABS) {
        return false
    }
    val numberTabs = (whiteSpaceItem!!.text.length + AREND_DOC_COMMENT_TABS_SIZE - 1) / AREND_DOC_COMMENT_TABS_SIZE
    if (numberTabs != context.lastIndex - firstNotEqualContextIndex + 1) {
        return false
    }
    for (index in firstNotEqualContextIndex..context.lastIndex) {
        if (!LIST_ELEMENT_TYPES.contains(context[index].first)) {
            return false
        }
    }

    val listItemType = docElements.getOrNull(elementIndex + 2).elementType
    return LIST_ELEMENT_TYPES.contains(listItemType)
}
