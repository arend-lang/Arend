package org.arend.arc

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.LocalTimeCounter
import org.arend.ext.module.ModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.ArendBundle
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import org.arend.util.findInternalLibrary
import org.arend.util.getRelativeFile
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.Charset

/**
 * The .arc view of the module [modulePath] of the library [libraryName], see [ArcFileSystem]. Its text is set by
 * [ArcViewService] when the view is shown in an editor; until then, it is a placeholder. Reading the text prepares
 * nothing: the platform reads files it does not show, such as editors that are restored but not selected.
 */
class ArcVirtualFile(private val fileSystem: ArcFileSystem, val project: Project, val libraryName: String, val modulePath: ModulePath) : VirtualFile() {
    @Volatile
    private var text: String? = null

    // A new text comes with a new stamp, so that the document is reloaded, and nothing computed for the old text applies to it
    @Volatile
    private var stamp = LocalTimeCounter.currentTime()

    val config: ArendModuleConfigService?
        get() = project.findInternalLibrary(libraryName) as? ArendModuleConfigService

    val sourceFile: VirtualFile?
        get() = config?.sourcesDirFile?.getRelativeFile(modulePath.toList(), EXTENSION)

    val hasText: Boolean
        get() = text != null

    internal fun setText(newText: String) {
        text = newText
        stamp = LocalTimeCounter.currentTime()
    }

    private fun currentText() = text ?: ArendBundle.message("arend.arc.preparing.placeholder", name)

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
        return !config.isInitialized || sourceFile != null
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
