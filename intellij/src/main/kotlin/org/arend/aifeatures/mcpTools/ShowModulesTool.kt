package org.arend.aifeatures.mcpTools

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.aifeatures.mcpTools.common.outputFile
import org.arend.ext.module.ModuleLocation
import org.arend.mcp.tools.ShowModulesTool
import org.arend.server.ArendServerService

class ShowModulesTool(private val project: Project) : ShowModulesTool(project.service<ArendServerService>().server) {
  override fun outputFile(moduleLocation: ModuleLocation): String =
    outputFile(project, moduleLocation)
}
