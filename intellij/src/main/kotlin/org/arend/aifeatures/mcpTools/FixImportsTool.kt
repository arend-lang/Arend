package org.arend.aifeatures.mcpTools

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiTreeUtil
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.mcp.McpTool
import org.arend.psi.ext.ArendReferenceElement
import org.arend.quickfix.referenceResolve.ArendImportHintAction
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.util.findLibrary

class FixImportsTool(private val project: Project? = null) : McpTool {
    override val name: String = "FixImports"
    override val description: String =
        "Applies 'fix imports' quick fix for all unresolved definitions in a given Arend file. " +
        "For each unresolved reference that has exactly one import candidate, the import is added automatically. " +
        "Returns a summary of which imports were added and which references had multiple or no candidates."

    override fun getInputSchema(): JsonObject =
        buildJsonObject {
            putJsonObject("libraryPath") {
                put("type", "string")
            }
            putJsonObject("modulePath") {
                put("type", "string")
            }
        }

    @Serializable
    private data class FixImportsInput(val modulePath: String, val libraryPath: String)

    override fun execute(arguments: String): String {
        val input = Json.decodeFromString<FixImportsInput>(arguments)
        val module = input.modulePath
        val libPath = input.libraryPath
        val libraryName = java.io.File(libPath).name
        val path = ModulePath.fromString(module.split("/").last())
        val sourceLocation = ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, path)

        val arendFile = runReadAction {
            val libraryConfig = project?.findLibrary(sourceLocation.libraryName) ?: return@runReadAction null
            libraryConfig.findArendFile(sourceLocation)
        } ?: return "Error: Module '$module' not found in library '$libraryName'"

        // Collect all unresolved reference elements
        val unresolvedRefs = runReadAction {
            PsiTreeUtil.collectElementsOfType(arendFile, ArendReferenceElement::class.java)
                .filter { ArendImportHintAction.importQuickFixAllowed(it) && ArendImportHintAction.referenceUnresolved(it) }
        }

        if (unresolvedRefs.isEmpty()) {
            return "No unresolved references found in module '$module'"
        }

        val fixed = mutableListOf<String>()
        val ambiguous = mutableListOf<String>()
        val notFound = mutableListOf<String>()

        for (ref in unresolvedRefs) {
            val refName = runReadAction { if (ref.isValid) ref.referenceName else null } ?: continue

            val candidates = runReadAction {
                if (!ref.isValid) return@runReadAction emptyList()
                ArendImportHintAction.getStubElementSet(project!!, ref, arendFile)
                    .mapNotNull { ResolveReferenceAction.getProposedFix(it, ref) }
            }

            when {
                candidates.isEmpty() -> notFound.add(refName)
                candidates.size > 1 -> ambiguous.add("$refName (${candidates.size} candidates: ${candidates.joinToString()})")
                else -> {
                    val action = candidates.first()
                    WriteCommandAction.runWriteCommandAction(project!!, "Fix Import: $refName", null, {
                        if (ref.isValid) action.execute(null)
                    }, arendFile)
                    fixed.add("$refName -> $action")
                }
            }
        }

        val result = StringBuilder()
        result.appendLine("Fix imports result for module '$module':")
        result.appendLine()
        if (fixed.isNotEmpty()) {
            result.appendLine("Fixed (${fixed.size}):")
            fixed.forEach { result.appendLine("  + $it") }
            result.appendLine()
        }
        if (ambiguous.isNotEmpty()) {
            result.appendLine("Ambiguous - multiple candidates (${ambiguous.size}):")
            ambiguous.forEach { result.appendLine("  ? $it") }
            result.appendLine()
        }
        if (notFound.isNotEmpty()) {
            result.appendLine("Not found - no import candidates (${notFound.size}):")
            notFound.forEach { result.appendLine("  - $it") }
        }
        return result.toString()
    }
}
