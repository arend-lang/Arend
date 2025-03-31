package org.arend.psi.arc

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.util.*
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import java.util.function.Function
import javax.swing.JComponent

class ArcFileNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(project: Project, virtualFile: VirtualFile): Function<in FileEditor, out JComponent?>? {
        if (!project.service<ArcUnloadedModuleService>().containsUnloadedModule(virtualFile)) {
            return null
        }
        return Function { createPanel(project, virtualFile, it) }
    }

    private fun createPanel(project: Project, virtualFile: VirtualFile, editor: FileEditor): EditorNotificationPanel? {
        val panel = EditorNotificationPanel(editor, EditorNotificationPanel.Status.Info)

        val config = project.arendModules.map { ArendModuleConfigService.getInstance(it) }.find {
            it?.root?.let { root -> VfsUtilCore.isAncestor(root, virtualFile, true) } ?: false
        } ?: ArendModuleConfigService.getInstance(project.arendModules.getOrNull(0))
        val relativePath = config?.binariesDirFile?.getRelativePath(virtualFile) ?: mutableListOf(virtualFile.name)
        relativePath[relativePath.lastIndex] = relativePath[relativePath.lastIndex].removeSuffix(SERIALIZED_EXTENSION)

        val psiManager = PsiManager.getInstance(project)
        val arendFile = config?.sourcesDirFile?.getRelativeFile(relativePath, EXTENSION)
            ?.let { psiManager.findFile(it) } as? ArendFile? ?: return null
        panel.text = ArendBundle.message("arend.arc.retypecheck", ModulePath(relativePath))

        panel.createActionLabel(ArendBundle.message("arend.arc.typecheck")) {
            object : Task.Backgroundable(project, "Typechecking", false) {
                override fun run(indicator: ProgressIndicator) {
                    project.service<ArcUnloadedModuleService>().removeLoadedModule(virtualFile)
                    runReadAction {
                        val server = project.service<ArendServerService>().server
                        arendFile.moduleLocation?.let { server.getCheckerFor(listOf(it)).typecheck(null, DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty()) }
                    }

                    EditorNotifications.getInstance(project).updateNotifications(virtualFile)
                    invokeLater {
                      FileDocumentManager.getInstance().reloadBinaryFiles()
                    }
                }
            }.queue()
        }
        return panel
    }
}
