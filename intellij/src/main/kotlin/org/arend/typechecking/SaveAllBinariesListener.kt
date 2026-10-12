package org.arend.typechecking

import com.intellij.ide.actions.SaveAllAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import org.arend.util.arendModules

// Save All saves the binary cache of the project too, as closing the project does
class SaveAllBinariesListener : AnActionListener {
    override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
        val project = event.project
        if (action !is SaveAllAction || !result.isPerformed || project == null || project.isDisposed || project.arendModules.isEmpty()) return
        // Once the action is over: saving takes a modal progress
        ApplicationManager.getApplication().invokeLater({ project.service<BinaryFileSaver>().saveAll() }, project.disposed)
    }
}
