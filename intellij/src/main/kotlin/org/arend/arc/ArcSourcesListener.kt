package org.arend.arc

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.FileUtils.SERIALIZED_EXTENSION

/**
 * The .arc views in the project view mirror the sources and are faded when they have no .arc on disk, so it is updated
 * when .ard or .arc files or directories appear or disappear; so are the icons of the views in the editor tabs.
 */
class ArcSourcesListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any(::changesViews)) {
            ProjectView.getInstance(project).refresh()
            val editorManager = FileEditorManager.getInstance(project)
            for (file in editorManager.openFiles) {
                if (file is ArcVirtualFile) editorManager.updateFilePresentation(file)
            }
        }
    }

    private fun changesViews(event: VFileEvent): Boolean {
        val structural = event is VFileCreateEvent || event is VFileDeleteEvent || event is VFileMoveEvent || event is VFileCopyEvent ||
            event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME
        return structural && (event.path.endsWith(EXTENSION) || event.path.endsWith(SERIALIZED_EXTENSION) ||
            event.file?.isDirectory == true || (event as? VFileCreateEvent)?.isDirectory == true)
    }
}
