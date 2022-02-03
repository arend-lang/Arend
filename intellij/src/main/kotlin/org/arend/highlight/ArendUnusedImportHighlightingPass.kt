package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import org.arend.codeInsight.OptimizationResult
import org.arend.codeInsight.getOptimalImportStructure
import org.arend.codeInsight.processRedundantImportedDefinitions
import org.arend.inspection.ArendUnusedImportInspection
import org.arend.intention.ArendOptimizeImportsQuickFix
import org.arend.psi.ArendFile
import org.arend.psi.ArendNsId
import org.arend.psi.ArendStatement
import org.arend.util.ArendBundle
import org.jetbrains.annotations.Nls

class ArendUnusedImportHighlightingPass(private val file: ArendFile, private val editor: Editor) :
    TextEditorHighlightingPass(file.project, editor.document) {

    @Volatile
    private var optimizationResult: OptimizationResult? = null

    @Volatile
    private var redundantElements: List<PsiElement> = emptyList()

    override fun doCollectInformation(progress: ProgressIndicator) {
        val currentOptimizationResult = getOptimalImportStructure(file, false, progress)
        val (fileImports, openStructure, _) = currentOptimizationResult
        val toErase = mutableListOf<PsiElement>()
        processRedundantImportedDefinitions(file, fileImports, openStructure) {
            toErase.add(it)
        }
        optimizationResult = currentOptimizationResult
        redundantElements = toErase
    }

    private fun registerUnusedThing(
        element: PsiElement,
        description: @Nls String,
        collector: MutableList<HighlightInfo>
    ) {
        val profile = InspectionProjectProfileManager.getInstance(myProject).currentProfile
        val key = HighlightDisplayKey.find(ArendUnusedImportInspection.ID)
        val highlightInfoType = if (key == null) HighlightInfoType.UNUSED_SYMBOL else HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(key, element).severity, HighlightInfoType.UNUSED_SYMBOL.attributesKey)
        UnusedSymbolUtil.createUnusedSymbolInfo(element, description, highlightInfoType, ArendUnusedImportInspection.ID)?.let {
            val actualOptimizationResult = optimizationResult
            if (actualOptimizationResult != null) {
                val intentionAction = QuickFixWrapper.wrap(InspectionManager.getInstance(element.project).createProblemDescriptor(element, description, ArendOptimizeImportsQuickFix(actualOptimizationResult), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true), 0)
                QuickFixAction.registerQuickFixAction(it, intentionAction)
            }
            collector.add(it)
        }
    }

    override fun doApplyInformationToEditor() {
        val infos = mutableListOf<HighlightInfo>()
        for (element in redundantElements) {
            val message = when {
                element is ArendStatement && element.statCmd?.importKw != null -> ArendBundle.message("arend.inspection.unused.import.message.unused.import")
                element is ArendStatement && element.statCmd?.openKw != null -> ArendBundle.message("arend.inspection.unused.import.message.unused.open")
                element is ArendNsId -> ArendBundle.message("arend.inspection.unused.import.message.unused.definition")
                else -> error("Unexpected element. Please report")
            }
            registerUnusedThing(element, message, infos)
        }
        UpdateHighlightersUtil.setHighlightersToEditor(
            file.project,
            editor.document,
            0,
            file.textLength,
            infos,
            colorsScheme,
            id
        )
    }
}