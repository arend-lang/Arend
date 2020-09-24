package org.arend.module.config

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleServiceManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.ext.module.ModulePath
import org.arend.library.LibraryDependency
import org.arend.module.*
import org.arend.settings.ArendProjectSettings
import org.arend.typechecking.ArendTypechecking
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.util.*
import org.arend.yaml.*
import org.jetbrains.yaml.psi.YAMLFile


class ArendModuleConfigService(val module: Module) : LibraryConfig(module.project), ArendModuleConfiguration {
    private var synchronized = false

    fun synchronize() = synchronized(this) {
        val result = !synchronized
        synchronized = true
        result
    }

    private val libraryManager = project.service<TypeCheckingService>().libraryManager

    override var librariesRoot = project.service<ArendProjectSettings>().librariesRoot
        set(value) {
            field = value
            project.service<ArendProjectSettings>().librariesRoot = value
        }

    override var sourcesDir = ""
    override var withBinaries = false
    override var binariesDirectory = ""
    override var testsDir = ""
    override var withExtensions = false
    override var extensionsDirectory = ""
    override var extensionMainClassData = ""
    override var modules: List<ModulePath>? = null
    override var dependencies: List<LibraryDependency> = emptyList()
    override var langVersionString: String = ""

    override val binariesDir: String?
        get() = flaggedBinariesDir

    override val extensionsDir: String?
        get() = flaggedExtensionsDir

    override val extensionMainClass: String?
        get() = flaggedExtensionMainClass

    override val langVersion: Range<Version>
        get() = Range.parseVersionRange(langVersionString) ?: Range.unbound()

    override val root: VirtualFile?
        get() = ModuleRootManager.getInstance(module).contentEntries.firstOrNull()?.file

    override val name
        get() = module.name

    private val yamlFile
        get() = root?.findChild(FileUtils.LIBRARY_CONFIG_FILE)?.let { PsiManager.getInstance(project).findFile(it) as? YAMLFile }

    val librariesRootDef: String?
        get() {
            val librariesRoot = librariesRoot
            return if (librariesRoot.isEmpty()) {
                root?.parent?.path?.let { FileUtil.toSystemDependentName(it) }
            } else librariesRoot
        }

    val library = ArendRawLibrary(this)

    private fun updateDependencies(newDependencies: List<LibraryDependency>, reload: Boolean, callback: () -> Unit) {
        val oldDependencies = dependencies
        dependencies = ArrayList(newDependencies)
        synchronized = false

        if (!reload) {
            return
        }

        var reloadLib = false
        for (dependency in oldDependencies) {
            if (!newDependencies.contains(dependency) && libraryManager.getRegisteredLibrary(dependency.name) != null) {
                reloadLib = true
                break
            }
        }

        if (reloadLib) {
            refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
            project.service<TypeCheckingService>().reload(true)
            callback()
        } else ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Loading Arend Libraries", false) {
            override fun run(indicator: ProgressIndicator) {
                refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
                runReadAction {
                    val typechecking = ArendTypechecking.create(project)
                    for (dependency in newDependencies) {
                        if (!oldDependencies.contains(dependency)) {
                            var depLibrary = libraryManager.getRegisteredLibrary(dependency.name)
                            if (depLibrary == null) {
                                depLibrary = libraryManager.loadDependency(library, dependency.name, typechecking)
                            }
                            if (depLibrary != null) {
                                libraryManager.registerDependency(library, depLibrary)
                            }
                        }
                    }
                }
                callback()
            }
        })
    }

    fun copyFromYAML(yaml: YAMLFile, update: Boolean) {
        val newDependencies = yaml.dependencies
        if (dependencies != newDependencies) {
            updateDependencies(newDependencies, update) {
                ModuleSynchronizer.synchronizeModule(this, false)
            }
        }

        modules = yaml.modules
        flaggedBinariesDir = yaml.binariesDir
        testsDir = yaml.testsDir
        val extDir = yaml.extensionsDir
        val extMain = yaml.extensionMainClass
        withExtensions = extDir != null && extMain != null
        if (extDir != null) {
            extensionsDirectory = extDir
        }
        if (extMain != null) {
            extensionMainClassData = extMain
        }
        sourcesDir = yaml.sourcesDir ?: ""
        langVersionString = yaml.langVersion ?: ""
    }

    fun copyFromYAML(update: Boolean) {
        copyFromYAML(yamlFile ?: return, update)
    }

    fun updateFromIDEA(config: ArendModuleConfiguration) {
        val newLibrariesRoot = config.librariesRoot
        val reload = librariesRoot != newLibrariesRoot
        var updateYAML = false

        val newDependencies = config.dependencies
        if (dependencies != newDependencies) {
            updateDependencies(newDependencies, !reload) {}
            updateYAML = true
        }

        val newSourcesDir = config.sourcesDir
        if (sourcesDir != newSourcesDir) {
            sourcesDir = newSourcesDir
            updateYAML = true
        }

        val newBinariesDir = config.flaggedBinariesDir
        if (flaggedBinariesDir != newBinariesDir) {
            updateYAML = true
        }
        withBinaries = config.withBinaries
        binariesDirectory = config.binariesDirectory

        val newTestsDir = config.testsDir
        if (testsDir != newTestsDir) {
            testsDir = newTestsDir
            updateYAML = true
        }

        val newExtensionsDir = config.flaggedExtensionsDir
        if (flaggedExtensionsDir != newExtensionsDir) {
            updateYAML = true
        }

        val newExtensionMainClass = config.flaggedExtensionMainClass
        if (flaggedExtensionMainClass != newExtensionMainClass) {
            updateYAML = true
        }
        withExtensions = config.withExtensions
        extensionsDirectory = config.extensionsDirectory
        extensionMainClassData = config.extensionMainClassData

        val newLangVersion = config.langVersionString
        if (langVersionString != newLangVersion) {
            langVersionString = newLangVersion
            updateYAML = true
        }

        if (updateYAML) yamlFile?.write {
            langVersion = newLangVersion
            sourcesDir = newSourcesDir
            binariesDir = newBinariesDir
            testsDir = newTestsDir
            extensionsDir = newExtensionsDir
            extensionMainClass = newExtensionMainClass
            dependencies = newDependencies
        }

        if (reload) {
            synchronized = false
            librariesRoot = newLibrariesRoot
            ModuleSynchronizer.synchronizeModule(this, true)
        }
    }

    companion object {
        fun getConfig(module: Module): LibraryConfig {
            if (ArendModuleType.has(module)) {
                val service = ModuleServiceManager.getService(module, ArendModuleConfigService::class.java)
                if (service != null) {
                    return service
                }
                NotificationErrorReporter.ERROR_NOTIFICATIONS.createNotification("Failed to get ArendModuleConfigService for $module", NotificationType.ERROR).notify(module.project)
            }
            return EmptyLibraryConfig(module.name, module.project)
        }

        fun getInstance(module: Module?) =
            if (module != null && ArendModuleType.has(module)) ModuleServiceManager.getService(module, ArendModuleConfigService::class.java) else null
    }
}