package org.arend.aifeatures

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.mcp.McpTool
import org.arend.server.ArendServerService

@Service(Service.Level.PROJECT)
class McpToolRegistryService(val project: Project) {
  private val tools = mutableMapOf<String, McpTool>()

  init {
    McpToolFactory.createAllTools(project).forEach { register(it) }
  }

  fun getTools() = tools.keys.toList()

  fun register(tool: McpTool) {
    tools[tool.name] = tool
  }

  fun execute(toolName: String, args: String): String {
    val tool = tools[toolName] ?: throw IllegalArgumentException("Unknown tool: $toolName")
    return tool.execute(args)
  }

  fun getAllTools(): List<McpTool> = tools.values.toList()
}
