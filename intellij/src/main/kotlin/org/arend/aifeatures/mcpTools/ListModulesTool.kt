package org.arend.aifeatures.mcpTools

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import org.arend.aifeatures.McpTool
import org.arend.server.ArendServerService

/**
 * MCP Tool that lists all supported modules from the current project and its dependencies.
 * Returns full identifiers for each module.
 */
class ListModulesTool : McpTool {
    override val name = "mcp_arend_List_modules"
    override val description = "Lists all supported modules from the current project and its library dependencies. " +
            "Returns full module identifiers. You need to send it the full library path as a string. " +
            "For example: {\"libraryPath\":\"/Users/username/Dev/myProject\"}"

    override fun getInputSchema(): JsonObject = buildJsonObject {
        putJsonObject("libraryPath") {
            put("type", "string")
        }
    }

    override fun execute(project: Project, arguments: String): String {
//        val libraryPath = parseLibraryPath(arguments)
        return listAllModules(project)
    }

    private fun parseLibraryPath(arguments: String): String {
        if (arguments.isBlank()) return ""
        
        return try {
            val jsonElement = Json.parseToJsonElement(arguments)
            val jsonObject = jsonElement.jsonObject
            jsonObject["libraryPath"]?.jsonPrimitive?.contentOrNull ?: ""
        } catch (e: Exception) {
            arguments.trim() // If not valid JSON, treat the whole argument as library path
        }
    }

    private fun listAllModules(project: Project): String {
        val serverService = project.service<ArendServerService>()
        val server = serverService.server
        
        val result = StringBuilder()
        
        // Get all registered libraries
        val libraries = server.libraries
        result.appendLine("=== Registered Libraries ===")
        if (libraries.isEmpty()) {
            result.appendLine("No libraries registered.")
        } else {
            for (lib in libraries.sorted()) {
                result.appendLine("- $lib")
            }
        }
        result.appendLine()
        
        // Get all registered modules
        val modules = server.modules
        println("modules = $modules")
        result.appendLine("=== All Modules (${modules.size} total) ===")
        
        if (modules.isEmpty()) {
            result.appendLine("No modules registered.")
        } else {
            val arendLibJsonSummaries = object {}.javaClass.getResource("/org/arend/aifeatures/storedinfo/arendLibSummaries.json")?.readText()
            val descriptionMap = if (arendLibJsonSummaries != null) {
                parseSummaries(arendLibJsonSummaries).second
            } else {
                result.appendLine("Note: arendLibSummaries.json not found, module descriptions will not be available.")
                emptyMap()
            }
//            println("descriptionMap = $descriptionMap")

            // Group modules by library for better readability
            val modulesByLibrary = modules.groupBy { it.libraryName }
            
            for ((libName, libModules) in modulesByLibrary.toSortedMap()) {
                result.appendLine("\n[$libName]")
                for (module in libModules.sortedBy { it.toString() }) {
                    val modulePathString = module.modulePath.toString()
                    println(modulePathString)
                    // Try to find description by full path, path without leading dot, or partial path
                    val moduleDescription : String? = descriptionMap[modulePathString] 
                        ?: descriptionMap[modulePathString.removePrefix(".")]
                        ?: descriptionMap.entries.find { (name, _) -> modulePathString.endsWith(name) }?.value
                    
                    if (moduleDescription != null){
                      result.appendLine("  ${module.modulePath} (description : ${moduleDescription}) (${module.locationKind})")
                    } else {
                      result.appendLine("  ${module.modulePath} (${module.locationKind})")
                    }
                }
            }
        }
//        println(result)
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
