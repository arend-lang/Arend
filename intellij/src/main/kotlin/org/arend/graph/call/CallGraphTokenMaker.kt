package org.arend.graph.call

import com.jetbrains.rd.util.UsedImplicitly
import org.fife.ui.rsyntaxtextarea.AbstractTokenMaker
import org.fife.ui.rsyntaxtextarea.Token
import org.fife.ui.rsyntaxtextarea.TokenMap
import org.fife.ui.rsyntaxtextarea.modes.PlainTextTokenMaker
import javax.swing.text.Segment

@UsedImplicitly
class CallGraphTokenMaker : AbstractTokenMaker() {
  private fun checkSymbol(token: Token): Boolean {
    return token.textArray.getOrNull(token.textOffset - 1) == ' ' &&
      (token.textArray.getOrNull(token.textOffset + 1) == ' ' || token.nextToken.type == Token.NULL)
  }

  override fun getTokenList(text: Segment?, initialTokenType: Int, startOffset: Int): Token {
    val tokeList = PlainTextTokenMaker().getTokenList(text, initialTokenType, startOffset)
    var token = tokeList
    while (token != null) {
      when {
        token.isSingleChar('<') && checkSymbol(token) -> token.type = CALL_GRAPH_LESS_NUMBER
        token.isSingleChar('=') && checkSymbol(token) -> token.type = CALL_GRAPH_EQUAL_NUMBER
        token.isSingleChar('?') && checkSymbol(token) -> token.type = CALL_GRAPH_QUESTION_NUMBER
      }

      token = token.nextToken
    }
    return tokeList
  }

  override fun getWordsToHighlight(): TokenMap {
    return TokenMap().apply {
      put("<", CALL_GRAPH_LESS_NUMBER)
      put("=", CALL_GRAPH_EQUAL_NUMBER)
      put("?", CALL_GRAPH_QUESTION_NUMBER)
    }
  }
}