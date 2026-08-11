package org.arend.toolWindow.errors

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.MutableBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import org.arend.ext.module.ModuleLocation
import org.arend.server.ArendServerService
import org.arend.settings.ArendProjectSettings


@Service(Service.Level.PROJECT)
class ArendMessagesService(private val project: Project) {
    var view: ArendMessagesView? = null
        private set
    var isGoalTextPinned: Boolean = false
    var isErrorTextPinned: Boolean = false
    private val arendProjectSettings = project.service<ArendProjectSettings>().apply {
        data.isShowImplicitGoals = true
    }

    var isShowImplicitGoals: MutableBooleanProperty =
            AtomicBooleanProperty(arendProjectSettings.data.isShowImplicitGoals).apply {
                afterChange { arendProjectSettings.data.isShowImplicitGoals = it }
            }

    var isShowErrorsOrInfoPanel: MutableBooleanProperty =
            AtomicBooleanProperty(arendProjectSettings.data.isShowErrorsOrInfoPanel).apply {
                afterChange { arendProjectSettings.data.isShowErrorsOrInfoPanel = it }
            }
    var isShowGoalsInErrorsPanel: MutableBooleanProperty =
            AtomicBooleanProperty(arendProjectSettings.data.isShowGoalsInErrorsPanel).apply {
                afterChange { arendProjectSettings.data.isShowGoalsInErrorsPanel = it }
            }

    var isEnabledWrapPanel: MutableBooleanProperty =
            AtomicBooleanProperty(arendProjectSettings.data.goalPrintingOptions.enableWrap).apply {
                afterChange {
                    arendProjectSettings.data.goalPrintingOptions.enableWrap = it
                    updateGoalText()
                }
            }

    fun activate(project: Project, selectFirst: Boolean) {
        runInEdt {
            ToolWindowManager.getInstance(project).getToolWindow(ArendMessagesFactory.TOOL_WINDOW_ID)?.activate(if (selectFirst) Runnable {
                val service = project.service<ArendMessagesService>()
                val view = service.view
                if (view != null) {
                    view.update()
                    view.tree.selectFirst()
                }
            } else null, false, false)
        }
    }

    fun initView(toolWindow: ToolWindow) {
        view = ArendMessagesView(project, toolWindow)
    }

    fun update(module: ModuleLocation? = null) {
        val view = view
        if (view == null) {
            if (project.service<ArendServerService>().server.hasErrors()) {
                activate(project, true)
            }
        } else {
            view.update()
        }
    }

    fun updateEditors() {
        view?.updateEditors()
    }

    fun updateGoalText() {
        view?.updateGoalText()
    }

    fun updateErrorText() {
        view?.updateErrorText()
    }

    fun clearGoalEditor() {
        view?.clearGoalEditor()
    }
}