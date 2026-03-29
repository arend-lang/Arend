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
 * 
 * In normal (non-headless) mode, the annotator does not create annotations to avoid
 * conflicts with ErrorHighlightingPass. However, external tools can use the companion
 * object's [getErrorsForFile] method to retrieve error information.
 */
class ArendExternalAnnotator : ExternalAnnotator<ArendFile, List<ArendExternalAnnotator.ErrorInfo>>() {

    data class ErrorInfo(
        val message: String,
        val textRange: TextRange,
        val severity: HighlightSeverity
    )

    companion object {
        /**
         * Get error information for an Arend file.
         * This method can be called by external tools to retrieve error data
         * without creating annotations in the editor.
         * 
         * @param file The Arend file to get errors for
         * @return List of error information for the file
         */
        fun getErrorsForFile(file: ArendFile): List<ErrorInfo> {
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
    }

    override fun collectInformation(file: PsiFile): ArendFile? {
        return file as? ArendFile
    }

    override fun doAnnotate(file: ArendFile): List<ErrorInfo> {
        return getErrorsForFile(file)
    }

    override fun apply(file: PsiFile, errors: List<ErrorInfo>, holder: AnnotationHolder) {
        // Don't create annotations in normal mode - ErrorHighlightingPass handles this.
        // This annotator is only used to provide error info for external tools via getErrorsForFile().
    }
}
