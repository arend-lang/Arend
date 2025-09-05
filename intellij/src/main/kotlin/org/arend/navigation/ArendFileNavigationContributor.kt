package org.arend.navigation

import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.containers.addIfNotNull
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.DataContainer
import org.arend.server.ArendServerService
import org.arend.util.FileUtils

class ArendFileNavigationContributor : ChooseByNameContributor {
    override fun getNames(project: Project?, includeNonProjectItems: Boolean): Array<String> {
        project ?: return emptyArray()
        return project.service<ArendServerService>().server.modules.filter { it.locationKind == ModuleLocation.LocationKind.GENERATED }.map { it.modulePath.toString() + FileUtils.EXTENSION }.toTypedArray()
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
        val modulePath = ModulePath.fromString(FileUtil.getNameWithoutExtension(name))
        val server = project.service<ArendServerService>().server
        for (lib in server.libraries) {
            val group = server.getRawGroup(ModuleLocation(lib, ModuleLocation.LocationKind.GENERATED, modulePath))
            result.addIfNotNull((group?.referable as? DataContainer)?.data as? NavigationItem)
        }
        return result.toTypedArray()
    }
}