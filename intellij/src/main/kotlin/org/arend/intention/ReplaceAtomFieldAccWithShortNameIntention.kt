package org.arend.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.descendantsOfType
import org.arend.intention.ReplaceLongNameWithShortNameIntention.Companion.isApplicableTo
import org.arend.psi.ext.ArendAtomFieldsAcc
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.refactoring.RenameReferenceAction
import org.arend.util.ArendBundle
import kotlin.sequences.forEach

class ReplaceAtomFieldAccWithShortNameIntention: SelfTargetingIntention<ArendAtomFieldsAcc>(ArendAtomFieldsAcc::class.java, ArendBundle.message("arend.import.replaceWithShortName")) {
  override fun isApplicableTo(element: ArendAtomFieldsAcc, caretOffset: Int, editor: Editor): Boolean {
    return element.fieldAccList.isNotEmpty() && element.fieldAccList.all { field -> field.refIdentifier?.resolve is PsiLocatedReferable } &&
      isApplicableTo(element.fieldAccList.last().refIdentifier ?: return false)
  }

  override fun applyTo(element: ArendAtomFieldsAcc, project: Project, editor: Editor) {
    val currentRefIdentifier = element.fieldAccList.last().refIdentifier
    val target = currentRefIdentifier?.resolve
    val containingGroup = element.containingFile
    if (target is PsiLocatedReferable && containingGroup != null)
      containingGroup.descendantsOfType<ArendAtomFieldsAcc>().mapNotNull { it.fieldAccList.lastOrNull()?.refIdentifier }
        .filter { it.resolve == target && isApplicableTo(it) }.forEach {
          RenameReferenceAction(it, it.longName, target, true).execute(if (it == currentRefIdentifier) editor else null)
        }
  }
}
