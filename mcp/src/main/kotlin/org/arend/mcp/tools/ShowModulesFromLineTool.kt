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
import kotlin.math.max
import kotlin.math.min

class ShowModulesFromLineTool(private val server: ArendServer? = null) : McpTool {
    override val name: String = "ShowModulesFromLine"
    override val description: String = "Shows the content of a given file from line1 to line2. " +
        "You must send it the full name of the file (module) as a string and the line numbers as integers. " +
        "Then, the tool will return the content of this file from line1 to max(line2, line1 + 500). " +
        "Provide the line numbers as a string in the format \"line1-line2\" or \"line1\". In the latter case the tool takes line2 = line1 + 500."

    @Serializable
    private data class ShowModulesFromLineInput(
        val libraryPath: String = "",
        val modulePath: String = "",
        val lineStart: Int = 0,
        val lineEnd: Int = 500
    )

    override fun getInputSchema(): JsonObject = buildJsonObject {
        putJsonObject("libraryPath") {
            put("type", "string")
        }
        putJsonObject("modulePath") {
            put("type", "string")
        }
        putJsonObject("lineStart") {
            put("type", "integer")
        }
        putJsonObject("lineEnd") {
            put("type", "integer")
        }
    }

    override fun execute(arguments: String): String {
        val input = try {
            Json.decodeFromString<ShowModulesFromLineInput>(arguments)
        } catch (e: Exception) {
            return "Error: Failed to parse arguments: ${e.message}"
        }
        val module = input.modulePath
        val libPath = input.libraryPath
        val lineStart = input.lineStart
        val lineEnd = if (input.lineEnd == 500 && input.lineStart != 0) input.lineStart + 500 else input.lineEnd

        val libraryName = java.io.File(libPath).name
        val path = ModulePath.fromString(module.split("/").last())
        val sourceLocation = ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, path)
        val srv = server ?: return "Server is not initialized"
        var fileContent = outputFile(srv, sourceLocation)
        if (fileContent.startsWith("Error:")) {
            for (lib in srv.getLibraries()) {
                if (lib == libraryName) continue
                val depContent = outputFile(srv, ModuleLocation(lib, ModuleLocation.LocationKind.SOURCE, path))
                if (!depContent.startsWith("Error:")) {
                    fileContent = depContent
                    break
                }
            }
        }
        if (fileContent.startsWith("Error:")) return fileContent
        val outputFileLines = fileContent.lines()
        val startRange = max(0, lineStart)
        val endRange = min(outputFileLines.size, lineEnd)
        val slicedOutput = outputFileLines.slice(startRange until endRange).joinToString("\n")
        return "Showing the lines $startRange - $endRange lines out of 0 - ${outputFileLines.size} of the file:\n \"$slicedOutput"
    }
}
