@file:Suppress("UnstableApiUsage")

package org.arend.intention.generating

import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import java.util.concurrent.atomic.AtomicReference

internal class LetWrappingOptionEditorRenderer(
        private val editor: Editor,
        private val project: Project,
        private val commandGroupId: String?
) : Disposable {
    private val insertedRangeReference: AtomicReference<TextRange?> = AtomicReference(null)
    private val highlighterReference: AtomicReference<ScopeHighlighter?> = AtomicReference(ScopeHighlighter(editor))

    private inline fun executeWriteCommand(crossinline action: () -> Unit) {
        executeCommand(project, null, commandGroupId) { runWriteAction(action) }
    }

    fun cleanup() {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val range = insertedRangeReference.getAndSet(null)
        if (range != null) {
            executeWriteCommand {
                editor.document.deleteString(range.startOffset, range.endOffset)
                PsiDocumentManager.getInstance(project).commitDocument(editor.document)
            }
        }
        highlighterReference.getAndSet(null)?.dropHighlight()
    }

    fun renderOption(offset: Int) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        cleanup()
        val document = editor.document
        val highlighter = highlighterReference.get()
        executeWriteCommand {
            @NlsSafe val text = "\\let ... \\in "
            document.insertString(offset, text)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            val range = TextRange(offset, offset + text.length)
            insertedRangeReference.set(range)
            val rangeToHighlight = range.grown(-1)
            highlighter?.highlight(Pair.create(rangeToHighlight, listOf(rangeToHighlight)))
        }
    }

    override fun dispose() = cleanup()
}