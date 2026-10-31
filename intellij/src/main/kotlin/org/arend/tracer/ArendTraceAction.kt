package org.arend.tracer

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.ui.LayeredIcon
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import org.arend.actions.ArendPopupAction
import org.arend.codeInsight.ArendPopupHandler
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.FullName
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.collectArendExprs
import org.arend.refactoring.selectedExpr
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.typechecking.runner.RunnerService
import org.arend.util.ArendBundle

class ArendTraceAction : ArendPopupAction() {
    init {
        templatePresentation.icon = TRACE_ICON
    }

    override fun update(presentation: Presentation, project: Project, editor: Editor, file: PsiFile) {
        if (file !is ArendFile) {
            presentation.isEnabled = false
            return
        }
        val expressionAtCaret = getExpressionAtCaret(file, editor)
        if (expressionAtCaret != null) {
            presentation.isEnabled = true
            presentation.text = "Trace to Expression"
            return
        }
        val declarationAtCaret = getDeclarationAtCaret(file, editor)
        if (declarationAtCaret != null) {
            presentation.isEnabled = true
            presentation.text = "Trace '${declarationAtCaret.second.representableName}'"
            return
        }
        presentation.isEnabled = false
    }

    override fun getHandler() = object : ArendPopupHandler(requestFocus) {
        override fun invoke(project: Project, editor: Editor, file: PsiFile) {
            val (expression, definitionRef) = getElementAtCursor(file, editor)
                ?: return displayErrorHint(editor, ArendBundle.message("arend.trace.action.cannot.find.expression"))
            val notFoundMessage = ArendBundle.message("arend.trace.action.cannot.find.concrete.definition", definitionRef.representableName)
            val module = definitionRef.location ?: return displayErrorHint(editor, notFoundMessage)
            val factory = ArendTracingCheckerFactory()
            var tracingData: ArendTracingData? = null
            project.service<RunnerService>().runChecker(module, definitionRef.refLongName, factory, null, {
                tracingData = runReadAction { factory.getTracingData(expression) }
            }) {
                val data = tracingData ?: return@runChecker displayErrorHint(editor, notFoundMessage)
                val starter = object : XDebugProcessStarter() {
                    override fun start(session: XDebugSession): XDebugProcess = ArendTraceProcess(session, data)
                }
                XDebuggerManager.getInstance(project).startSessionAndShowTab(
                    definitionRef.representableName,
                    TRACE_ICON,
                    null,
                    false,
                    starter
                )
            }
        }
    }

    companion object {
        private val TRACE_ICON = LayeredIcon.create(AllIcons.Actions.ListFiles, AllIcons.Nodes.RunnableMark)

        fun getElementAtRange(file: PsiFile, editor: Editor): Pair<ArendExpr, FullName>? {
            val range = EditorUtil.getSelectionInAnyMode(editor)
            val sExpr = selectedExpr(file, range)
            if (sExpr != null) {
                val def = sExpr.ancestor<ReferableBase<*>>()?.fullName
                if (def != null) {
                    return Pair(sExpr, def)
                }
            }
            return null
        }

        fun getElementAtCursor(file: PsiFile, editor: Editor): Pair<ArendExpr, TCDefReferable>? {
            return getExpressionAtCaret(file, editor) ?: getDeclarationAtCaret(file, editor)
        }

        private fun getExpressionAtCaret(file: PsiFile, editor: Editor): Pair<ArendExpr, TCDefReferable>? {
            val range = EditorUtil.getSelectionInAnyMode(editor)
            val sExpr = selectedExpr(file, range)
            if (sExpr != null) {
                val expression = collectArendExprs(sExpr.parent, range)?.first as? ArendExpr
                if (expression != null) {
                    val def = sExpr.ancestor<ReferableBase<*>>()?.tcReferable
                    if (def != null) {
                        return Pair(expression, def)
                    }
                }
            }
            return null
        }

        private fun getDeclarationAtCaret(file: PsiFile, editor: Editor): Pair<ArendExpr, TCDefReferable>? {
            val def = file.findElementAt(editor.caretModel.currentCaret.offset)?.ancestor<ReferableBase<*>>() ?: return null
            val ref = def.tcReferable ?: return null
            val head = when (def) {
                is ArendFunctionDefinition<*> -> {
                    val body = def.body ?: return null
                    val expr = body.expr
                    if (expr != null) expr else {
                        val clauses = body.clauseList
                        if (clauses.isNotEmpty()) clauses[0].expression else body.coClauseList.firstOrNull()?.expr
                    }
                }
                is ArendDefData -> {
                    val body = def.dataBody ?: return null
                    val constructors = body.constructorList
                    val constructor = constructors.find {
                        it.parameters.isNotEmpty()
                    } ?: body.constructorClauseList.find {
                            it.constructors.any { constructor -> constructor.parameters.isNotEmpty() }
                        }?.constructors?.find {
                            it.parameters.isNotEmpty()
                        }
                    constructor?.parameters?.firstOrNull()?.type
                }
                is ArendDefClass -> {
                    val classStat = def.classStatList.firstOrNull()
                    classStat?.classField?.returnExpr?.type
                        ?: classStat?.classImplement?.expr?.type
                        ?: classStat?.coClause?.expr?.type
                        ?: classStat?.overriddenField?.returnExpr?.type
                        ?: def.classFieldList.firstOrNull()?.returnExpr?.type
                        ?: def.classImplementList.firstOrNull()?.expr?.type
                        ?: def.fieldTeleList.firstOrNull()?.type
                }
                else -> return null
            } ?: return null
            return Pair(head, ref)
        }

        /**
         * Synchronously typechecks a renamed copy of [definitionRef] with a tracing checker.
         * Returns `null` if the definition cannot be found.
         */
        internal fun runTracingTypechecker(
            project: Project,
            definitionRef: TCDefReferable,
            expression: ArendExpr
        ): ArendTracingData? {
            val module = definitionRef.location ?: return null
            val factory = ArendTracingCheckerFactory()
            project.service<ArendServerService>().server.getCheckerFor(listOf(module))
                .typecheck(FullName(module, definitionRef.refLongName), factory, null, DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
            return runReadAction { factory.getTracingData(expression) }
        }
    }
}