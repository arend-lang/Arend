package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.startOffset
import org.arend.core.definition.Definition
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.psi.ext.ArendDefinition

@Suppress("UnstableApiUsage")
abstract class ArendDefinitionInlayProvider : InlayHintsProvider {
    override fun createCollector(file: PsiFile, editor: Editor): InlayHintsCollector? {
        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                if (element !is ArendDefIdentifier) return
                val arendDef = element.parent as? ArendDefinition<*> ?: return
                val def = arendDef.tcReferable?.typechecked ?: return
                val text = getText(def) ?: return

                val offset = (arendDef.parent ?: arendDef).startOffset
                sink.addPresentation(AboveLineIndentedPosition(offset), hintFormat = HintFormat.default) {
                    text(text)
                }
                return
            }
        }
    }

    abstract fun getText(definition: Definition): String?
}