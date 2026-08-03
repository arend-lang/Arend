package org.arend.highlight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.IArendFile
import org.arend.ext.module.ModuleLocation
import org.arend.psi.*
import org.arend.psi.fragments.ArendExpressionCodeFragment
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.settings.ArendSettings
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.*
import org.arend.typechecking.runner.RunnerService
import org.arend.util.ArendFragmentUtils

class ArendHighlightingPass(file: IArendFile, editor: Editor, textRange: TextRange)
    : BasePass(file, editor, "Arend resolver annotator", textRange) {

    private val module = (file as? ArendFile)?.moduleLocation

    override fun collectHighlightingInfo(progress: ProgressIndicator): Boolean {
        if ((file as? ArendFile)?.isRepl == true) return true
        progress.isIndeterminate = true
        val server = myProject.service<ArendServerService>().server
        val visitor = HighlightingVisitor(this, server.typingInfo)

        if (module == null && file is ArendExpressionCodeFragment) {
            ArendFragmentUtils.resolveFragment(file, visitor, server)
        } else if (module != null) {
            server.getCheckerFor(listOf(module)).resolveModules(ProgressCancellationIndicator(progress), ProgressReporter.empty())
            // The module stays unresolved when the resolver gives up (the computation was
            // interrupted, a dependency changed under us, ...). Publishing the empty result would
            // drop the highlighting of the whole file until some later pass happens to succeed.
            if (!server.isResolved(module)) return false
            for (definitionData in server.getResolvedDefinitions(module)) {
                definitionData.definition.accept(visitor, null)
            }
        }
        collectHighlights()
        return true
    }

    override fun applyInformationWithProgress() {
        // Nothing happened in this session, another one owns the pass; do not touch the editor and
        // do not schedule the checker for a second time.
        if (!wasCollected()) return
        // Publishes the highlighting only if it was collected; the checker is started in any case,
        // a failed resolve is precisely when the module has to be revisited.
        super.applyInformationWithProgress()
        myProject.service<ArendMessagesService>().update(module)
        if (module?.locationKind == ModuleLocation.LocationKind.GENERATED) return

        if (module != null && service<ArendSettings>().isBackgroundTypechecking) {
            invokeLater {
                myProject.service<RunnerService>().runChecker(module)
            }
        } else if (!ApplicationManager.getApplication().isUnitTestMode) {
            DaemonCodeAnalyzer.getInstance(myProject).restart()
        }
    }
}