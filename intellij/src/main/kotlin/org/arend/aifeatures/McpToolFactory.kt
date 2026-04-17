// src/main/kotlin/org/arend/aifeatures/McpToolFactory.kt
package org.arend.aifeatures

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.aifeatures.mcpTools.FixImportsTool
import org.arend.aifeatures.mcpTools.ProofSearcherTool
import org.arend.mcp.McpTool
import org.arend.mcp.tools.ListModulesTool
import org.arend.mcp.tools.SearchSymbolsTool
import org.arend.mcp.tools.ShowModulesFromLineTool
import org.arend.mcp.tools.TypecheckerTool
import org.arend.server.ArendServerService

object McpToolFactory {
  fun createAllTools(project: Project): List<McpTool> {
    val server = project.service<ArendServerService>().server
    return listOf(
      ProofSearcherTool(project),
      ListModulesTool(server),
      org.arend.aifeatures.mcpTools.ShowModulesTool(project),
      ShowModulesFromLineTool(server),
      SearchSymbolsTool(server),
      TypecheckerTool(server),
      FixImportsTool(project),
    )
  }
}
