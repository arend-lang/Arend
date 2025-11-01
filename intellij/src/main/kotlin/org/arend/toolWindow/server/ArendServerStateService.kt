package org.arend.toolWindow.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.util.messages.MessageBusConnection
import org.arend.server.ArendServerListener
import org.arend.server.ArendServerService

@Service(Service.Level.PROJECT)
class ArendServerStateService(private val project: Project) : Disposable {
    var view: ArendServerStateView? = null
        private set

    private var serverListener: ArendServerListener? = null
    private var toolWindow: ToolWindow? = null
    @Volatile private var pendingRefresh: Boolean = false
    private var connection: MessageBusConnection? = null

    fun initView(toolWindow: ToolWindow) {
        this.toolWindow = toolWindow
        view = ArendServerStateView(project, toolWindow)

        // Listen for tool window visibility changes to apply pending refreshes
        connection = project.messageBus.connect(this).also { conn ->
            conn.subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun toolWindowShown(toolWindow: ToolWindow) {
                    // When our tool window becomes visible, flush pending refresh
                    if (toolWindow == this@ArendServerStateService.toolWindow && pendingRefresh) {
                        pendingRefresh = false
                        // Ensure on EDT
                        ApplicationManager.getApplication().invokeLater {
                            if (!project.isDisposed) view?.refresh()
                        }
                    }
                }
            })
        }

        // Subscribe to server error events to refresh the tree when something changes
        val server = project.service<ArendServerService>().server
        server.addErrorReporter { _ ->
            requestRefresh()
        }
        // Subscribe to server structural events (libraries/modules updated/removed)
        val listener = object : ArendServerListener {
            private fun queueRefresh() = requestRefresh()
            override fun onLibraryUpdated(libraryName: String) = queueRefresh()
            override fun onLibraryRemoved(libraryName: String) = queueRefresh()
            override fun onLibrariesUnloaded(onlyInternal: Boolean) = queueRefresh()
            override fun onModuleRemoved(module: org.arend.ext.module.ModuleLocation) = queueRefresh()
            override fun onModuleResolved(module: org.arend.ext.module.ModuleLocation) = queueRefresh()
            override fun onTypecheckingFinished() = queueRefresh()
        }
        server.addListener(listener)
        serverListener = listener

        // Do an initial refresh only if visible now; otherwise, defer
        requestRefresh()
    }

    private fun requestRefresh() {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) return@invokeLater
            val tw = toolWindow
            if (tw == null || tw.isVisible) {
                view?.refresh()
            } else {
                pendingRefresh = true
            }
        }
    }

    override fun dispose() {
        val srv = project.service<ArendServerService>().server
        serverListener?.let { srv.removeListener(it) }
        serverListener = null
        connection?.disconnect()
        connection = null
        toolWindow = null
    }
}
