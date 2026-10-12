package org.arend.arc

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

// Editor tabs and other places that show the virtual file fade a view without an .arc on disk, as the project view does
class ArcFileIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?) = (file as? ArcVirtualFile)?.icon
}
