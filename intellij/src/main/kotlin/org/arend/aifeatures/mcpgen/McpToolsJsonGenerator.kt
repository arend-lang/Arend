package org.arend.aifeatures.mcpgen

import kotlinx.serialization.json.*
import org.arend.aifeatures.mcpTools.FixImportsTool
import org.arend.aifeatures.mcpTools.ProofSearcherTool
import org.arend.mcp.tools.ListModulesTool
import org.arend.mcp.tools.SearchSymbolsTool
import org.arend.mcp.tools.ShowModulesFromLineTool
import org.arend.mcp.tools.ShowModulesTool
import org.arend.mcp.tools.TypecheckerTool
import java.io.File

object McpToolsJsonGenerator {

  @JvmStatic
  fun main(args: Array<String>) {
    val outputPath = args.getOrNull(0) ?: "build/resources/main/mcp-tools.json"
    generateToolsJson(outputPath)
  }

  private fun generateToolsJson(outputPath: String) {
    val tools = listOf(
      ListModulesTool(),
      ShowModulesTool(),
      ShowModulesFromLineTool(),
      SearchSymbolsTool(),
      TypecheckerTool(),
      ProofSearcherTool(),
      FixImportsTool()
    )

    val toolsArray = tools.map { tool ->
      buildJsonObject {
        put("name", tool.name)
        put("description", tool.description)
        put("inputSchema", tool.getInputSchema())
      }
    }

    val rootObject = buildJsonObject {
      put("tools", JsonArray(toolsArray))
    }

    val file = File(outputPath)
    file.parentFile?.mkdirs()

    // Pretty print JSON
    val prettyJson = Json { prettyPrint = true }
    file.writeText(prettyJson.encodeToString(JsonObject.serializer(), rootObject))

    println("MCP tools manifest generated at: ${file.absolutePath}")
    println("Generated ${tools.size} tools:")
    tools.forEach { println("  - ${it.name}") }
  }
}