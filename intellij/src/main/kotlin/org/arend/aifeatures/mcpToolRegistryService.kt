package org.arend.aifeatures

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.arend.aifeatures.mcpTools.ProofSearcherTool
import org.arend.aifeatures.mcpTools.ListModulesTool

@Service
class McpToolRegistryService {
  private val tools = mutableMapOf<String, McpTool>()
  private val delimiter = "|||"

  init {
    McpToolFactory.createAllTools().forEach { register(it) }
    println(tools.keys.toList())
  }

  fun getDelimiter() = delimiter

  fun getTools() = tools.keys.toList()

  fun register(tool: McpTool) {
    tools[tool.name] = tool
  }

  fun execute(toolName: String, args: String, project: Project): String {
    println("tools : $tools")
    println("toolName : $toolName")
    val tool = tools[toolName] ?: throw IllegalArgumentException("Unknown tool: $toolName")
    return tool.execute(project, args)
  }

  // Helper to list tools for MCP discovery later
  fun getAllTools(): List<McpTool> = tools.values.toList()
}