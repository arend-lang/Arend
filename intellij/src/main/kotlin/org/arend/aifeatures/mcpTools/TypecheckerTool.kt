package org.arend.aifeatures.mcpTools

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.arend.aifeatures.McpTool
import org.arend.aifeatures.TypecheckRequestData.*
import org.arend.ext.error.GeneralError
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModuleLocation.LocationKind
import org.arend.ext.module.ModulePath
import org.arend.server.ArendServerService
import org.arend.typechecking.runner.RunnerService


class TypecheckerTool : McpTool {
  override val name = "mcp_arend_Typecheck_definition"
  override val description = "Typechecks what you wrote in Arend, returns error messages separated by comma." +
  "You need to send it the full library path as a string and a list of paths of modules that you want to typecheck." +
  "For example if in project myProject you want to typecheck module myFile.ard you send the json {\"libraryName\":\"/Users/username/Dev/myProject\",\"modulePaths\":[\"myFile\"]}"



  override fun getInputSchema(): JsonObject =
  buildJsonObject {
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


  override fun execute(project : Project, arguments: String): String {
    val parsedUserRequest = parseTypecheckData(arguments)
    val modules: List<ModuleLocation> = parsedUserRequest.modulePaths.map {
      ModuleLocation(parsedUserRequest.libraryName, LocationKind.SOURCE, ModulePath.fromString(it.split("/").last()))
    }
    return executeTypecheckModules(project, modules)
  }

  private fun executeTypecheckModules(project: Project, modules: List<ModuleLocation>): String {
    val runnerService = project.service<RunnerService>()
    val server = project.service<ArendServerService>().server

    val previousErrorCount = runnerService.myFileListErrorReporter.getErrorList().size
    runnerService.myFileListErrorReporter.getErrorList().clear()

    // Remove modules from server cache to force re-typechecking
    for (module in modules) {
      server.removeModule(module)
    }

    // Run typechecking synchronously using runBlocking
    runBlocking {
      for (module in modules) {
        runnerService.runCheckerForList(module)
      }
    }

    // Retrieve errors from the server's ErrorService - this is where errors are actually stored
    val errorsReturn = mutableListOf<GeneralError>()
    for (module in modules) {
      val typecheckingErrors = server.getTypecheckingErrors(module)
      errorsReturn.addAll(typecheckingErrors)
    }

    // Also check the error map for all errors
    val errorMap = server.errorMap
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
      val result = errorsReturn.joinToString(separator = ",") { it.toString() }
      result
    }
  }




}