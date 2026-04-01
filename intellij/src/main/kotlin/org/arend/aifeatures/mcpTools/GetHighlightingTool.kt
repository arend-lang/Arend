package org.arend.aifeatures.mcpTools

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.aifeatures.McpTool
import org.arend.aifeatures.parseDataWithModules
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.highlight.ArendExternalAnnotator
import org.arend.util.findLibrary

class GetHighlightingTool : McpTool {
    override val name: String = "GetHighlighting"

    override val description: String = "Returns the highlighting information (errors, warnings, etc.) for a given Arend file. " +
        "You must send it the full name of the file (module) as a string. " +
        "The tool will return a list of highlighting entries with their message, text range (start and end offsets), and severity level."

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
        val module = parsedUserRequest.modulePaths.firstOrNull()
            ?: return "Error: No module path provided"
        val libPath = parsedUserRequest.libPath
        val libraryName = java.io.File(libPath).name
        val path = ModulePath.fromString(module.split("/").last())
        val sourceLocation = ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, path)

        val libraryConfig = project.findLibrary(sourceLocation.libraryName)
            ?: return "Error: Library '${sourceLocation.libraryName}' not found"

        val arendFile = libraryConfig.findArendFile(sourceLocation)
            ?: return "Error: File for module '${sourceLocation.modulePath}' not found in library '${sourceLocation.libraryName}'"

        val errors = ArendExternalAnnotator.getErrorsForFile(arendFile)

        if (errors.isEmpty()) {
            return "No highlighting information found for module '$module'"
        }

        val result = StringBuilder()
        result.appendLine("Highlighting information for module '$module':")
        result.appendLine("Total entries: ${errors.size}")
        result.appendLine()

        for ((index, error) in errors.withIndex()) {
            result.appendLine("Entry ${index + 1}:")
            result.appendLine("  Severity: ${error.severity.name}")
            result.appendLine("  Message: ${error.message}")
            result.appendLine("  Range: ${error.textRange.startOffset}-${error.textRange.endOffset}")
            result.appendLine()
        }

        return result.toString()
    }
}
