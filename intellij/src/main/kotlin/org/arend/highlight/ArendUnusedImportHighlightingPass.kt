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
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.arend.codeInsight.getUnusedImports
import org.arend.server.ImportFinding
import org.arend.inspection.ArendUnusedImportInspection
import org.arend.intention.ArendOptimizeImportsQuickFix
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendNsId
import org.arend.psi.ext.ArendStat
import org.arend.psi.ext.ArendStatCmd
import org.arend.util.ArendBundle
import org.jetbrains.annotations.Nls

class ArendUnusedImportHighlightingPass(private val file: ArendFile, private val editor: Editor, private val lastModification: Long) :
    TextEditorHighlightingPass(file.project, editor.document) {

    @Volatile
    private var findings: List<ImportFinding> = emptyList()

    @Volatile
    private var redundantElements: List<PsiElement> = emptyList()

    override fun doCollectInformation(progress: ProgressIndicator) {
        if (file.isRepl) return

        val currentFindings = getUnusedImports(file) ?: return
        // a command is greyed out as a whole statement, which is also what carries the keyword the
        // message below reads; a name is greyed out on its own
        redundantElements = currentFindings.mapNotNull {
            when (val element = it.data()) {
                is ArendNsId -> element
                is ArendStatCmd -> element.parentOfType<ArendStat>()
                else -> null
            }
        }
        findings = currentFindings
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
        if (findings.isNotEmpty()) {
            val intentionAction = QuickFixWrapper.wrap(InspectionManager.getInstance(element.project).createProblemDescriptor(element, description, ArendOptimizeImportsQuickFix(findings), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true), 0)
            builder.registerFix(intentionAction, null, null, null, null)
        }
        builder.create()?.let {
            collector.add(it)
        }
    }

    override fun doApplyInformationToEditor() {
        val infos = mutableListOf<HighlightInfo>()
        for (element in redundantElements) {
            val message = when {
                element is ArendStat && element.statCmd?.importKw != null -> element.statCmd?.longName?.text?.run {
                    ArendBundle.message("arend.inspection.unused.import.message.unused.import.0", this)
                } ?: ArendBundle.message("arend.inspection.unused.import.message.unused.import")
                element is ArendStat && element.statCmd?.openKw != null -> element.statCmd?.longName?.text?.run {
                    ArendBundle.message("arend.inspection.unused.import.message.unused.open.0", this)
                } ?: ArendBundle.message("arend.inspection.unused.import.message.unused.open")
                element is ArendNsId -> element.name?.run {
                    ArendBundle.message("arend.inspection.unused.import.message.unused.definition.0", this )
                } ?: ArendBundle.message("arend.inspection.unused.import.message.unused.definition", this )
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
        // TODO[server2]: file.lastModificationImportOptimizer.updateAndGet { lastModification }
    }
}