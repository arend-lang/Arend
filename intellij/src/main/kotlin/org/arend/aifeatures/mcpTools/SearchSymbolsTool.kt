package org.arend.aifeatures.mcpTools

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import kotlinx.serialization.json.*
import org.arend.aifeatures.McpTool
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.fullNameText
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.psi.stubs.index.ArendGotoClassIndex

/**
 * MCP Tool that searches for Arend definitions by name pattern,
 * similar to IntelliJ's Command+O (Go to Class) functionality.
 */
class SearchSymbolsTool : McpTool {
    override val name: String = "SearchSymbols"

    override val description: String = "Searches for Arend definitions (classes, functions, data types, etc.) by name pattern, " +
        "similar to Command+O in IntelliJ. Provide a search pattern (substring) to find matching symbols in the project. " +
        "Returns a list of matching definitions with their full qualified names and file locations. " +
        "Example: {\"pattern\":\"Nat\"} will find all definitions containing 'Nat' in their name."

    override fun getInputSchema(): JsonObject = buildJsonObject {
        putJsonObject("pattern") {
            put("type", "string")
            put("description", "The name or part of the name of the definition to search for")
        }
    }

    override fun execute(project: Project, arguments: String): String {
        val pattern = parsePattern(arguments)

        if (pattern.isBlank()) {
            return "Error: No search pattern provided. Please provide a pattern to search for."
        }

        val results = mutableListOf<SearchResult>()
        val scope = GlobalSearchScope.allScope(project)

        // Search using ArendDefinitionIndex (covers all definitions)
        val allDefinitionKeys = StubIndex.getInstance().getAllKeys(ArendDefinitionIndex.KEY, project)
        val matchedDefinitionKeys = allDefinitionKeys.filter { it.contains(pattern, ignoreCase = true) }

        for (key in matchedDefinitionKeys) {
            val elements = StubIndex.getElements(
                ArendDefinitionIndex.KEY,
                key,
                project,
                scope,
                PsiReferable::class.java
            )
            for (element in elements) {
                val fullName = if (element is PsiLocatedReferable) {
                    element.fullNameText
                } else {
                    element.refName ?: key
                }
                val fileName = element.containingFile?.name ?: "unknown"
                val filePath = element.containingFile?.virtualFile?.path ?: "unknown"

                results.add(SearchResult(
                    name = key,
                    fullName = fullName,
                    fileName = fileName,
                    filePath = filePath,
                    kind = getDefinitionKind(element)
                ))
            }
        }

        // Also search using ArendGotoClassIndex for classes specifically
        val allClassKeys = StubIndex.getInstance().getAllKeys(ArendGotoClassIndex.KEY, project)
        val matchedClassKeys = allClassKeys.filter {
            it.contains(pattern, ignoreCase = true) &&
            matchedDefinitionKeys.none { defKey -> defKey == it }
        }

        for (key in matchedClassKeys) {
            val elements = StubIndex.getElements(
                ArendGotoClassIndex.KEY,
                key,
                project,
                scope,
                PsiReferable::class.java
            )
            for (element in elements) {
                val fullName = if (element is PsiLocatedReferable) {
                    element.fullNameText
                } else {
                    element.refName ?: key
                }
                val fileName = element.containingFile?.name ?: "unknown"
                val filePath = element.containingFile?.virtualFile?.path ?: "unknown"

                // Avoid duplicates
                if (results.none { it.fullName == fullName }) {
                    results.add(SearchResult(
                        name = key,
                        fullName = fullName,
                        fileName = fileName,
                        filePath = filePath,
                        kind = "class"
                    ))
                }
            }
        }

        if (results.isEmpty()) {
            return "No definitions found matching pattern '$pattern'"
        }

        // Sort results by name and limit to reasonable number
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

    private fun parsePattern(arguments: String): String {
        if (arguments.isBlank()) return ""

        return try {
            val jsonElement = Json.parseToJsonElement(arguments)
            val jsonObject = jsonElement.jsonObject
            jsonObject["pattern"]?.jsonPrimitive?.contentOrNull ?: ""
        } catch (e: Exception) {
            // If not valid JSON, treat the whole argument as the pattern
            arguments.trim()
        }
    }

    private fun getDefinitionKind(element: PsiReferable): String {
        val className = element.javaClass.simpleName
        return when {
            className.contains("DefClass") -> "class"
            className.contains("DefData") -> "data"
            className.contains("DefFunction") || className.contains("DefFunc") -> "function"
            className.contains("DefInstance") -> "instance"
            className.contains("DefModule") -> "module"
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
