package org.arend.module.config

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.DataContainer
import org.arend.library.LibraryDependency
import org.arend.module.IntellijClassLoaderDelegate
import org.arend.module.ModuleLocation
import org.arend.module.ModuleLocation.LocationKind
import org.arend.module.ModuleLocation.LocationKind.GENERATED
import org.arend.module.ModuleLocation.LocationKind.SOURCE
import org.arend.module.ModuleLocation.LocationKind.TEST
import org.arend.naming.reference.DataModuleReferable
import org.arend.psi.ArendFile
import org.arend.server.ArendLibrary
import org.arend.server.ArendServerService
import org.arend.ui.impl.ArendGeneralUI
import org.arend.util.*
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import org.jetbrains.yaml.psi.YAMLFile


abstract class LibraryConfig(val project: Project) : ArendLibrary {
    open val sourcesDir: String
        get() = ""
    open val binariesDir: String?
        get() = null
    open val testsDir: String
        get() = ""
    open val extensionsDir: String?
        get() = null
    open val modules: List<ModulePath>?
        get() = null
    open val dependencies: List<LibraryDependency>
        get() = emptyList()
    open val version: Version?
        get() = null
    open val langVersion: Range<Version>
        get() = Range.unbound()

    abstract val name: String

    abstract val root: VirtualFile?

    private val yamlVirtualFile
        get() = root?.findChild(FileUtils.LIBRARY_CONFIG_FILE)

    val yamlFile
        get() = yamlVirtualFile?.let { PsiManager.getInstance(project).findFile(it) as? YAMLFile }

    open val localFSRoot: VirtualFile?
        get() = root?.let { if (it.isInLocalFileSystem) it else JarFileSystem.getInstance().getVirtualFileForJar(it) }

    private fun findDir(dir: String) = root?.findFileByRelativePath(FileUtil.toSystemIndependentName(dir).removeSuffix("/"))

    open val sourcesDirFile: VirtualFile?
        get() = sourcesDir.let { if (it.isEmpty()) root else findDir(it) }

    open val testsDirFile: VirtualFile?
        get() = testsDir.let { if (it.isEmpty()) null else findDir(it) }

    val binariesDirFile: VirtualFile?
        get() = binariesDir?.let { findDir(it) }

    open val isExternal: Boolean
        get() = false

    override fun getLibraryName() = name

    override fun isExternalLibrary() = false

    override fun getModificationStamp(): Long = -1

    override fun getLibraryDependencies() = dependencies.map { it.name }

    override fun getClassLoaderDelegate() = extensionDirFile?.let { IntellijClassLoaderDelegate(it) }

    override fun getExtensionMainClass(): String? = null

    override fun getArendUI() = ArendGeneralUI(project)

    // Extensions

    val extensionDirFile: VirtualFile?
        get() = extensionsDir?.let(::findDir)

    val extensionMainClassFile: VirtualFile?
        get() {
            val className = extensionMainClass ?: return null
            return extensionDirFile?.getRelativeFile(className.split('.'), ".class")
        }

    private fun getBaseDir(kind: LocationKind) = when (kind) {
        SOURCE -> sourcesDirFile
        TEST -> testsDirFile
        GENERATED -> binariesDirFile
    }

    // Modules

    fun findModules(locationKind: LocationKind): List<ModulePath> {
        val modules = modules
        if (modules != null) {
            return modules
        }

        val dir = getBaseDir(locationKind) ?: return emptyList()
        val result = ArrayList<ModulePath>()
        VfsUtil.iterateChildrenRecursively(dir, null) { file ->
            if (file.name.endsWith(EXTENSION)) {
                dir.getRelativePath(file, EXTENSION)?.let { result.add(ModulePath(it)) }
            }
            if (file.name.endsWith(SERIALIZED_EXTENSION)) {
                dir.getRelativePath(file, SERIALIZED_EXTENSION)?.let { result.add(ModulePath(it)) }
            }
            return@iterateChildrenRecursively true
        }
        return result
    }

  // TODO
//    private fun findParentDirectory(modulePath: ModulePath, locationKind: LocationKind): VirtualFile? {
//        var dir = getBaseDir(locationKind) ?: return null
    private fun findParentDirectory(modulePath: ModulePath, inTests: Boolean): VirtualFile? {
        var dir = (if (inTests) testsDirFile else sourcesDirFile) ?: return null
        val list = modulePath.toList()
        var i = 0
        while (i < list.size - 1) {
            dir = dir.findChild(list[i++]) ?: return null
        }
        return dir
    }

    fun findArendDirectory(modulePath: ModulePath): PsiDirectory? {
        var dir = sourcesDirFile ?: return null
        for (name in modulePath.toList()) {
            dir = dir.findChild(name) ?: return null
        }
        return PsiManager.getInstance(project).findDirectory(dir)
    }

    fun findArendFile(modulePath: ModulePath, locationKind: LocationKind): ArendFile? =
        findParentDirectory(modulePath, locationKind)?.findChild(modulePath.lastName + if (locationKind == GENERATED) SERIALIZED_EXTENSION else EXTENSION)?.let {
            PsiManager.getInstance(project).findFile(it) as? ArendFile
        }

    fun findGeneratedArendFile(modulePath: ModulePath): ArendFile? =
        (project.service<ArendServerService>().server.getRawGroup(ModuleLocation(libraryName, ModuleLocation.LocationKind.GENERATED, modulePath))?.referable as? DataContainer)?.data as? ArendFile

    fun findArendFile(modulePath: ModulePath, withAdditional: Boolean, withTests: Boolean): ArendFile? =
        if (modulePath.size() == 0) {
            null
        } else {
            (if (withAdditional) additionalModules[modulePath] else null) ?:
                findArendFile(modulePath, SOURCE) ?: if (withTests) findArendFile(modulePath, TEST) else null
          // TODO
//            (if (withAdditional) findGeneratedArendFile(modulePath) else null) ?:
//                findArendFile(modulePath, false) ?: if (withTests) findArendFile(modulePath, true) else null
        }

    fun findArendFile(moduleLocation: ModuleLocation): ArendFile? {
            val server = project.service<ArendServerService>().server
        return ((server.getRawGroup(moduleLocation)?.referable as? DataModuleReferable)?.data as? ArendFile) ?:
            findArendFile(moduleLocation.modulePath, moduleLocation.locationKind == ModuleLocation.LocationKind.GENERATED, moduleLocation.locationKind == ModuleLocation.LocationKind.TEST)
    }

    fun findArendFileOrDirectory(modulePath: ModulePath, withAdditional: Boolean, withTests: Boolean): PsiFileSystemItem? {
        if (modulePath.size() == 0) {
            return findArendDirectory(modulePath)
        }
        if (withAdditional) {
            findGeneratedArendFile(modulePath)?.let {
                return it
            }
        }

        val psiManager = PsiManager.getInstance(project)

        val srcDir = findParentDirectory(modulePath, SOURCE)
        srcDir?.findChild(modulePath.lastName + EXTENSION)?.let {
            val file = psiManager.findFile(it)
            if (file is ArendFile) {
                return file
            }
        }

        val testDir = if (withTests) findParentDirectory(modulePath, TEST) else null
        testDir?.findChild(modulePath.lastName + EXTENSION)?.let {
            val file = psiManager.findFile(it)
            if (file is ArendFile) {
                return file
            }
        }

        return (findArendFileOrDirectoryByModulePath(sourcesDirFile, modulePath) ?:
                findArendFileOrDirectoryByModulePath(testsDirFile, modulePath))?.let {
            psiManager.findDirectory(it)
        }
    }

    fun getFileModulePath(file: ArendFile): ModuleLocation? {
        file.generatedModuleLocation?.let {
            return it
        }

        val vFile = file.originalFile.viewProvider.virtualFile
        val sourcesPath = sourcesDirFile?.getRelativePath(vFile, EXTENSION)
        val testPath = testsDirFile?.getRelativePath(vFile, EXTENSION)
        val path: List<String>
        val locationKind = if (sourcesPath != null) {
            path = sourcesPath
            SOURCE
        } else if (testPath != null) {
            path = testPath
            TEST
        } else {
            // TODO FileUtils.SERIALIZED_EXTENSION after creating the icon for the arc files
            path = binariesDirFile?.getRelativePath(vFile, SERIALIZED_EXTENSION) ?: return null
            GENERATED
        }
        return ModuleLocation(name, locationKind, ModulePath(path))
    }

    fun getFileLocationKind(file: ArendFile): LocationKind? = getFileModulePath(file)?.locationKind

    // Dependencies

    val availableConfigs: List<LibraryConfig>
        get() = listOf(this) + dependencies.mapNotNull { project.findLibrary(it.name) }

    inline fun <T> forAvailableConfigs(f: (LibraryConfig) -> T?): T? =
        f(this) ?: dependencies.mapFirstNotNull { dep -> project.findLibrary(dep.name)?.let { f(it) } }

    companion object {
        fun findArendFileOrDirectoryByModulePath(root: VirtualFile?, modulePath: ModulePath): VirtualFile? {
            val path = modulePath.toList()
            var curElement = root
            for (index in path.indices) {
                curElement = if (index == path.indices.last) {
                    curElement?.findChild(path[index]) ?: curElement?.findChild(path[index] + EXTENSION)
                } else {
                    curElement?.findChild(path[index])
                }
            }
            return curElement
        }
    }
}
