package org.arend.mcp.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.arend.mcp.McpTool
import org.arend.server.ArendServer
import org.arend.server.ProgressReporter
import org.arend.term.group.ConcreteGroup
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.typechecking.computation.UnstoppableCancellationIndicator

class ShowDefinitionTool(private val server: ArendServer? = null) : McpTool {
  override val name: String = "ShowDefinition"
  override val description: String = "Shows the pretty-printed content of an Arend definition by its full qualified name. " +
      "The full name should be in the format returned by SearchSymbols, e.g. 'arend-lib.Algebra.Ring.Ring'. " +
      "The first component is the library name, followed by the module path and definition path separated by dots."

  @Serializable
  private data class ShowDefinitionInput(val libraryPath: String = "", val fullName: String = "")

  override fun getInputSchema(): JsonObject = buildJsonObject {
    putJsonObject("libraryPath") {
      put("type", "string")
    }
    putJsonObject("fullName") {
      put("type", "string")
      put("description", "The full qualified name of the definition, e.g. 'arend-lib.Algebra.Ring.Ring'")
    }
  }

  override fun execute(arguments: String): String {
    val input = try {
      Json.decodeFromString<ShowDefinitionInput>(arguments)
    } catch (e: Exception) {
      return "Error: Failed to parse arguments: ${e.message}"
    }

    val fullName = input.fullName
    if (fullName.isBlank()) {
      return "Error: No definition name provided."
    }

    val server = server ?: return "Server not initialized."

    val dotIndex = fullName.indexOf('.')
    if (dotIndex < 0) {
      return "Error: Invalid full name format. Expected 'libraryName.path.to.Definition'."
    }

    val libraryName = fullName.substring(0, dotIndex)
    val rest = fullName.substring(dotIndex + 1)

    // We need to find which module contains this definition.
    // The rest could be e.g. "Algebra.Ring.Ring" where "Algebra.Ring" is the module and "Ring" is the definition,
    // or "Algebra.Ring.Ring.+", etc. We try progressively shorter module paths.
    val modules = server.getModules().toList()
    server.getCheckerFor(modules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())

    val parts = rest.split(".")

    // Try each possible split: module path vs definition path
    for (moduleEnd in parts.size downTo 1) {
      val modulePath = parts.subList(0, moduleEnd).joinToString(".")
      val defPath = if (moduleEnd < parts.size) parts.subList(moduleEnd, parts.size) else emptyList()

      for (moduleLocation in modules) {
        if (moduleLocation.libraryName != libraryName) continue
        if (moduleLocation.modulePath.toString() != modulePath) continue

        val group = server.getRawGroup(moduleLocation) ?: continue

        // If no definition path, return the whole module
        if (defPath.isEmpty()) {
          return prettyPrintGroup(group)
        }

        // Navigate to the definition within the group
        val targetGroup = findSubgroup(group, defPath) ?: continue
        return prettyPrintGroup(targetGroup)
      }
    }

    return "Error: Definition '$fullName' not found."
  }

  private fun findSubgroup(group: ConcreteGroup, path: List<String>): ConcreteGroup? {
    if (path.isEmpty()) return group
    val name = path[0]
    val remaining = path.subList(1, path.size)

    // Search in static subgroups
    for (stmt in group.statements()) {
      val subgroup = stmt.group() ?: continue
      if (subgroup.referable().textRepresentation() == name) {
        return findSubgroup(subgroup, remaining)
      }
    }

    // Search in dynamic subgroups
    for (dynGroup in group.dynamicGroups()) {
      if (dynGroup.referable().textRepresentation() == name) {
        return findSubgroup(dynGroup, remaining)
      }
    }

    return null
  }

  private fun prettyPrintGroup(group: ConcreteGroup): String {
    val builder = StringBuilder()
    val visitor = PrettyPrintVisitor(builder, 0)
    visitor.printGroup(group)
    return builder.toString()
  }
}
