package org.arend.toolWindow.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import org.arend.server.ArendServerListener
import org.arend.server.ArendServerService

@Service(Service.Level.PROJECT)
class ArendServerStateService(private val project: Project) : Disposable {
    var view: ArendServerStateView? = null
        private set

    private var serverListener: ArendServerListener? = null

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
        // Subscribe to server structural events (libraries/modules updated/removed)
        val listener = object : ArendServerListener {
            private fun queueRefresh() {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) refresh()
                }
            }
            override fun onLibraryUpdated(libraryName: String) = queueRefresh()
            override fun onLibraryRemoved(libraryName: String) = queueRefresh()
            override fun onLibrariesUnloaded(onlyInternal: Boolean) = queueRefresh()
            override fun onModuleUpdated(module: org.arend.ext.module.ModuleLocation) = queueRefresh()
            override fun onModuleRemoved(module: org.arend.ext.module.ModuleLocation) = queueRefresh()
            override fun onModuleResolved(module: org.arend.ext.module.ModuleLocation) = queueRefresh()
            override fun onTypecheckingFinished() = queueRefresh()
        }
        server.addListener(listener)
        serverListener = listener
    }

    fun refresh() {
        view?.refresh()
    }

    override fun dispose() {
        val srv = project.service<ArendServerService>().server
        serverListener?.let { srv.removeListener(it) }
        serverListener = null
    }
}
