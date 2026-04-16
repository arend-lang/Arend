package org.arend.mcp.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.mcp.McpTool
import org.arend.server.ArendServer
import org.arend.server.ProgressReporter
import org.arend.typechecking.computation.UnstoppableCancellationIndicator

class TypecheckerTool(private val server: ArendServer? = null) : McpTool {
    override val name = "mcp_arend_Typecheck_module"
    override val description = "Typechecks what you wrote in Arend, returns error messages separated by comma. " +
        "You need to send it the full library path as a string and a list of paths of modules that you want to typecheck. " +
        "For example if in project myProject you want to typecheck module myFile.ard you send the json " +
        "{\"libraryName\":\"/Users/username/Dev/myProject\",\"modulePaths\":[\"myFile\"]}"

    override fun getInputSchema(): JsonObject = buildJsonObject {
        putJsonObject("libraryPath") {
            put("type", "string")
        }
        putJsonObject("modulePaths") {
            put("type", "array")
            putJsonObject("items") {
                put("type", "string")
            }
        }
    }

    @Serializable
    private data class TypecheckerInput(val libraryPath: String = "", val modulePaths: List<String> = emptyList())

    override fun execute(arguments: String): String {
        val input = try {
            Json.decodeFromString<TypecheckerInput>(arguments)
        } catch (e: Exception) {
            return "Error: Failed to parse arguments: ${e.message}"
        }
        val modules: List<ModuleLocation> = input.modulePaths.map {
            ModuleLocation(input.libraryPath, ModuleLocation.LocationKind.SOURCE, ModulePath.fromString(it.split("/").last()))
        }
        return server?.let { executeTypecheckModules(it, modules) } ?: "Server not initialized."
    }

    private fun executeTypecheckModules(server: ArendServer, modules: List<ModuleLocation>): String {
        for (module in modules) {
            server.removeModule(module)
        }

        val checker = server.getCheckerFor(modules)
        checker.resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())

        val errorsReturn = mutableListOf<org.arend.ext.error.GeneralError>()
        for (module in modules) {
            errorsReturn.addAll(server.getTypecheckingErrors(module))
        }

        val errorMap = server.getErrorMap()
        for ((errorModule, errors) in errorMap) {
            if (modules.any { it.modulePath == errorModule.modulePath }) {
                for (error in errors) {
                    if (!errorsReturn.contains(error)) {
                        errorsReturn.add(error)
                    }
                }
            }
        }

        return if (errorsReturn.isEmpty()) {
            "Typechecking completed successfully. No errors found."
        } else {
            errorsReturn.joinToString(separator = ",") { it.toString() }
        }
    }
}
