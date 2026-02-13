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

class ProofSearcherTool : McpTool {
  override val name = "mcp_arend_Proof_search"
  override val description = "Triggers Arend proof search for the query you send." +
    " You need to send it the full library path as a string and the query as a string." +
    "The grammar of Proof Search queries is defined as follows:\n" +
    "\n" +
    "  query ::= (and_pattern '->')* and_pattern\n" +
    "  and_pattern ::= (app_pattern '\\and')*\n" +
    "  app_pattern app_pattern ::= atom_pattern+\n" +
    "  atom_pattern ::= '_' | (IDENTIFIER '.')* IDENTIFIER | '(' app_pattern ')'" +
    "For example, the query Foo -> Bar will produce the following results:\n" +
    "\n" +
    "\\func foo (f : Foo) : Bar -- matched\n" +
    "\\func bar :    Foo -> Bar -- matched\n" +
    "\\func baz :           Bar -- not matched"


  override fun getInputSchema(): JsonObject  = buildJsonObject {
    putJsonObject("query") {
      put("type", "string")
    }
  }

  override fun execute(project : Project, arguments: String): String {
    val query = arguments.split("|||").dropLast(1).first()
    println("query: $query")

    val resultsOfProofSearch = executeProofSearch(project, query)
    return resultsOfProofSearch
  }

  private fun executeProofSearch(project: Project, query: String): String {
    val results: Sequence<ProofSearchEntry?> = generateProofSearchResults(project, query)
    // Materialize the lazy sequence to a list to ensure all results are collected
    val resultsList = results.filterNotNull().toList()
    val resultString = resultsList.joinToString("\n") { entry ->
      "${entry.def.refName} at ${entry.def.containingFile.virtualFile?.path}"
    }
    println("results from execute: $resultString")
    return runReadAction {
      resultString
    }
  }
}