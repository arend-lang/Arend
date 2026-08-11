package org.arend.toolWindow.errors

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import org.arend.util.ArendBundle

class EnableWrapAction : ToggleAction(
    ArendBundle.message("arend.show.enable.wrap.action.name"),
    ArendBundle.message("arend.show.enable.wrap.action.description"),
    AllIcons.Actions.ListFiles
), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isSelected(e: AnActionEvent): Boolean =
        e.project?.service<ArendMessagesService>()?.isEnabledWrapPanel?.get() ?: false

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        e.project?.service<ArendMessagesService>()?.isEnabledWrapPanel?.set(state)
    }
}
