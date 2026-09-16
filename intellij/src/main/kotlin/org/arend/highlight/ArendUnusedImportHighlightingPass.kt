package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UnusedSymbolUtil
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ex.QuickFixWrapper
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import org.arend.inspection.ArendUnusedImportInspection
import org.arend.intention.ArendOptimizeImportsQuickFix
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService
import org.arend.server.ImportAnalyzer
import org.arend.server.ImportFinding
import org.arend.util.ArendBundle
import org.jetbrains.annotations.Nls

class ArendUnusedImportHighlightingPass(private val file: ArendFile, private val editor: Editor, private val lastModification: Long) :
    TextEditorHighlightingPass(file.project, editor.document) {

    @Volatile
    private var findings: List<ImportFinding> = emptyList()

    override fun doCollectInformation(progress: ProgressIndicator) {
        if (file.isRepl) return
        val module = file.moduleLocation ?: return
        findings = ImportAnalyzer(myProject.service<ArendServerService>().server).findUnused(module, true) ?: emptyList()
    }

    private fun registerUnusedThing(
        element: PsiElement,
        @Nls description: String,
        collector: MutableList<HighlightInfo>
    ) {
        val profile = InspectionProjectProfileManager.getInstance(myProject).currentProfile
        val key = HighlightDisplayKey.find(ArendUnusedImportInspection.ID)
        val highlightInfoType = if (key == null) HighlightInfoType.UNUSED_SYMBOL else HighlightInfoType.HighlightInfoTypeImpl(profile.getErrorLevel(key, element).severity, HighlightInfoType.UNUSED_SYMBOL.attributesKey)
        val builder = UnusedSymbolUtil.createUnusedSymbolInfoBuilder(element, description, highlightInfoType, ArendUnusedImportInspection.ID)
        val intentionAction = QuickFixWrapper.wrap(InspectionManager.getInstance(element.project).createProblemDescriptor(element, description, ArendOptimizeImportsQuickFix(), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true), 0)
        builder.registerFix(intentionAction, null, null, null, null)
        builder.create()?.let {
            collector.add(it)
        }
    }

    private fun message(finding: ImportFinding): String = when (finding.kind()) {
        ImportFinding.Kind.UNUSED_IMPORT -> ArendBundle.message("arend.inspection.unused.import.message.unused.import.0", finding.name())
        ImportFinding.Kind.UNUSED_OPEN -> ArendBundle.message("arend.inspection.unused.import.message.unused.open.0", finding.name())
        ImportFinding.Kind.UNUSED_NAME, ImportFinding.Kind.UNUSED_ALIAS ->
            ArendBundle.message("arend.inspection.unused.import.message.unused.definition.0", finding.name())
    }

    override fun doApplyInformationToEditor() {
        val infos = mutableListOf<HighlightInfo>()
        for (finding in findings) {
            val element = finding.data() as? PsiElement ?: continue
            registerUnusedThing(element, message(finding), infos)
        }
        UpdateHighlightersUtil.setHighlightersToEditor(
            myProject,
            editor.document,
            0,
            file.textLength,
            infos,
            colorsScheme,
            id
        )
        // TODO[server2]: file.lastModificationImportOptimizer.updateAndGet { lastModification }
    }
}
