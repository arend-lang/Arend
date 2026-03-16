package org.arend.highlight

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService

/**
 * External annotator for Arend files that provides error highlighting in headless mode.
 * 
 * This annotator is designed to work with MainPassesRunner which operates on VirtualFile 
 * objects without editors. It supplements the editor-based ErrorHighlightingPass by 
 * providing the same error information through the ExternalAnnotator API.
 */
class ArendExternalAnnotator : ExternalAnnotator<ArendFile, List<ArendExternalAnnotator.ErrorInfo>>() {

    data class ErrorInfo(
        val message: String,
        val textRange: TextRange,
        val severity: HighlightSeverity
    )

    override fun collectInformation(file: PsiFile): ArendFile? {
        return file as? ArendFile
    }

    override fun doAnnotate(file: ArendFile): List<ErrorInfo> {
        val module = runReadAction { file.moduleLocation } ?: return emptyList()
        val errors = file.project.service<ArendServerService>().server.errorMap[module] ?: return emptyList()

        return errors.mapNotNull { error ->
            runReadAction {
                val cause = BasePass.getImprovedCause(error)
                if (cause != null && cause.containingFile == file) {
                    val textRange = BasePass.getImprovedTextRange(error) ?: cause.textRange
                    ErrorInfo(
                        message = error.shortMessage ?: "",
                        textRange = textRange,
                        severity = BasePass.levelToSeverity(error.level)
                    )
                } else null
            }
        }
    }

    override fun apply(file: PsiFile, errors: List<ErrorInfo>, holder: AnnotationHolder) {
        for (error in errors) {
            holder.newAnnotation(error.severity, error.message)
                .range(error.textRange)
                .create()
        }
    }
}
