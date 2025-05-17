package org.arend.navigation

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.arend.ext.module.ModulePath
import org.arend.module.config.LibraryConfig
import org.arend.prelude.Prelude
import org.arend.server.ArendServerService
import org.arend.util.FileUtils
import org.arend.util.findLibrary

class ArendFileNavigationContributor : ChooseByNameContributor {
    override fun getNames(project: Project?, includeNonProjectItems: Boolean): Array<String> {
        project ?: return emptyArray()
        val result = mutableListOf(Prelude.MODULE_PATH.toString() + FileUtils.EXTENSION)
        val service = project.service<ArendServerService>()
        forEachArendRawLib(service) { lib ->
            lib.additionalModulesSet.forEach { modulePath ->
                result.add(modulePath.toString() + FileUtils.EXTENSION)
            }
        }
        return result.toTypedArray()
    }

    override fun getItemsByName(
            name: String?,
            pattern: String?,
            project: Project?,
            includeNonProjectItems: Boolean
    ): Array<NavigationItem> {
        project ?: return emptyArray()
        name ?: return emptyArray()
        val result = mutableListOf<NavigationItem>()
        if (name == Prelude.MODULE_PATH.toString() + FileUtils.EXTENSION) {
            project.service<ArendServerService>().prelude?.let { result.add(it) }
        }
        val modulePath = ModulePath.fromString(FileUtil.getNameWithoutExtension(name))
        val service = project.service<ArendServerService>()
        forEachArendRawLib(service) { lib ->
            lib.findArendFile(modulePath, withAdditional = true, withTests = false)?.let { file ->
                result.add(file)
            }
        }
        return result.toTypedArray()
    }
}

private fun forEachArendRawLib(service: ArendServerService, action: (LibraryConfig) -> Unit) {
    for (library in service.server.libraries) {
        service.project.findLibrary(library)?.let(action)
    }
}