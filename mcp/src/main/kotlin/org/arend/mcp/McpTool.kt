package org.arend.mcp

import kotlinx.serialization.json.JsonObject
import org.arend.server.ArendServer

interface McpTool {
  val name: String
  val description: String
  fun getInputSchema(): JsonObject
  fun execute(arguments: String): String
}
