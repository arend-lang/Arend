package org.arend.toolWindow.server

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import org.arend.server.ArendServerService

@Service(Service.Level.PROJECT)
class ArendServerStateService(private val project: Project) {
    var view: ArendServerStateView? = null
        private set

    fun initView(toolWindow: ToolWindow) {
        view = ArendServerStateView(project, toolWindow)
        // Subscribe to server error events to refresh the tree when something changes
        val server = project.service<ArendServerService>().server
        server.addErrorReporter { _ ->
            // Debounce on EDT
            ApplicationManager.getApplication().invokeLater {
                refresh()
            }
        }
    }

    fun refresh() {
        view?.refresh()
    }
}
