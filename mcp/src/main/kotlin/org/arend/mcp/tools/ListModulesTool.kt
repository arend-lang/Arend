package org.arend.mcp.tools

import kotlinx.serialization.json.*
import org.arend.ext.module.ModulePath
import org.arend.frontend.library.FileSourceLibrary
import org.arend.mcp.McpTool
import org.arend.server.ArendServer

class ListModulesTool(private val server: ArendServer? = null) : McpTool {
    override val name = "mcp_arend_List_modules"
    override val description = "Lists all supported modules from the current project and its library dependencies. " +
            "Returns full module identifiers. You need to send it the full library path as a string. " +
            "For example: {\"libraryPath\":\"/Users/username/Dev/myProject\"}"

    override fun getInputSchema(): JsonObject = buildJsonObject {
        putJsonObject("libraryPath") {
            put("type", "string")
        }
    }

    override fun execute(arguments: String): String {
        return listAllModules()
    }

    private fun listAllModules(): String {
        val result = StringBuilder()
        val libraryNames = server?.getLibraries()?.sorted() ?: return "Server not initialized."

        result.appendLine("=== Libraries ===")
        if (libraryNames.isEmpty()) {
            result.appendLine("No libraries found.")
        } else {
            for (name in libraryNames) {
                result.appendLine("- $name")
            }
        }
        result.appendLine()

        val modulesByLibrary = mutableMapOf<String, MutableList<Pair<ModulePath, String>>>()

        for (libName in libraryNames) {
            val library = server.getLibrary(libName) as? FileSourceLibrary ?: continue
            val libModules = modulesByLibrary.getOrPut(libName) { mutableListOf() }

            for (modulePath in library.findModules(false)) {
                libModules.add(modulePath to "SOURCE")
            }
            for (modulePath in library.findModules(true)) {
                libModules.add(modulePath to "TEST")
            }
        }

        val totalModules = modulesByLibrary.values.sumOf { it.size }
        result.appendLine("=== All Modules ($totalModules total) ===")

        if (totalModules == 0) {
            result.appendLine("No modules found.")
        } else {
            val arendLibJsonSummaries = object {}.javaClass.getResource("/org/arend/aifeatures/storedinfo/arendLibSummaries.json")?.readText()
            val descriptionMap = if (arendLibJsonSummaries != null) {
                parseSummaries(arendLibJsonSummaries).second
            } else {
                result.appendLine("Note: arendLibSummaries.json not found, module descriptions will not be available.")
                emptyMap()
            }
            for ((libName, libModules) in modulesByLibrary.toSortedMap()) {
                result.appendLine("\n[$libName]")
                for ((modulePath, locationKind) in libModules.sortedBy { it.first.toString() }) {
                    val modulePathString = modulePath.toString()
                    val moduleDescription: String? = descriptionMap[modulePathString]
                        ?: descriptionMap[modulePathString.removePrefix(".")]
                        ?: descriptionMap.entries.find { (name, _) -> modulePathString.endsWith(name) }?.value
                    if (moduleDescription != null) {
                        result.appendLine("  $modulePath (description: $moduleDescription) ($locationKind)")
                    } else {
                        result.appendLine("  $modulePath ($locationKind)")
                    }
                }
            }
        }
        return result.toString()
    }

    fun parseSummaries(jsonString: String): Pair<String, Map<String, String>> {
        val jsonObject = Json.parseToJsonElement(jsonString).jsonObject
        val version = jsonObject["version"]?.jsonPrimitive?.content ?: "unknown"
        val modulesArray = jsonObject["modules"]?.jsonArray ?: JsonArray(emptyList())
        val modulesMap = modulesArray.associate {
            val moduleObj = it.jsonObject
            val name = moduleObj["name"]?.jsonPrimitive?.content ?: ""
            val summary = moduleObj["summary"]?.jsonPrimitive?.content ?: ""
            name to summary
        }
        return version to modulesMap
    }
}
