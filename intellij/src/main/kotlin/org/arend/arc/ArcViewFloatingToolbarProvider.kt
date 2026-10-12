package org.arend.arc

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NotNullLazyKey
import org.arend.toolWindow.errors.ArendPrintOptionsActionGroup
import org.arend.toolWindow.errors.PrintOptionKind

// The toolbar in the upper right corner of the editors of .arc views; like the one that reloads Gradle projects, it does not hide
class ArcViewFloatingToolbarProvider : AbstractFloatingToolbarProvider(ACTION_GROUP) {
    override val autoHideable = false

    override suspend fun isApplicableAsync(dataContext: DataContext): Boolean {
        val editor = dataContext.getData(CommonDataKeys.EDITOR) ?: return false
        return editor.editorKind == EditorKind.MAIN_EDITOR && FileDocumentManager.getInstance().getFile(editor.document) is ArcVirtualFile
    }

    override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
        component.scheduleShow()
    }

    companion object {
        const val ACTION_GROUP = "Arend.ArcView.FloatingToolbar"
    }
}

/**
 * The actions of the floating toolbar of .arc views: the print options of the views, as the Arend Messages tool window
 * has them for goals and errors. Changing them prints the shown views again.
 */
class ArcViewToolbarActionGroup : ActionGroup(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        val project = e?.project?.takeIf { !it.isDisposed } ?: return EMPTY_ARRAY
        return arrayOf(PRINT_OPTIONS.getValue(project))
    }

    companion object {
        // The options toggle the settings of the project, so each project has its own
        private val PRINT_OPTIONS = NotNullLazyKey.createLazyKey<AnAction, Project>("arend.arc.printOptions") { project ->
            ArendPrintOptionsActionGroup(project, PrintOptionKind.ARC_VIEW_PRINT_OPTIONS) {
                project.service<ArcViewService>().printOptionsChanged()
            }
        }
    }
}
