package org.arend.arc

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem
import com.intellij.openapi.vfs.NonPhysicalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.arend.ext.module.ModulePath
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import java.util.concurrent.ConcurrentHashMap

/**
 * The .arc views: a read-only file `arend-arc://<project>/<library>/<module path>.arc` for each module of a library
 * of the project, which shows the module as it is in the server (see [ArcViewService]). The .arc files on disk are a
 * cache of the server that is written when the project is closed, and are not shown. A view of the same module is
 * always the same file, so that its document and editors are shared, and an editor of it is restored with the project.
 */
class ArcFileSystem : DeprecatedVirtualFileSystem(), NonPhysicalFileSystem {
    private val files = ConcurrentHashMap<String, ArcVirtualFile>()

    override fun getProtocol() = PROTOCOL

    override fun findFileByPath(path: String): VirtualFile? {
        files[path]?.let { return it }
        val segments = path.split('/')
        if (segments.size < 3 || !segments.last().endsWith(SERIALIZED_EXTENSION)) return null
        val project = ProjectManager.getInstance().openProjects.find { it.locationHash == segments[0] } ?: return null
        val modulePath = ModulePath(segments.subList(2, segments.size - 1) + segments.last().removeSuffix(SERIALIZED_EXTENSION))
        return findFile(project, segments[1], modulePath)
    }

    fun findFile(project: Project, libraryName: String, modulePath: ModulePath): ArcVirtualFile =
        files.computeIfAbsent(path(project, libraryName, modulePath)) { ArcVirtualFile(this, project, libraryName, modulePath) }

    // The views of the project that have been asked for
    fun files(project: Project): List<ArcVirtualFile> = files.values.filter { it.project == project }

    fun forget(project: Project) {
        files.values.removeIf { it.project == project }
    }

    override fun refresh(asynchronous: Boolean) {}

    override fun refreshAndFindFileByPath(path: String) = findFileByPath(path)

    companion object {
        const val PROTOCOL = "arend-arc"

        fun getInstance() = VirtualFileManager.getInstance().getFileSystem(PROTOCOL) as ArcFileSystem

        fun path(project: Project, libraryName: String, modulePath: ModulePath) =
            project.locationHash + "/" + libraryName + "/" + modulePath.toList().joinToString("/") + SERIALIZED_EXTENSION
    }
}
