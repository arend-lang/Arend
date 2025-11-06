package org.arend.highlight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModuleLocation
import org.arend.naming.resolving.CollectingResolverListener
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.EmptyScope
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.settings.ArendSettings
import org.arend.term.abs.ConcreteBuilder
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.*
import org.arend.typechecking.runner.RunnerService

class ArendHighlightingPass(file: IArendFile, editor: Editor, textRange: TextRange)
    : BasePass(file, editor, "Arend resolver annotator", textRange) {

    private val module = (file as? ArendFile)?.moduleLocation

    public override fun collectInformationWithProgress(progress: ProgressIndicator) {
        progress.isIndeterminate = true
        val server = myProject.service<ArendServerService>().server
        val visitor = HighlightingVisitor(this, server.typingInfo)

        if (module == null) {
            val expr = (file as? ArendCodeFragment)?.expr ?: return
            val concrete = ConcreteBuilder.convertExpression(expr)
            // Maybe this should be moved to ArendChecker
            val resolverListener = CollectingResolverListener(ArendServerRequesterImpl(myProject), true)
            ExpressionResolveNameVisitor(EmptyScope.INSTANCE, ArrayList(), server.typingInfo, DummyErrorReporter.INSTANCE, null, resolverListener).resolve(concrete)
            concrete.accept(visitor, null)
            for (resolvedReference in resolverListener.getCacheStructure(null)?.cache ?: emptyList()) {
                (resolvedReference.reference as? ArendReferenceElement)?.putResolved(resolvedReference.referable)
            }
            file.fragmentResolved()
        } else {
            server.getCheckerFor(listOf(module)).resolveModules(ProgressCancellationIndicator(progress), ProgressReporter.empty())
            for (definitionData in server.getResolvedDefinitions(module)) {
                definitionData.definition.accept(visitor, null)
            }
        }
    }

    override fun applyInformationWithProgress() {
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