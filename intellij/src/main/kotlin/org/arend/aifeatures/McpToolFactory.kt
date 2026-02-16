// src/main/kotlin/org/arend/aifeatures/McpToolFactory.kt
package org.arend.aifeatures

import org.arend.aifeatures.mcpTools.ListModulesTool
import org.arend.aifeatures.mcpTools.ListSimplifiedModulesTool
import org.arend.aifeatures.mcpTools.ProofSearcherTool
import org.arend.aifeatures.mcpTools.TypecheckerTool

object McpToolFactory {
  /**
   * Returns all tool instances.
   * This can be called at build time since tools don't need Project for metadata.
   */
  fun createAllTools(): List<McpTool> {
    return listOf(
      TypecheckerTool(),
      ProofSearcherTool(),
      ListModulesTool(),
      ListSimplifiedModulesTool()
    )
  }
}