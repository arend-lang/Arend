package org.arend.arc

import com.intellij.CommonBundle
import com.intellij.ide.DeleteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.arend.util.ArendBundle
import java.io.IOException

/**
 * Deletes the .arc files and directories on disk that the selected .arc views and their directories stand for, without
 * the safe delete of the platform: nothing refers to a binary cache. The views stay, as they show the server.
 */
class ArcDeleteProvider(private val project: Project, private val files: List<VirtualFile>) : DeleteProvider {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun canDeleteElement(dataContext: DataContext) = files.isNotEmpty()

    override fun deleteElement(dataContext: DataContext) {
        // A file in a selected directory goes with it
        val roots = files.filter { file -> file.isValid && files.none { it != file && VfsUtilCore.isAncestor(it, file, true) } }
        if (roots.isEmpty()) return
        val single = roots.singleOrNull()
        val message = when {
            single == null -> ArendBundle.message("arend.arc.delete.several", roots.size)
            single.isDirectory -> ArendBundle.message("arend.arc.delete.directory", FileUtil.getLocationRelativeToUserHome(single.presentableUrl))
            else -> ArendBundle.message("arend.arc.delete.file", FileUtil.getLocationRelativeToUserHome(single.presentableUrl))
        }
        val title = ArendBundle.message("arend.arc.delete.title")
        if (Messages.showOkCancelDialog(project, message, title, ArendBundle.message("arend.arc.delete.button"),
                CommonBundle.getCancelButtonText(), Messages.getQuestionIcon()) != Messages.OK) {
            return
        }
        try {
            WriteCommandAction.writeCommandAction(project).withName(title).withGlobalUndo().run<IOException> {
                for (file in roots) {
                    if (file.isValid) file.delete(this)
                }
            }
        } catch (e: IOException) {
            Messages.showErrorDialog(project, e.message, title)
        }
    }
}
