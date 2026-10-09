package org.arend.arc

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.arend.util.ArendBundle
import java.util.function.Function
import javax.swing.JComponent

class ArcFileNotificationProvider : EditorNotificationProvider {
    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (virtualFile !is ArcVirtualFile) return null
        val service = project.service<ArcViewService>()
        if (service.isCancelled(virtualFile)) {
            return Function { createPanel(it, virtualFile, "arend.arc.decompilation.cancelled", EditorNotificationPanel.Status.Warning) }
        }
        if (project.service<ArcUnloadedModuleService>().containsUnloadedModule(virtualFile)) {
            return Function { createPanel(it, virtualFile, "arend.arc.retypecheck", EditorNotificationPanel.Status.Info) }
        }
        return null
    }

    // Preparing the view again loads or typechecks the module
    private fun createPanel(editor: FileEditor, file: ArcVirtualFile, message: String, status: EditorNotificationPanel.Status): EditorNotificationPanel {
        val panel = EditorNotificationPanel(editor, status)
        panel.text = ArendBundle.message(message, file.name)
        panel.createActionLabel(ArendBundle.message("arend.arc.decompile", file.name)) {
            file.project.service<ArcViewService>().prepare(file)
        }
        return panel
    }
}
