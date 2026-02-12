package org.arend.aifeatures
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject

interface McpTool {
  val name: String
  val description: String


  fun getInputSchema(): JsonObject

  fun execute(project : Project, arguments: String): String
}