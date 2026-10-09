package org.arend.arc

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import org.arend.util.FileUtils.EXTENSION

// The .arc views in the project view mirror the sources, so it is updated when .ard files or directories appear or disappear
class ArcSourcesListener(private val project: Project) : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any(::changesSources)) {
            ProjectView.getInstance(project).refresh()
        }
    }

    private fun changesSources(event: VFileEvent): Boolean {
        val structural = event is VFileCreateEvent || event is VFileDeleteEvent || event is VFileMoveEvent || event is VFileCopyEvent ||
            event is VFilePropertyChangeEvent && event.propertyName == VirtualFile.PROP_NAME
        return structural && (event.path.endsWith(EXTENSION) || event.file?.isDirectory == true || (event as? VFileCreateEvent)?.isDirectory == true)
    }
}
