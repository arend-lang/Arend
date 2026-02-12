package org.arend.aifeatures.mcpTools

import com.intellij.openapi.application.runReadAction
import org.arend.aifeatures.McpTool
import org.arend.search.proof.generateProofSearchResults
import com.intellij.openapi.project.Project
import org.arend.search.proof.ProofSearchEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.buildJsonObject

class ProofSearcherTool() : McpTool {
  override val name: String = "mcp_arend_Proof_search"
  override val description: String = ""
  private val proofSearchDoneMarker = "PROOF_SEARCH_DONE"


  override fun getInputSchema(): JsonObject  = buildJsonObject {
    putJsonObject("query") {
      put("type", "string")
    }
  }

  override fun execute(project : Project, arguments: String): String {
    val query = arguments
    val resultsOfProofSearch = executeProofSearch(project, query)
//    val outputFile = File(project.basePath!! + "/.junieCommunication/proofSearchResults.txt")
//    outputFile.writeText(resultsOfProofSearch)
//    outputFile.appendText("\n$proofSearchDoneMarker")
    return resultsOfProofSearch
  }

  private fun executeProofSearch(project: Project, query: String): String {
    val results: Sequence<ProofSearchEntry?> = generateProofSearchResults(project, query)
    return runReadAction {
      results.filterNotNull().joinToString("\n") { entry ->
        "${entry.def.refName} at ${entry.def.containingFile.virtualFile?.path}"
      }
    }
  }
}