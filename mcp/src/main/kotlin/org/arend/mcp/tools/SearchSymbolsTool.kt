package org.arend.mcp.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

import org.arend.mcp.McpTool
import org.arend.server.ArendServer
import org.arend.server.ProgressReporter
import org.arend.term.group.ConcreteGroup
import org.arend.typechecking.computation.UnstoppableCancellationIndicator

class SearchSymbolsTool(private val server: ArendServer? = null) : McpTool {
  override val name: String = "SearchSymbols"
  override val description: String = "Searches for Arend definitions (classes, functions, data types, etc.) by name pattern, " +
        "similar to Command+O in IntelliJ. Provide a search pattern (substring) to find matching symbols in the project and its dependencies. " +
        "Returns a list of matching definitions with their full qualified names and file locations. " +
        "Example: 'Nat' will find all definitions containing 'Nat' in their name. "


  @Serializable
  private data class SearchInput(val libraryPath: String = "", val pattern: String = "", val libraryFilter: String = "")

  override fun getInputSchema(): JsonObject = buildJsonObject {
    putJsonObject("libraryPath") {
      put("type", "string")
    }
    putJsonObject("pattern") {
        put("type", "string")
        put("description", "The name or part of the name of the definition to search for.")
    }
  }

  override fun execute(arguments: String): String {
    val input = try {
        Json.decodeFromString<SearchInput>(arguments)
    } catch (e: Exception) {
        return "Error: Failed to parse arguments: ${e.message}"
    }
    val pattern = input.pattern

    if (pattern.isBlank()) {
        return "Error: No search pattern provided. Please provide a pattern to search for."
    }

    val results = mutableListOf<SearchResult>()
    val modules = server?.getModules()?.toList() ?: return "Server not initialized."

    server.getCheckerFor(modules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())

    for (moduleLocation in modules) {
        val group = server.getRawGroup(moduleLocation) ?: continue
        group.traverseGroup { concreteGroup ->
            if (concreteGroup.isTopLevel) return@traverseGroup
            val referable = concreteGroup.referable
            val name = referable.textRepresentation()
            if (name.contains(pattern, ignoreCase = true)) {
              val fullName = referable.getRefLongName().toString()
              val filePath = getFilePath(moduleLocation.libraryName, moduleLocation.modulePath.toString())
              val fileName = moduleLocation.modulePath.lastName + ".ard"
              val kind = getDefinitionKind(concreteGroup)
              results.add(SearchResult(
                name = name,
                fullName = "${moduleLocation.libraryName}.$fullName",
                fileName = fileName,
                filePath = filePath,
                kind = kind
              ))
            }
        }
    }

    if (results.isEmpty()) {
      return "No definitions found matching pattern '$pattern'"
    }

    val sortedResults = results.sortedBy { it.fullName }.take(100)
    val output = StringBuilder()
    output.appendLine("Search results for pattern '$pattern':")
    output.appendLine("Found ${results.size} definitions" +
        if (results.size > 100) " (showing first 100)" else "")
    output.appendLine()

    for ((index, result) in sortedResults.withIndex()) {
        output.appendLine("${index + 1}. ${result.fullName}")
        output.appendLine("   Kind: ${result.kind}")
        output.appendLine("   File: ${result.fileName}")
        output.appendLine("   Path: ${result.filePath}")
        output.appendLine()
    }
    return output.toString()
  }

    private fun getFilePath(libraryName: String, modulePath: String): String {
        val library = server?.getLibrary(libraryName)
            ?: return "$libraryName:$modulePath"
        val fileSourceLibrary = library as? org.arend.frontend.library.FileSourceLibrary
            ?: return "$libraryName:$modulePath"
        return fileSourceLibrary.getSourceBasePath()?.resolve(modulePath.replace(".", "/") + ".ard").toString()
    }


    private fun getDefinitionKind(group: ConcreteGroup): String {
        val definition = group.definition ?: return "module"
        val className = definition.javaClass.simpleName
        return when {
            className.contains("ClassDefinition") -> "class"
            className.contains("DataDefinition") -> "data"
            className.contains("FunctionDefinition") -> "function"
            className.contains("InstanceDefinition") -> "instance"
            className.contains("Constructor") -> "constructor"
            className.contains("ClassField") -> "field"
            else -> "definition"
        }
    }

    private data class SearchResult(
        val name: String,
        val fullName: String,
        val fileName: String,
        val filePath: String,
        val kind: String
    )
}
