package org.arend.aifeatures.mcpTools

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.aifeatures.McpTool
import org.arend.aifeatures.mcpTools.common.numberOfAllowedLines
import org.arend.aifeatures.mcpTools.common.outputFile
import org.arend.aifeatures.parseDataWithModules
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath

class ShowModulesTool : McpTool {
  override val name: String = "ShowModules"
//  TODO: add examples in description
//  TODO: make so that modules are shown not in full, but skipping
//   Prop realizations and putting {?Library_Statement} instead
//  TODO: support generated modules

  override val description: String = "Shows the content of a given file. " +
    "You must send it the full name of the file (module) as a string. " +
    "Then, the tool will return the content of this file. " +
    "If the file is bigger than $numberOfAllowedLines lines, only the first $numberOfAllowedLines lines will be returned." +
    "In the case the file is larger than $numberOfAllowedLines lines, use another tool that shows the content of the file starting from a given line."

  override fun getInputSchema(): JsonObject =
    buildJsonObject {
      putJsonObject("libraryPath") {
        put("type", "string")
      }
      putJsonObject("modulePath") {
        put("type", "string")
      }
    }

  override fun execute(project: Project, arguments: String): String {
    val parsedUserRequest = parseDataWithModules(arguments)
    
    if (parsedUserRequest.modulePaths.isEmpty()) {
      return "Error: No module path provided"
    }
    
    val module = parsedUserRequest.modulePaths.first()
    val libPath = parsedUserRequest.libPath
    
    if (libPath.isBlank()) {
      return "Error: No library path provided"
    }
    
    val libraryName = java.io.File(libPath).name
    val path = ModulePath.fromString(module.split("/").last())
    val sourceLocation = ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, path)

    val fileContent = outputFile(project, sourceLocation)
    
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
}