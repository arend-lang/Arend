package org.arend.quickfix

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.IntentionWrapper
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.arend.psi.containsInside
import org.arend.psi.parentsWithSelf

// code borrowed from kotlin plugin

@Suppress("EqualsOrHashCode")
abstract class SelfTargetingIntention<T : PsiElement>(
        val elementType: Class<T>,
        private var text: String,
        private val familyName: String = text
) : IntentionAction {

    protected val defaultText: String = text

    protected fun setText(text: String) {
        this.text = text
    }

    final override fun getText() = text
    final override fun getFamilyName() = familyName

    abstract fun isApplicableTo(element: T, caretOffset: Int): Boolean

    abstract fun applyTo(element: T, editor: Editor?)

    private fun getTarget(editor: Editor, file: PsiFile): T? {
        val offset = editor.caretModel.offset
        val leaf1 = file.findElementAt(offset)
        val leaf2 = file.findElementAt(offset - 1)
        val commonParent = if (leaf1 != null && leaf2 != null) PsiTreeUtil.findCommonParent(leaf1, leaf2) else null

        var elementsToCheck: Sequence<PsiElement> = emptySequence()
        if (leaf1 != null) {
            elementsToCheck += leaf1.parentsWithSelf.takeWhile { it != commonParent }
        }
        if (leaf2 != null) {
            elementsToCheck += leaf2.parentsWithSelf.takeWhile { it != commonParent }
        }
        if (commonParent != null && commonParent !is PsiFile) {
            elementsToCheck += commonParent.parentsWithSelf
        }

        for (element in elementsToCheck) {
            @Suppress("UNCHECKED_CAST")
            if (elementType.isInstance(element) && isApplicableTo(element as T, offset)) {
                return element
            }
            if (!allowCaretInsideElement(element) && element.textRange.containsInside(offset)) break
        }
        return null
    }

    protected open fun allowCaretInsideElement(element: PsiElement): Boolean = true


    final override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean =
        getTarget(editor, file) != null

    final override fun invoke(project: Project, editor: Editor?, file: PsiFile) {
        editor ?: return
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val target = getTarget(editor, file) ?: return
        if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) return
        applyTo(target, editor)
    }

    override fun startInWriteAction() = true

    override fun toString(): String = getText()

    override fun equals(other: Any?): Boolean {
        if (other is IntentionWrapper) return this == other.action
        return other is SelfTargetingIntention<*> && javaClass == other.javaClass && text == other.text
    }

}

abstract class SelfTargetingRangeIntention<T : PsiElement>(
        elementType: Class<T>,
        text: String,
        familyName: String = text
) : SelfTargetingIntention<T>(elementType, text, familyName) {

    abstract fun applicabilityRange(element: T): TextRange?

    override final fun isApplicableTo(element: T, caretOffset: Int): Boolean {
        val range = applicabilityRange(element) ?: return false
        return range.containsOffset(caretOffset)
    }
}