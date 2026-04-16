package org.arend.mcp.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.mcp.McpTool
import org.arend.server.ArendServer

open class ShowModulesTool(private val server: ArendServer? = null) : McpTool {
  private val numberOfAllowedLines: Int = 500
  override val name: String = "ShowModules"
  override val description: String = "Shows the content of a given file. " +
        "You must send it the full name of the file (module) as a string. " +
        "Then, the tool will return the content of this file. " +
        "If the file is bigger than $numberOfAllowedLines lines, only the first $numberOfAllowedLines lines will be returned. " +
        "In the case the file is larger than $numberOfAllowedLines lines, use another tool that shows the content of the file starting from a given line."

  override fun getInputSchema(): JsonObject = buildJsonObject {
      putJsonObject("libraryPath") {
          put("type", "string")
      }
      putJsonObject("modulePath") {
          put("type", "string")
      }
  }

    @Serializable
    private data class ShowModulesInput(val libraryPath: String = "", val modulePath: String = "")

    override fun execute(arguments: String): String {
        val input = try {
            Json.decodeFromString<ShowModulesInput>(arguments)
        } catch (e: Exception) {
            return "Error: Failed to parse arguments: ${e.message}"
        }

        val module = input.modulePath
        val libPath = input.libraryPath

        if (module.isBlank()) {
            return "Error: No module path provided"
        }

        if (libPath.isBlank()) {
            return "Error: No library path provided"
        }

        val libraryName = java.io.File(libPath).name
        val path = ModulePath.fromString(module.split("/").last())
        val sourceLocation = ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, path)
        var fileContent = outputFile(sourceLocation)

        if (fileContent.startsWith("Error:")) {
            val allLibraries = server?.getLibraries() ?: emptySet()
            for (lib in allLibraries) {
                if (lib == libraryName) continue
                val depLocation = ModuleLocation(lib, ModuleLocation.LocationKind.SOURCE, path)
                val depContent = outputFile(depLocation)
                if (!depContent.startsWith("Error:")) {
                    fileContent = depContent
                    break
                }
            }
        }

        if (fileContent.startsWith("Error:")) {
            return fileContent
        }

        val lines = fileContent.split("\n")
        if (lines.size <= numberOfAllowedLines) {
            return "Showing the whole file:\n\"$fileContent\""
        }

        val truncatedContent = lines.take(numberOfAllowedLines).joinToString("\n")
        return "Showing the first $numberOfAllowedLines lines of the file:\n\"$truncatedContent\""
    }

  protected open fun outputFile(moduleLocation: ModuleLocation): String {
    return server?.let { outputFile(it, moduleLocation) } ?: "Server is not initialized"
  }
}
