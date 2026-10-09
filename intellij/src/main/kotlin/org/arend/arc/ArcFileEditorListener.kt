package org.arend.arc

import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

// An .arc view that is activated is brought up to date, and its module is loaded or typechecked if the server has not
class ArcFileEditorListener(private val project: Project) : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile as? ArcVirtualFile ?: return
        project.service<ArcViewService>().refreshIfOutdated(file, true)
    }

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        if (file is ArcVirtualFile) {
            project.serviceIfCreated<ArcViewService>()?.fileClosed(file)
        }
    }
}
