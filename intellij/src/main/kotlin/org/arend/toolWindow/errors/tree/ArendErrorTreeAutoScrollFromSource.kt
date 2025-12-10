package org.arend.toolWindow.errors.tree

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.ui.AutoScrollFromSourceHandler
import com.intellij.ui.UIBundle
import org.arend.actions.selectErrorFromEditor
import org.arend.settings.ArendProjectSettings
import org.arend.toolWindow.errors.MessageType
import java.util.*
import kotlin.collections.HashMap


class ArendErrorTreeAutoScrollFromSource(private val project: Project, private val tree: ArendErrorTree) : AutoScrollFromSourceHandler(project, tree, project) {
    init {
        install()
    }

    private var actionMap = HashMap<MessageType, MyAction>()

    fun setEnabled(types: EnumSet<MessageType>, enabled: Boolean) {
        // Do not mutate template presentations directly (IDEA platform restriction).
        // The enabled state is computed dynamically in MyAction.update() from settings.
        // Trigger selection update when enabling relevant types to keep UI in sync.
        val filterSet = project.service<ArendProjectSettings>().autoScrollFromSource
        if (enabled && filterSet.intersect(types).isNotEmpty()) {
            updateCurrentSelection()
        }
    }

    override fun install() {
        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                selectInAlarm(event.editor)
            }
        }, project)
    }

    private fun selectInAlarm(editor: Editor?) {
        if (editor != null && tree.isShowing && isAutoScrollEnabled) {
            myAlarm.cancelAllRequests()
            myAlarm.addRequest({ selectElementFromEditor(editor) }, alarmDelay, modalityState)
        }
    }

    fun updateCurrentSelection() {
        val selectedEditor = (FileEditorManager.getInstance(myProject).selectedEditor as? TextEditor)?.editor
        if (selectedEditor != null) {
            runInEdt(ModalityState.nonModal()) {
                selectInAlarm(selectedEditor)
            }
        }
    }

    private fun selectElementFromEditor(editor: Editor) {
        if (editor.project == project) {
            selectErrorFromEditor(project, editor, null, always = false, activate = false)
        }
    }

    override fun selectElementFromEditor(editor: FileEditor) {
        (editor as? TextEditor)?.editor?.let { selectElementFromEditor(it) }
    }

    override fun setAutoScrollEnabled(enabled: Boolean) {}

    public override fun isAutoScrollEnabled(): Boolean {
        val service = project.service<ArendProjectSettings>()
        return service.autoScrollFromSource.contains(MessageType.GOAL) && service.messagesFilterSet.contains(MessageType.GOAL)
    }

    private inner class MyAction(private val type: MessageType) : ToggleAction("Autoscroll from ${type.toText()}s", null, null), DumbAware {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun update(e: AnActionEvent) {
            super.update(e)
            val settings = project.service<ArendProjectSettings>()
            val enabledByFilters = settings.messagesFilterSet.contains(type) &&
                (!(type == MessageType.RESOLVING || type == MessageType.PARSING) ||
                        (settings.autoScrollFromSource.contains(MessageType.SHORT) && settings.messagesFilterSet.contains(MessageType.SHORT)))
            e.presentation.isEnabled = enabledByFilters
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            val settings = project.service<ArendProjectSettings>()
            return settings.autoScrollFromSource.contains(type) && settings.messagesFilterSet.contains(type) &&
                (!(type == MessageType.RESOLVING || type == MessageType.PARSING) || settings.autoScrollFromSource.contains(MessageType.SHORT) && settings.messagesFilterSet.contains(MessageType.SHORT))
        }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
            val settings = project.service<ArendProjectSettings>()
            if (state) {
                settings.autoScrollFromSource.add(type)
                updateCurrentSelection()
            } else {
                settings.autoScrollFromSource.remove(type)
            }

            // Do not touch template presentations here; MyAction.update() handles enablement.
        }
    }

    fun createActionGroup() = DefaultActionGroup(UIBundle.message("autoscroll.from.source.action.description"), true).apply {
        templatePresentation.icon = AllIcons.General.AutoscrollFromSource
        for (type in MessageType.entries) {
            val action = MyAction(type)
            add(action)
            actionMap[type] = action
        }

        // Initial enablement will be handled dynamically in MyAction.update().
    }
}
