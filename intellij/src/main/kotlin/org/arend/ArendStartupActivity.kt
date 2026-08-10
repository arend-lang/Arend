package org.arend

import com.intellij.codeInsight.editorActions.SelectionQuotingTypedHandler
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.*
import com.intellij.openapi.startup.ProjectActivity
import org.arend.educational.ArendConfigurator
import org.arend.module.AREND_LIB
import org.arend.module.ModuleSynchronizer
import org.arend.module.checkForUpdates
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.server.ArendServerListener
import org.arend.server.ArendServerService
import org.arend.util.ArendBundle
import org.arend.util.arendModules
import org.arend.util.orderModules
import org.arend.util.findExternalLibrary
import org.arend.util.register
import org.arend.util.registerStudyLibrary
import org.arend.util.unregister
import org.arend.yaml.YAMLFileListener
import com.jetbrains.edu.learning.StudyTaskManager


class ArendStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val service = project.service<ArendServerService>()

        project.messageBus.connect(service).subscribe(ModuleListener.TOPIC, object : ModuleListener {
            override fun modulesAdded(project: Project, modules: List<Module>) {
                for (module in orderModules(modules)) {
                    module.register(modules)
                }
            }

            override fun beforeModuleRemoved(project: Project, module: Module) {
                val config = ArendModuleConfigService.getInstance(module) ?: return
                config.isInitialized = ApplicationManager.getApplication().isUnitTestMode
            }

            override fun moduleRemoved(project: Project, module: Module) {
                service.server.removeLibrary(module.name)
                module.unregister()
            }
        })

        /* All resources are stored in TypeCheckingService, so they should be disposed anyway
        ProjectManager.getInstance().addProjectManagerListener(project, object : ProjectManagerListener {
            override fun projectClosing(project: Project) {
                libraryManager.unload()
            }
        })
        */

        if (!ApplicationManager.getApplication().isUnitTestMode) DumbService.getInstance(project)
            .queueTask(object : DumbModeTask() {
                override fun performInDumbMode(indicator: ProgressIndicator) {
                    ApplicationManager.getApplication().executeOnPooledThread {
                        val modules = orderModules(project.arendModules)
                        indicator.text = ArendBundle.message("arend.startup.loading.arend.modules")
                        indicator.isIndeterminate = false
                        indicator.fraction = 0.0
                        var frac = 0.0
                        val progressFraction = 1.0 / modules.size.toDouble()
                        for (module in modules) {
                            module.register()
                            frac += progressFraction
                            indicator.fraction = frac
                        }
                        if (runReadAction { StudyTaskManager.getInstance(project).course } != null) {
                            project.registerStudyLibrary()
                        }
                    }
                }
            })

        ApplicationManager.getApplication().messageBus.connect(service)
            .subscribe<AppLifecycleListener>(AppLifecycleListener.TOPIC, object : AppLifecycleListener {
                override fun appWillBeClosed(isRestart: Boolean) {
                    if (project.isDisposed) return
                    for (module in project.arendModules) {
                        ArendModuleConfigService.getInstance(module)?.saveSettings()
                    }
                }
            })

        val yamlFileListener = YAMLFileListener(project)
        yamlFileListener.register()
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(yamlFileListener, project)

        ModuleSynchronizer(project).install()
        ArendConfigurator.registerArendLanguage()

        if (runReadAction { StudyTaskManager.getInstance(project).course } != null) {
            project.registerStudyLibrary()
        }

        service.server.addListener(object : ArendServerListener {
            override fun onLibraryUpdated(libraryName: String) {
                val library = service.server.getLibrary(libraryName)
                library?.libraryDependencies?.forEach {
                    if (it == AREND_LIB) {
                        val version = (project.findExternalLibrary(AREND_LIB) as? ExternalLibraryConfig)?.version
                        checkForUpdates(project, version)
                    }
                }

                super.onLibraryUpdated(libraryName)
            }
        })

        disableActions()
    }

    private fun disableActions() {
        val actionManager = ApplicationManager.getApplication().getServiceIfCreated(ActionManager::class.java)
        listOf(
            ArendBundle.message("arend.disableGroupSource"),
            ArendBundle.message("arend.disableActionExclude"),
            ArendBundle.message("arend.disableActionUnmark"),
            ArendBundle.message("arend.disableActionCompile")
        ).forEach { action ->
            actionManager?.getAction(action)?.let {
                actionManager.unregisterAction(action)
            }
        }
        TypedHandlerDelegate.EP_NAME.point.unregisterExtension(SelectionQuotingTypedHandler::class.java)
    }
}
