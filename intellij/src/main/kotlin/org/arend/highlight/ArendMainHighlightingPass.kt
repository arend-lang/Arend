package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.validOrNull
import com.intellij.util.containers.mapSmartNotNull
import com.intellij.xml.util.XmlStringUtil
import org.arend.ext.error.GeneralError
import org.arend.ext.error.LocalError
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.ext.prettyprinting.doc.DocFactory.vHang
import org.arend.ext.prettyprinting.doc.DocStringBuilder
import org.arend.ext.reference.DataContainer
import org.arend.naming.scope.EmptyScope
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import java.util.*

/**
 * A highlighting pass that can be used by MainPassesRunner (used by Junie/ElectroJunior)
 * to collect error highlighting information without requiring an Editor.
 */
class ArendMainHighlightingPass(
    private val file: ArendFile,
    document: Document
) : TextEditorHighlightingPass(file.project, document, true) {

    private val highlights = ArrayList<HighlightInfo>()

    override fun doCollectInformation(progress: ProgressIndicator) {
        println("[DEBUG_LOG] ArendMainHighlightingPass.doCollectInformation called for file: ${file.name}")
        val module = file.moduleLocation ?: run {
            println("[DEBUG_LOG] ArendMainHighlightingPass.doCollectInformation: module is null")
            return
        }
        println("[DEBUG_LOG] ArendMainHighlightingPass.doCollectInformation: module=$module")
        val errors = myProject.service<ArendServerService>().server.errorMap[module] ?: run {
            println("[DEBUG_LOG] ArendMainHighlightingPass.doCollectInformation: no errors for module")
            return
        }
        println("[DEBUG_LOG] ArendMainHighlightingPass.doCollectInformation: found ${errors.size} errors")
        
        runReadAction {
            for (error in errors) {
                processError(error)
            }
        }
    }

    private fun processError(error: GeneralError) {
        val causeList = error.cause?.let { it as? Collection<*> ?: listOf(it) }?.mapSmartNotNull { getCauseElement(it)?.validOrNull() }
        
        if (causeList.isNullOrEmpty()) {
            val psi = ((error as? LocalError)?.definition as? DataContainer)?.data as? PsiElement
            if (psi != null && psi.isValid && psi.containingFile == file) {
                addHighlightForError(error, psi)
            }
        } else {
            for (psi in causeList) {
                if (psi.isValid && psi.containingFile == file) {
                    addHighlightForError(error, psi)
                }
            }
        }
    }

    private fun getCauseElement(data: Any?): PsiElement? {
        return when (val d = (data as? DataContainer)?.data ?: data) {
            is PsiElement -> d
            else -> null
        }
    }

    private fun addHighlightForError(error: GeneralError, cause: PsiElement) {
        val textRange = cause.textRange ?: return
        val builder = createHighlightInfoBuilder(error, textRange)
        val info = builder.create()
        if (info != null) {
            highlights.add(info)
        }
    }

    private fun createHighlightInfoBuilder(error: GeneralError, range: TextRange): HighlightInfo.Builder {
        val ppConfig = PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE)
        ppConfig.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE)
        return HighlightInfo.newHighlightInfo(levelToHighlightInfoType(error.level))
            .range(range)
            .severity(levelToSeverity(error.level))
            .description(error.shortMessage ?: "")
            .escapedToolTip(XmlStringUtil.escapeString(DocStringBuilder.build(vHang(error.getShortHeaderDoc(ppConfig), error.getBodyDoc(ppConfig)))).replace("\n", "<br>"))
    }

    private fun levelToSeverity(level: GeneralError.Level): HighlightSeverity = when (level) {
        GeneralError.Level.ERROR -> HighlightSeverity.ERROR
        GeneralError.Level.WARNING, GeneralError.Level.WARNING_UNUSED -> HighlightSeverity.WARNING
        GeneralError.Level.GOAL -> HighlightSeverity.WARNING
        GeneralError.Level.INFO -> HighlightSeverity.INFORMATION
    }

    private fun levelToHighlightInfoType(level: GeneralError.Level): HighlightInfoType = when (level) {
        GeneralError.Level.ERROR -> HighlightInfoType.ERROR
        GeneralError.Level.WARNING, GeneralError.Level.WARNING_UNUSED -> HighlightInfoType.WARNING
        GeneralError.Level.GOAL -> HighlightInfoType.INFORMATION
        GeneralError.Level.INFO -> HighlightInfoType.INFORMATION
    }

    override fun doApplyInformationToEditor() {
        println("[DEBUG_LOG] ArendMainHighlightingPass.doApplyInformationToEditor called with ${highlights.size} highlights")
        // Print stack trace to see who's calling this
        println("[DEBUG_LOG] Stack trace:")
        Thread.currentThread().stackTrace.take(15).forEach { element ->
            println("[DEBUG_LOG]   at $element")
        }
        // Always call setHighlightersToEditor, even with empty list, to clear old highlights
        UpdateHighlightersUtil.setHighlightersToEditor(
            myProject,
            myDocument,
            0,
            myDocument.textLength,
            highlights,
            colorsScheme,
            id
        )
    }
}
