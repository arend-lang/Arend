package org.vclang.ide.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType
import org.vclang.ide.colors.VcHighlightingColors
import org.vclang.lang.core.lexer.VcLexerAdapter
import org.vclang.lang.core.psi.VC_KEYWORDS
import org.vclang.lang.core.psi.VcElementTypes.*
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default

class VcSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = VcLexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
            pack(map(tokenType)?.textAttributesKey)

    companion object {
        fun map(tokenType: IElementType?): VcHighlightingColors? = when (tokenType) {
            PREFIX -> VcHighlightingColors.IDENTIFIER
            NUMBER -> VcHighlightingColors.NUMBER
            in VC_KEYWORDS -> VcHighlightingColors.KEYWORD
            SET, UNIVERSE, TRUNCATED_UNIVERSE -> VcHighlightingColors.UNIVERSE
            UNDERSCORE, GOAL -> VcHighlightingColors.IMPLICIT

            INFIX -> VcHighlightingColors.OPERATORS
            DOT -> VcHighlightingColors.DOT
            COMMA -> VcHighlightingColors.COMMA
            COLON, COLONCOLON -> VcHighlightingColors.COLON
            GRAVE -> VcHighlightingColors.GRAVE
            PIPE -> VcHighlightingColors.COMMA
            ARROW, FAT_ARROW -> VcHighlightingColors.ARROW

            LBRACE, RBRACE -> VcHighlightingColors.BRACES
            LPAREN, RPAREN -> VcHighlightingColors.PARENTHESIS

            BLOCK_COMMENT -> VcHighlightingColors.BLOCK_COMMENT
            LINE_COMMENT -> VcHighlightingColors.LINE_COMMENT

            TokenType.BAD_CHARACTER -> VcHighlightingColors.BAD_CHARACTER
            else -> null
        }
    }
}
