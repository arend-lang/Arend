package org.arend.module

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import org.arend.library.classLoader.ClassLoaderDelegate
import java.io.IOException

class IntellijClassLoaderDelegate(private val root: VirtualFile) : ClassLoaderDelegate {
    override fun findClass(longName: String): ByteArray? {
        var file: VirtualFile? = root
        ApplicationManager.getApplication().executeOnPooledThread {
            for (name in (longName.replace('.', '/') + ".class").split('/')) {
                file = file?.findChild(name)
                file ?: break
            }
        }.get()

        try {
            return file?.contentsToByteArray()
        } catch (e: IOException) {
            throw ClassNotFoundException("An exception happened during loading of class $longName", e)
        }
    }

    override fun toString() = root.path
}