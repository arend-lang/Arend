package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsCollector
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.endOffset
import org.arend.codeInsight.hints.ArendParametersInlaySettingsProvider.Companion.SHOW_TYPE_SETTINGS
import org.arend.core.context.binding.LevelVariable
import org.arend.core.context.binding.ParamLevelVariable
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.ClassDefinition
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.psi.ext.*
import org.arend.psi.ArendElementTypes.NO_CLASSIFYING_KW
import org.arend.psi.findNextSibling
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import java.lang.Integer.min

class ArendParametersInlayProvider : InlayHintsProvider {
    override fun createCollector(
        file: PsiFile,
        editor: Editor
    ): InlayHintsCollector? {
        return object : SharedBypassCollector {
            override fun collectFromElement(
                element: PsiElement,
                sink: InlayTreeSink
            ) {
                if (element !is ArendDefIdentifier) return
                val arendDef = element.parent as? ArendDefinition<*> ?: return
                val def = arendDef.tcReferable?.typechecked ?: return
                val levelParams = def.levelParameters
                if ((levelParams == null || levelParams.isEmpty()) && def.parametersOriginalDefinitions.isEmpty()) return
                val builder = StringBuilder()

                if (levelParams != null && levelParams.isNotEmpty()) {
                    if (levelParams[0].type == LevelVariable.LvlType.PLVL && levelParams[0] is ParamLevelVariable && arendDef.pLevelParameters == null) {
                        builder.append(" ")
                        val ppv = PrettyPrintVisitor(builder, 0)
                        ppv.prettyPrintLevelParameters(ToAbstractVisitor.visitLevelParameters(levelParams.subList(0, def.numberOfPLevelParameters), true), true)
                    }
                    val lastVar = levelParams[levelParams.size - 1]
                    if (lastVar.type == LevelVariable.LvlType.HLVL && lastVar is ParamLevelVariable && arendDef.hLevelParameters == null) {
                        builder.append(" ")
                        val ppv = PrettyPrintVisitor(builder, 0)
                        ppv.prettyPrintLevelParameters(ToAbstractVisitor.visitLevelParameters(levelParams.subList(def.numberOfPLevelParameters, levelParams.size), false), false)
                    }
                }

                if (def.parametersOriginalDefinitions.isNotEmpty()) {
                    builder.append(" ")
                    if (SHOW_TYPE_SETTINGS.showTypes) {
                        val ppv = PrettyPrintVisitor(builder, 0)
                        val parameters = if (def is ClassDefinition) {
                            def.personalFields.subList(0, def.parametersOriginalDefinitions.size).map { Concrete.TelescopeParameter(null, it.referable.isExplicitField, listOf(it.referable), ToAbstractVisitor.convert(it.resultType, PrettyPrinterConfig.DEFAULT), it.isProperty) }
                        } else {
                            ToAbstractVisitor.convert(DependentLink.Helper.take(if (def.hasEnclosingClass()) def.parameters.next else def.parameters, def.parametersOriginalDefinitions.size), PrettyPrinterConfig.DEFAULT)
                        }
                        ppv.prettyPrintParameters(parameters)
                    } else {
                        val list = if (def is ClassDefinition) {
                            def.personalFields.subList(0, min(def.parametersOriginalDefinitions.size, def.personalFields.size)).map { Pair(it.referable.isExplicitField, it.name) }
                        } else {
                            DependentLink.Helper.toList(DependentLink.Helper.take(if (def.hasEnclosingClass()) def.parameters.next else def.parameters, def.parametersOriginalDefinitions.size)).map { Pair(it.isExplicit, it.name) }
                        }
                        builder.append(list.joinToString(" ") { if (it.first) it.second else "{${it.second}}" })
                    }
                }

                val str = builder.toString()
                if (str.isEmpty()) return
                var lastElement = element
                val sibling1 = lastElement.findNextSibling()
                if (sibling1 is ArendAlias) lastElement = sibling1
                val sibling2 = lastElement.findNextSibling()
                if (sibling2 is LeafPsiElement && sibling2.elementType == NO_CLASSIFYING_KW) lastElement = sibling2
                sink.addPresentation(InlineInlayPosition(lastElement.endOffset, true), hintFormat = HintFormat.default) {
                    text(str)
                }
            }
        }
    }
}