package org.arend.aifeatures.mcpTools

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.module.ModuleLocation
import org.arend.server.ArendServerService
import org.arend.mcp.tools.ShowModulesFromLineTool

class ShowModulesFromLineTool(private val project: Project) : ShowModulesFromLineTool(project.service<ArendServerService>().server) {
  override fun outputFile(moduleLocation: ModuleLocation): String =
    org.arend.aifeatures.mcpTools.common.outputFile(project, moduleLocation)
}