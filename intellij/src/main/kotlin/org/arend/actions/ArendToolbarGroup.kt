package org.arend.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.Project
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.PrintOptionKind

class ArendToolbarGroup : ActionGroup() {
    private val actions = mutableMapOf<Project, List<AnAction>>()

    override fun getChildren(eNull: AnActionEvent?): Array<AnAction> {
    val e = eNull ?: return emptyArray()
    val project = e.project ?: return emptyArray()
    if (project.isDisposed) return emptyArray()
    if (actions[project] == null) {
        actions.put(project, listOf(ArendNormalizeToggleAction, ArendPrintOptionsActionGroup(project, PrintOptionKind.POPUP_PRINT_OPTIONS)))
    }
    return arrayOf(
        Separator.getInstance(),
        *(actions[project]?.toTypedArray() ?: emptyArray()),
        Separator.getInstance()
    )
  }
}