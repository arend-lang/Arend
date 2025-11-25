package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.naming.reference.Referable
import org.arend.psi.ext.ArendNsId
import org.arend.psi.ext.ArendNsUsing
import org.arend.psi.ext.ArendStatCmd
import org.arend.refactoring.doAddIdToHiding
import org.arend.refactoring.doRemoveRefFromStatCmd
import org.arend.term.group.ConcreteNamespaceCommand
import org.arend.util.ArendBundle
import java.util.Collections.singletonList

class HideImportQuickFix(private val cause: Any,
                         private val referable: Referable?): IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getText(): String = ArendBundle.message("arend.import.hide")

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        when (cause) {
            is ConcreteNamespaceCommand.NameRenaming -> {
                val nsId = cause.data() as? ArendNsId
                val using = nsId?.parent as? ArendNsUsing
                val statCmd = using?.parent
                if (using is ArendNsUsing && statCmd is ArendStatCmd)
                    doRemoveRefFromStatCmd(nsId.refIdentifier, false)
            }
            is ConcreteNamespaceCommand -> {
                val statCmd = cause.data() as? ArendStatCmd
                if (referable != null && statCmd != null)
                    doAddIdToHiding(statCmd, singletonList(referable))
            }
        }
    }
}