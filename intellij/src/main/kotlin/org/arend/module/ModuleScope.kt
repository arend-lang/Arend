package org.arend.module

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModulePath
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiModuleReferable
import org.arend.util.FileUtils


class ModuleScope private constructor(private val libraryConfig: LibraryConfig?, private val rootDirs: List<VirtualFile>?, private val prefix: List<String>, private val additionalPaths: List<List<String>>) : Scope {
    constructor(libraryConfig: LibraryConfig) : this(libraryConfig, null, emptyList(), libraryConfig.additionalModulesSet.map { it.toList() })

    private fun calculateRootDirs() = rootDirs ?: libraryConfig!!.availableConfigs.mapNotNull { it.sourcesDirFile }

    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()
        if (libraryConfig != null) {
            val psiManager = PsiManager.getInstance(libraryConfig.project)
            for (root in calculateRootDirs()) {
                for (file in root.children) {
                    val name = file.name
                    if (file.isDirectory) {
                        if (FileUtils.isModuleName(name)) {
                            val dir = psiManager.findDirectory(file)
                            if (dir != null) {
                                result.add(PsiModuleReferable(listOf(dir), ModulePath(prefix + name)))
                            }
                        }
                    } else if (name.endsWith(FileUtils.EXTENSION)) {
                        (psiManager.findFile(file) as? ArendFile)?.let {
                            result.add(PsiModuleReferable(listOf(it), ModulePath(prefix + name.substring(0, name.length - FileUtils.EXTENSION.length))))
                        }
                    }
                }
            }
        }
        for (path in additionalPaths) {
            result.add(ModuleReferable(ModulePath(prefix + path[0])))
        }
        if (rootDirs == null) {
            result.add(ModuleReferable(Prelude.MODULE_PATH))
        }
        return result
    }

    override fun resolveNamespace(name: String, onlyInternal: Boolean): Scope {
        val newRootDirs = if (libraryConfig == null) emptyList() else (calculateRootDirs()).mapNotNull { root ->
            for (file in root.children) {
                if (file.name == name) {
                    return@mapNotNull if (file.isDirectory) file else null
                }
            }
            return@mapNotNull null
        }
        val newPaths = additionalPaths.mapNotNull { if (it.size > 1 && it[0] == name) it.drop(1) else null }
        return if (newRootDirs.isEmpty() && newPaths.isEmpty()) EmptyScope.INSTANCE else ModuleScope(if (newRootDirs.isEmpty()) null else libraryConfig, newRootDirs, prefix + name, newPaths)
    }
}