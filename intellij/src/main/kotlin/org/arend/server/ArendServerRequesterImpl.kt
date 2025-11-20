package org.arend.server

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findDirectory
import com.intellij.psi.PsiFileSystemItem
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.ext.module.ModuleLocation
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.DataModuleReferable
import org.arend.naming.reference.FullModuleReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.ReferableBase
import org.arend.term.abs.AbstractReferable
import org.arend.term.abs.AbstractReference
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.group.ConcreteGroup
import org.arend.toolWindow.repl.ArendReplService
import org.arend.util.FileUtils
import org.arend.util.addGeneratedModule
import org.arend.util.findInternalLibrary
import org.arend.util.findLibrary
import org.arend.util.moduleConfigs
import java.util.function.Supplier

class ArendServerRequesterImpl(private val project: Project) : ArendServerRequester {
    override fun requestModuleUpdate(server: ArendServer, module: ModuleLocation) {
        if (module.locationKind == ModuleLocation.LocationKind.GENERATED) return
        val repl = project.service<ArendReplService>().getRepl()
        runReadAction {
            val file = if (server == repl?.getServer()) {
                repl.replLibraries[module.libraryName]?.findArendFile(module.modulePath, module.locationKind == ModuleLocation.LocationKind.TEST)
            } else {
                project.findLibrary(module.libraryName)?.findArendFile(module.modulePath, module.locationKind == ModuleLocation.LocationKind.TEST)
            }
            doUpdateModule(server, module, file ?: return@runReadAction)
        }
    }

    override fun setupGeneratedModule(module: ModuleLocation, group: ConcreteGroup) {
        project.addGeneratedModule(module, group)
    }

    override fun getFiles(libraryName: String, inTests: Boolean, prefix: List<String>): List<String>? {
        val library = project.findLibrary(libraryName) ?: project.service<ArendReplService>().getRepl()?.replLibraries[libraryName] ?: return null
        var dir = (if (inTests) library.testsDirFile else library.sourcesDirFile) ?: return null
        for (name in prefix) {
            dir = dir.findDirectory(name) ?: return null
        }
        return dir.children.mapNotNull { when {
            it.isDirectory -> it.name
            it.name.endsWith(FileUtils.EXTENSION) -> it.name.removeSuffix(FileUtils.EXTENSION)
            else -> null
        } }
    }

    override fun <T : Any?> runUnderReadLock(supplier: Supplier<T?>): T? = runReadAction {
        supplier.get()
    }

    override fun fixModuleReferences(referables: List<Referable>): List<Referable> {
        val last = referables.lastOrNull() as? DataModuleReferable ?: return referables
        var file: PsiFileSystemItem? = last.data as? ArendFile ?: return referables

        val result = ArrayList<Referable>()
        val path = ArrayList(last.location.modulePath.toList())
        runReadAction {
            while (path.isNotEmpty()) {
                val current =
                    ModuleLocation(last.location.libraryName, last.location.locationKind, ModulePath(ArrayList(path)))
                result.add(if (file == null) FullModuleReferable(current) else DataModuleReferable(file, current))
                file = file?.parent
                path.removeLast()
            }
        }
        result.reverse()
        return result
    }

    override fun addReference(reference: AbstractReference, referable: Referable) {
        (reference as? ArendReferenceElement)?.putResolved(referable)
    }

    override fun addReference(module: ModuleLocation, referable: AbstractReferable, tcReferable: TCDefReferable) {
        (referable as? ReferableBase<*>)?.tcReferable = tcReferable
    }

    override fun addModuleDependency(module: ModuleLocation, dependency: ModuleLocation) {}

    private fun requestUpdate(server: ArendServer, modules: List<ModulePath>, library: String, inTests: Boolean) {
        for (module in modules) {
            val file = project.findInternalLibrary(library)?.findArendFile(module, inTests)
            if (file != null) {
                doUpdateModule(server, ModuleLocation(library, if (inTests) ModuleLocation.LocationKind.TEST else ModuleLocation.LocationKind.SOURCE, module), file)
            }
        }
    }

    private fun requestUpdate(server: ArendServer, config: ArendModuleConfigService, withTests: Boolean) {
        requestUpdate(server, config.findModules(false), config.name, false)
        if (withTests) {
            requestUpdate(server, config.findModules(true), config.name, true)
        }
    }

    fun doUpdateModule(server: ArendServer, module: ModuleLocation, file: ArendFile) = server.updateModule(file.modificationStamp, module) {
      ConcreteBuilder.convertGroup(file, module, DummyErrorReporter.INSTANCE)
    }

    fun requestUpdate(server: ArendServer, library: String?, withTests: Boolean) {
        runReadAction {
            if (library == null) {
                for (config in project.moduleConfigs) {
                    requestUpdate(server, config, withTests)
                }
            } else {
                requestUpdate(server, project.findInternalLibrary(library) as? ArendModuleConfigService ?: return@runReadAction, withTests)
            }
        }
    }
}