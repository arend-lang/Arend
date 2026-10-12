package org.arend.arc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.LocalTimeCounter
import org.arend.ArendIcons
import org.arend.ext.module.ModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.ArendBundle
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import org.arend.util.findInternalLibrary
import org.arend.util.findLoadedRelativeFile
import org.arend.util.getRelativeFile
import org.arend.util.getRelativePath
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset
import javax.swing.Icon

/**
 * The .arc view of the module [modulePath] of the library [libraryName], see [ArcFileSystem]. Its text, with the
 * links of its names, is set by [ArcViewService] when the view is shown in an editor; until then, it is a placeholder.
 * Reading the text prepares nothing: the platform reads files it does not show, such as editors that are restored but
 * not selected.
 */
class ArcVirtualFile(private val fileSystem: ArcFileSystem, val project: Project, val libraryName: String, val modulePath: ModulePath) : VirtualFile() {
    @Volatile
    private var printed: PrintedView? = null

    // A new text comes with a new stamp, so that the document is reloaded, and nothing computed for the old text applies to it
    @Volatile
    private var stamp = LocalTimeCounter.currentTime()

    val config: ArendModuleConfigService?
        get() = project.findInternalLibrary(libraryName) as? ArendModuleConfigService

    // The source found last, and whether it was looked up at all, which isValid checks on the EDT
    @Volatile
    private var lastSource: VirtualFile? = null
    @Volatile
    private var sourceLookedUp = false

    val sourceFile: VirtualFile?
        get() = config?.sourcesDirFile?.getRelativeFile(modulePath.toList(), EXTENSION).also {
            lastSource = it
            sourceLookedUp = true
        }

    /**
     * The .arc file of the module on disk, if there is one. The view does not show it, but stands for it in the project
     * view. On the EDT, only among the files the VFS has loaded: the binaries directory is excluded, so its files are
     * seldom loaded, and looking them up there is a slow operation.
     */
    val fileOnDisk: VirtualFile?
        get() {
            val binaries = config?.binariesDirFile ?: return null
            val path = modulePath.toList()
            val file = if (ApplicationManager.getApplication().isDispatchThread) binaries.findLoadedRelativeFile(path, SERIALIZED_EXTENSION)
                else binaries.getRelativeFile(path, SERIALIZED_EXTENSION)
            return file?.takeIf { !it.isDirectory }
        }

    val icon: Icon
        get() = if (fileOnDisk != null) ArendIcons.ARC_FILE else ArendIcons.ARC_FILE_NOT_ON_DISK

    val hasText: Boolean
        get() = printed != null

    // What the names of the text stand for
    val links: ArcLinks
        get() = printed?.links ?: ArcLinks.EMPTY

    internal fun setText(view: PrintedView) {
        printed = view
        stamp = LocalTimeCounter.currentTime()
    }

    private fun currentText() = printed?.text ?: ArendBundle.message("arend.arc.preparing.placeholder", name)

    override fun getName() = modulePath.lastName + SERIALIZED_EXTENSION

    override fun getFileSystem() = fileSystem

    override fun getPath() = ArcFileSystem.path(project, libraryName, modulePath)

    override fun getPresentableUrl() = "$libraryName: $modulePath$SERIALIZED_EXTENSION"

    override fun isWritable() = false

    override fun isDirectory() = false

    // A library reads its sources directory from arend.yaml when it is registered; until then, the view is kept
    override fun isValid(): Boolean {
        if (project.isDisposed) return false
        val config = config ?: return false
        if (!config.isInitialized) return true
        return if (ApplicationManager.getApplication().isDispatchThread) hasLoadedSource(config) else sourceFile != null
    }

    /**
     * Whether the view has a source, as far as can be told without looking files up in the VFS, which the platform asks
     * on the EDT, where that is a slow operation: the source found last is still there, or the VFS has loaded one. A view
     * whose source was never looked up, and which the VFS has not loaded, is taken to have one, as views are made for
     * sources; looking it up anywhere else settles it.
     */
    private fun hasLoadedSource(config: ArendModuleConfigService): Boolean {
        val sources = config.sourcesDirFile ?: return false
        val path = modulePath.toList()
        lastSource?.let { if (it.isValid && it.name.endsWith(EXTENSION) && sources.getRelativePath(it, EXTENSION) == path) return true }
        sources.findLoadedRelativeFile(path, EXTENSION)?.takeIf { !it.isDirectory }?.let {
            lastSource = it
            sourceLookedUp = true
            return true
        }
        return !sourceLookedUp
    }

    override fun getParent(): VirtualFile? = null

    override fun getChildren(): Array<VirtualFile> = EMPTY_ARRAY

    override fun getFileType() = ArcViewFileType.INSTANCE

    override fun getCharset(): Charset = Charsets.UTF_8

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream =
        throw IOException("$presentableUrl is read-only")

    override fun contentsToByteArray() = currentText().toByteArray(Charsets.UTF_8)

    override fun getTimeStamp() = stamp

    override fun getModificationStamp() = stamp

    override fun getLength() = contentsToByteArray().size.toLong()

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        postRunnable?.run()
    }

    override fun getInputStream() = ByteArrayInputStream(contentsToByteArray())
}
