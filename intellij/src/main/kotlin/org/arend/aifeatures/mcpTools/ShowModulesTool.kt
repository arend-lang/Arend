package org.arend.aifeatures.mcpTools

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.aifeatures.McpTool
import org.arend.aifeatures.mcpTools.common.outputFile
import org.arend.aifeatures.parseDataWithModules
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.server.ArendServerService
import org.arend.term.group.ConcreteGroup

class ShowModulesTool : McpTool {
  override val name: String = "ShowModules"
//  TODO: add examples in description
//  TODO: make so that modules are shown not in full, but skipping
//   Prop realizations and putting {?Library_Statement} instead
//  TODO: support generated modules

  override val description: String = "Shows the content of a given file. " +
    "You must send it the full name of the file (module) as a string. " +
    "Then, the tool will return the content of this file. " +
    "If the file is bigger than 500 lines, only the first 500 lines will be returned." +
    "In the case the file is larger than 500 lines, use another tool that shows the content of the file starting from a given line."

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
    val module = parsedUserRequest.modulePaths.first()
    val libPath = parsedUserRequest.libPath
    val libraryName = java.io.File(libPath).name
    val path = ModulePath.fromString(module.split("/").last())
    val sourceLocation = ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, path)
    println("parsedUserRequest: $parsedUserRequest")


    val outputFile : String = outputFile(project, sourceLocation)
    println("outputFile: $outputFile")
    if (outputFile.split("\n").size <= 500){
      return "Showing the whole file:\n \"$outputFile"
    }
    return "Showing the first 500 lines of the file:\n \"$outputFile"

  }
}