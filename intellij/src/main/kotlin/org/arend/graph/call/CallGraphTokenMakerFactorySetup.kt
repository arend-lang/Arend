package org.arend.graph.call

import org.fife.ui.rsyntaxtextarea.AbstractTokenMakerFactory
import org.fife.ui.rsyntaxtextarea.TokenMakerFactory

class CallGraphTokenMakerFactorySetup {
  companion object {
    fun register() {
      val atmf = TokenMakerFactory.getDefaultInstance() as? AbstractTokenMakerFactory? ?: return
      atmf.putMapping("text/CallGraphCustom", "org.arend.graph.call.CallGraphTokenMaker")
    }
  }
}