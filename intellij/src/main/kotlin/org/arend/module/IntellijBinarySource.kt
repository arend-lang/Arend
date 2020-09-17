package org.arend.module

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ext.module.ModulePath
import org.arend.library.SourceLibrary
import org.arend.util.getRelativeFile
import org.arend.source.StreamBinarySource
import org.arend.util.FileUtils
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

class IntellijBinarySource(root: VirtualFile, private val modulePath: ModulePath) : StreamBinarySource() {
    private val file = root.getRelativeFile(modulePath.toList(), FileUtils.SERIALIZED_EXTENSION)

    override fun getModulePath() = modulePath

    override fun getTimeStamp() = file?.timeStamp ?: 0

    override fun isAvailable() = file?.isValid == true

    override fun delete(library: SourceLibrary?) = if (file != null) runWriteAction {
        try {
            file.delete(library)
        } catch (e: IOException) {
            return@runWriteAction false
        }
        true
    } else false

    override fun getInputStream(): InputStream? = file?.inputStream

    override fun getOutputStream(): OutputStream? = file?.getOutputStream(this)
}