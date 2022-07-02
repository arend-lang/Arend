package org.arend.injection

import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.arend.core.context.param.DependentLink
import org.arend.core.expr.Expression
import org.arend.ext.concrete.ConcreteSourceNode
import org.arend.ext.core.context.CoreParameter
import org.arend.ext.core.expr.CoreExpression
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.error.GeneralError
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.doc.Doc
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.ext.reference.DataContainer
import org.arend.injection.actions.RevealingInformationCaretListener
import org.arend.injection.actions.UnblockingDocumentAction
import org.arend.injection.actions.withNormalizedTerms
import org.arend.naming.reference.Referable
import org.arend.naming.reference.Reference
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.*
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.impl.MetaAdapter
import org.arend.resolving.ArendReferableConverter
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.toolWindow.errors.ArendPrintOptionsFilterAction
import org.arend.toolWindow.errors.PrintOptionKind
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

abstract class InjectedArendEditor(
    val project: Project,
    name: String,
    var treeElement: ArendErrorTreeElement?,
) {
    protected val editor: Editor?
    private val panel: JPanel?
    protected val actionGroup: DefaultActionGroup = DefaultActionGroup()

    protected abstract val printOptionKind: PrintOptionKind

    var currentDoc: Doc? = null

    val verboseLevelMap: MutableMap<Expression, Int> = mutableMapOf()
    val verboseLevelParameterMap: MutableMap<DependentLink, Int> = mutableMapOf()

    init {
        val psi = ArendPsiFactory(project, name).injected("")
        val virtualFile = psi.virtualFile
        editor = if (virtualFile != null) {
            PsiDocumentManager.getInstance(project).getDocument(psi)?.let { document ->
                document.setReadOnly(true)
                EditorFactory.getInstance().createEditor(document, project, virtualFile, false).apply {
                    settings.setGutterIconsShown(false)
                    settings.isRightMarginShown = false
                    putUserData(AREND_GOAL_EDITOR, this@InjectedArendEditor)
                    caretModel.addCaretListener(RevealingInformationCaretListener(this@InjectedArendEditor))
                }
            }
        } else null

        if (editor != null) {
            panel = JPanel(BorderLayout())
            panel.add(editor.component, BorderLayout.CENTER)

            val toolbar = ActionManager.getInstance().createActionToolbar("ArendEditor.toolbar", actionGroup, false)
            toolbar.targetComponent = panel
            panel.add(toolbar.component, BorderLayout.WEST)
        } else {
            panel = null
        }

        updateErrorText()
    }

    fun release() {
        if (editor != null) {
            editor.putUserData(AREND_GOAL_EDITOR, null)
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    val component: JComponent?
        get() = panel

    fun updateErrorText(id: String? = null, postWriteCallback: () -> Unit = {}) {
        if (editor == null) return
        val treeElement = treeElement ?: return

        invokeLater {
            val builder = StringBuilder()
            val visitor = CollectingDocStringBuilder(builder, treeElement.sampleError.error)
            var fileScopes = Scopes.EMPTY

            runReadAction {
                var first = true
                for (arendError in treeElement.errors) {
                    if (first) {
                        first = false
                    } else {
                        builder.append("\n\n")
                    }

                    val error = arendError.error
                    val (resolve, scopes) = resolveCauseReference(error)
                    if (scopes != null) {
                        fileScopes = scopes
                    }
                    val ppConfig = getCurrentConfig(scopes?.expressionScope)
                    val doc = if (causeIsMetaExpression(error.causeSourceNode, resolve))
                        error.getDoc(ppConfig).withNormalizedTerms(treeElement.normalizationCache, ppConfig)
                    else
                        DocFactory.vHang(
                            error.getHeaderDoc(ppConfig).withNormalizedTerms(treeElement.normalizationCache, ppConfig),
                            error.getBodyDoc(ppConfig).withNormalizedTerms(treeElement.normalizationCache, ppConfig)
                        )
                    currentDoc = doc
                    doc.accept(visitor, false)
                }
            }
            val text = builder.toString()
            if (editor.isDisposed) return@invokeLater
            val action: () -> Unit = {
                UndoManager.getInstance(project).undoableActionPerformed(UnblockingDocumentAction(editor.document, id, false))
                modifyDocument { setText(text) }
                getInjectionFile()?.apply {
                    injectionRanges = visitor.textRanges
                    scopes = fileScopes
                    injectedExpressions = visitor.expressions
                }
                postWriteCallback()
                UndoManager.getInstance(project).undoableActionPerformed(UnblockingDocumentAction(editor.document, id, true))
            }
            WriteCommandAction.runWriteCommandAction(project, null, id, action)
            val support = EditorHyperlinkSupport.get(editor)
            support.clearHyperlinks()
            for (hyperlink in visitor.hyperlinks) {
                support.createHyperlink(hyperlink.first.startOffset, hyperlink.first.endOffset, null, hyperlink.second)
            }
        }
    }

    fun getCurrentConfig(scope: Scope?): PrettyPrinterConfig {
        return ProjectPrintConfig(
            project,
            printOptionKind,
            scope?.let { CachingScope.make(ConvertingScope(ArendReferableConverter, it)) },
            verboseLevelMap,
            verboseLevelParameterMap
        )
    }

    fun addDoc(doc: Doc, docScopes: Scopes) {
        if (editor == null) return

        val builder = StringBuilder()
        val visitor = CollectingDocStringBuilder(builder, treeElement?.sampleError?.error)
        doc.accept(visitor, false)
        builder.append('\n')
        val text = builder.toString()
        ApplicationManager.getApplication().invokeLater { runUndoTransparentWriteAction {
            if (editor.isDisposed) return@runUndoTransparentWriteAction
            val document = editor.document
            val length = document.textLength
            modifyDocument { insertString(textLength, text) }
            editor.scrollingModel.scrollTo(editor.offsetToLogicalPosition(length + text.length), ScrollType.MAKE_VISIBLE)

            getInjectionFile()?.apply {
                scopes = docScopes
                injectionRanges.addAll(visitor.textRanges.map { list -> list.map { it.shiftRight(length) } })
                injectedExpressions.addAll(visitor.expressions)
            }

            val support = EditorHyperlinkSupport.get(editor)
            for (hyperlink in visitor.hyperlinks) {
                support.createHyperlink(length + hyperlink.first.startOffset, length + hyperlink.first.endOffset, null, hyperlink.second)
            }
        } }
    }

    fun clearText() {
        editor ?: return
        ApplicationManager.getApplication().invokeLater {
            if (editor.isDisposed) return@invokeLater
            runWriteAction {
                getInjectionFile()?.apply {
                    injectionRanges.clear()
                    injectedExpressions.clear()
                }
                modifyDocument { setText("") }
                currentDoc = null
            }
        }
    }

    fun getInjectionFile(): PsiInjectionTextFile? = editor?.document?.let {
        PsiDocumentManager.getInstance(project).getPsiFile(it) as? PsiInjectionTextFile
    }

    private class ProjectPrintConfig(
        project: Project,
        printOptionsKind: PrintOptionKind,
        scope: Scope?,
        private val verboseLevelMap: Map<Expression, Int>,
        private val verboseLevelParameterMap: Map<DependentLink, Int>
    ) :
        PrettyPrinterConfigWithRenamer(scope) {
        private val flags = ArendPrintOptionsFilterAction.getFilterSet(project, printOptionsKind)

        override fun getExpressionFlags() = flags

        override fun getVerboseLevel(expression: CoreExpression): Int {
            return verboseLevelMap[expression] ?: 0
        }

        override fun getVerboseLevel(parameter: CoreParameter): Int {
            return verboseLevelParameterMap[parameter] ?: 0
        }

        override fun getNormalizationMode(): NormalizationMode? {
            return null
        }
    }

    fun getOffsetInEditor(relativeOffsetInInjection: Int, indexOfInjection : Int) : Int? {
        if (indexOfInjection == -1) {
            return null
        }
        val ranges = getInjectionFile()?.injectionRanges?.getOrNull(indexOfInjection) ?: return null
        var mutableRelativeOffset = relativeOffsetInInjection
        val text = editor?.document?.text ?: return null
        for (range in ranges) {
            val substring = text.subSequence(range.startOffset, range.endOffset)
            val initialSpaces = substring.takeWhile { it.isWhitespace() }.length
            val actualRangeLength = range.length - initialSpaces
            if (mutableRelativeOffset >= actualRangeLength) {
                mutableRelativeOffset -= actualRangeLength + 1 // for space
            } else {
                return range.startOffset + mutableRelativeOffset + initialSpaces
            }
        }
        return null
    }

    protected fun modifyDocument(modifier: Document.() -> Unit) {
        val thisEditor = editor ?: return
        thisEditor.document.setReadOnly(false)
        try {
            thisEditor.document.modifier()
        } finally {
            thisEditor.document.setReadOnly(true)
        }
    }

    companion object {
        val AREND_GOAL_EDITOR: Key<InjectedArendEditor> = Key.create("Arend goal editor")

        fun resolveCauseReference(error: GeneralError): Pair<Referable?, Scopes?> {
            val causeSourceNode = error.causeSourceNode
            val data = (causeSourceNode?.data as? DataContainer)?.data ?: causeSourceNode?.data
            val unresolvedRef = (data as? Reference)?.referent
            val scopes =
                if (unresolvedRef != null || error.hasExpressions())
                    (data as? PsiElement)?.ancestor<ArendCompositeElement>()?.scopes?.caching()
                else null
            val ref =
                if (unresolvedRef != null && scopes != null)
                    ExpressionResolveNameVisitor.resolve(unresolvedRef, scopes.expressionScope)
                else null
            return Pair(ref, scopes)
        }

        fun causeIsMetaExpression(cause: ConcreteSourceNode?, resolve: Referable?) =
            (resolve as? MetaAdapter)?.metaRef?.definition != null &&
                    (cause as? Concrete.ReferenceExpression)?.referent != resolve
    }
}