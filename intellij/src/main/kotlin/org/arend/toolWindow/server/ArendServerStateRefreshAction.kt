package org.arend.toolWindow.server

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ArendServerStateRefreshAction(private val onRefresh: () -> Unit) : AnAction("Refresh", "Refresh Arend server state", AllIcons.Actions.Refresh) {
    override fun actionPerformed(e: AnActionEvent) {
        onRefresh()
    }
}
