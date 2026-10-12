package org.arend.arc

import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ArcUnloadedModuleService {
    // Written by the preparation of views, in the background, and read by the notification provider on other threads
    private val unloadedModules: MutableSet<VirtualFile> = ConcurrentHashMap.newKeySet()

    fun addUnloadedModule(file: VirtualFile) {
        unloadedModules.add(file)
    }

    fun removeLoadedModule(file: VirtualFile) {
        unloadedModules.remove(file)
    }

    fun containsUnloadedModule(file: VirtualFile): Boolean {
        return unloadedModules.contains(file)
    }
}