package org.arend.highlight

import com.intellij.codeHighlighting.DirtyScopeTrackingHighlightingPassFactory
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.FileStatusMap
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

abstract class BasePassFactory<T : PsiFile>(private val clazz: Class<T>) : DirtyScopeTrackingHighlightingPassFactory {
    abstract fun createPass(file: T, editor: Editor, textRange: TextRange): TextEditorHighlightingPass?

    protected open fun allowWhiteSpaces() = false

    /**
     * The daemon highlights every showing editor of a document, and the Find Usages preview and the
     * diff viewers are editors over the very same document as the file editor. Highlighting them
     * separately is pointless (the highlighters live in the shared document markup model, so they
     * show up there anyway) and only doubles the number of sessions competing over that model.
     */
    protected open fun acceptsEditor(editor: Editor) =
        editor.editorKind != EditorKind.PREVIEW && editor.editorKind != EditorKind.DIFF

    override fun createHighlightingPass(file: PsiFile, editor: Editor): TextEditorHighlightingPass? {
        if (!clazz.isInstance(file) || !acceptsEditor(editor)) {
            return null
        }

        val textRange = FileStatusMap.getDirtyTextRange(editor.document, file, passId)
        return if (textRange != null) createPass(clazz.cast(file), editor, textRange) else EmptyHighlightingPass(file.project, editor.document)
    }
}
