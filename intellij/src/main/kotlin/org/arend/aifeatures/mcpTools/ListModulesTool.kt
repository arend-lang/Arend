package org.arend.aifeatures.mcpTools

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.*
import org.arend.aifeatures.McpTool
import org.arend.ext.module.ModulePath
import org.arend.module.config.LibraryConfig
import org.arend.util.findExternalLibrary
import org.arend.util.moduleConfigs

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
        val result = StringBuilder()
        
        // Collect all library configs (internal modules from project)
        val internalConfigs = project.moduleConfigs
        
        // Collect external library configs from dependencies
        val allLibraryConfigs = mutableListOf<LibraryConfig>()
        allLibraryConfigs.addAll(internalConfigs)
        
        // Get external libraries from dependencies
        for (config in internalConfigs) {
            for (depName in config.libraryDependencies) {
                val externalConfig = project.findExternalLibrary(depName)
                if (externalConfig != null && allLibraryConfigs.none { it.name == externalConfig.name }) {
                    allLibraryConfigs.add(externalConfig)
                }
            }
        }

        result.appendLine("=== Libraries ===")
        if (allLibraryConfigs.isEmpty()) {
            result.appendLine("No libraries found.")
        } else {
            for (config in allLibraryConfigs.sortedBy { it.name }) {
                result.appendLine("- ${config.name}")
            }
        }
        result.appendLine()
        
        // Get all modules from file system (not depending on typechecking)
        val modulesByLibrary = mutableMapOf<String, MutableList<Pair<ModulePath, String>>>()
        
        for (config in allLibraryConfigs) {
            val libName = config.name
            val libModules = modulesByLibrary.getOrPut(libName) { mutableListOf() }
            
            // Get source modules
            val sourceModules = config.findModules(false)
            for (modulePath in sourceModules) {
                libModules.add(modulePath to "SOURCE")
            }
            
            // Get test modules
            val testModules = config.findModules(true)
            for (modulePath in testModules) {
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
                    // Try to find description by full path, path without leading dot, or partial path
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
