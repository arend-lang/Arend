package org.arend.module

import com.intellij.openapi.vfs.VirtualFile
import org.arend.library.classLoader.ClassLoaderDelegate
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

class IntellijClassLoaderDelegate(private val root: VirtualFile) : ClassLoaderDelegate {
    override fun findClass(longName: String): ByteArray? {
        val relativePath = longName.replace('.', '/') + ".class"
        try {
            // Classes are loaded lazily, that is, in the middle of resolving, which happens under a read
            // lock. VFS operations there are either forbidden (a synchronous refresh) or needlessly slow,
            // so the bytecode of a class located in the local file system is read directly from the disk.
            if (root.isInLocalFileSystem) {
                val file = Paths.get(root.path).resolve(relativePath)
                return if (Files.isRegularFile(file)) Files.readAllBytes(file) else null
            }
            var file: VirtualFile? = root
            for (name in relativePath.split('/')) {
                file = file?.findChild(name) ?: return null
            }
            return if (file != null && !file.isDirectory) file.contentsToByteArray() else null
        } catch (e: IOException) {
            throw ClassNotFoundException("An exception happened during loading of class $longName", e)
        }
    }

    override fun toString() = root.path
}
