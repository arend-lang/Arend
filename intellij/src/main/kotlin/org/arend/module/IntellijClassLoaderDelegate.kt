package org.arend.module

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.arend.library.classLoader.ClassLoaderDelegate
import org.arend.util.refreshed
import java.io.IOException
import java.nio.file.Paths

class IntellijClassLoaderDelegate(private val root: VirtualFile) : ClassLoaderDelegate {
    override fun findClass(longName: String): ByteArray? {
        try {
            // root.path of a library packed into a zip is of the form '/path/to/library.zip!/ext',
            // which is not a path in the local file system, so it cannot be resolved through nio
            var file: VirtualFile? = if (root.isInLocalFileSystem) {
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Paths.get(root.path).toRealPath())
            } else {
                root
            }
            for (name in (longName.replace('.', '/') + ".class").split('/')) {
                file = file?.findChild(name) ?: file?.refreshed?.findChild(name) ?: return null
            }
            return file?.contentsToByteArray()
        } catch (e: IOException) {
            throw ClassNotFoundException("An exception happened during loading of class $longName", e)
        }
    }

    override fun toString() = root.path
}