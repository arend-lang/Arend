package org.arend.arc

import com.intellij.openapi.components.Service
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ArcUnloadedModuleService {
    private val unloadedModules = HashSet<VirtualFile>()

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