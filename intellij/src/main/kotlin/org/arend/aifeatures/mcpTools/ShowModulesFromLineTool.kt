package org.arend.aifeatures.mcpTools

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.aifeatures.McpTool
import org.arend.aifeatures.RequestDataWithModuleAndLines
import org.arend.aifeatures.mcpTools.common.outputFile
import org.arend.aifeatures.parseDataWithModulesAndLines
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import kotlin.math.max
import kotlin.math.min

class ShowModulesFromLineTool : McpTool {
    override val name: String = "ShowModulesFromLine"
    override val description: String = "Shows the content of a given file from line1 to line2. " +
      "You must send it the full name of the file (module) as a string and the line numbers as integers. " +
      "Then, the tool will return the content of this file from line1 to max(line2, line1 + 500)." +
      "Provide the line numbers as a string in the format \"line1-line2\" or \"line1\". In the latter case the tool takes line2 = line1+ 500."

    override fun getInputSchema(): JsonObject =
      buildJsonObject {
        putJsonObject("libraryPath") {
          put("type", "string")
        }
        putJsonObject("line1-line2"){
          put("type", "string")
        }
        putJsonObject("modulePath") {
          put("type", "string")
        }
      }

    override fun execute(project: Project, arguments: String): String {
      val parsedUserRequest : RequestDataWithModuleAndLines = parseDataWithModulesAndLines(arguments)
      val module = parsedUserRequest.modulePaths
      val libPath = parsedUserRequest.libPath
      val lineStart = parsedUserRequest.lineStart
      val lineEnd = parsedUserRequest.lineEnd
      println("parsedUserRequest: $parsedUserRequest")

      val path = ModulePath.fromString(module.split("/").last())
      val sourceLocation = ModuleLocation(libPath, ModuleLocation.LocationKind.SOURCE, path)
      val outputFileLines = outputFile(project, sourceLocation).lines()
      val startRange : Int = max(0, lineStart)
      val endRange : Int = min(outputFileLines.size, lineEnd)

      val slicedOutput = outputFileLines.slice(startRange until endRange).joinToString("\n")
      println(slicedOutput)
      return "Showing the lines ${startRange} - ${endRange} lines out of 0 - ${outputFileLines.size} of the file:\n \"$slicedOutput"
    }
  }