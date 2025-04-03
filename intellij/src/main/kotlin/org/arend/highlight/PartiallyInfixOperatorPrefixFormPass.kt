package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.descendantsOfType
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.util.ArendBundle
import org.arend.util.appExprToConcrete

class PartiallyInfixOperatorPrefixFormPass(file: ArendFile, editor: Editor):
    BasePass(file, editor, "Partially applied infix operators in prefix form annotator", TextRange(0, editor.document.textLength)) {

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val infixArendRefIdentifiers = file.descendantsOfType<ArendRefIdentifier>().filter {
            (it.resolve as? ReferableBase<*>?)?.precedence?.isInfix == true
        }.toList()

        loop@ for (arendRefIdentifier in infixArendRefIdentifiers) {
            var element = arendRefIdentifier as PsiElement?
            while (element !is ArendNewExpr) {
                element = element?.parent
                if (element == null)
                    continue@loop
            }
            val concrete = appExprToConcrete(element)
            val arguments = concrete?.argumentsSequence ?: emptyList()
            if (arguments.size != 2)
                continue

            val (first, second) = arguments
            val operationReference = when (val firstData = first.expression.data) {
                is ArendRefIdentifier -> firstData
                is ArendAtomFieldsAcc -> firstData.fieldAccList.last().refIdentifier ?: continue
                else -> continue
            }
            val argument = second.expression.data as? PsiElement? ?: continue

            if (operationReference.textRange.endOffset >= argument.textRange.startOffset ||
                operationReference.resolve != arendRefIdentifier.resolve ||
                argument !is ArendAtom || !second.isExplicit)
                continue

            val builder = HighlightInfo
                .newHighlightInfo(HighlightInfoType.WARNING)
                .range(element.textRange)
                .severity(HighlightSeverity.WARNING)
                .descriptionAndTooltip(ArendBundle.message("arend.inspection.infix.partially.prefix.form"))
            addHighlightInfo(builder)
        }
    }
}
