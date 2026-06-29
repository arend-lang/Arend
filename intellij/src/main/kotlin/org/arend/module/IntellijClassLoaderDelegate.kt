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
            val nio = Paths.get(root.path)
            val real = nio.toRealPath()
            var file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(real)
            for (name in (longName.replace('.', '/') + ".class").split('/')) {
                file = file?.findChild(name) ?: file?.refreshed?.findChild(name) ?: break
            }
            return file?.contentsToByteArray()
        } catch (e: IOException) {
            throw ClassNotFoundException("An exception happened during loading of class $longName", e)
        }
    }

    override fun toString() = root.path
}