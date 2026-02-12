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


class TypecheckerTool() : McpTool {
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
//    println("[DEBUG_LOG] TypecheckerTool.execute called with arguments: $arguments")
    val parsedUserRequest = parseTypecheckData(arguments)
//    println("[DEBUG_LOG] Parsed request: libraryName=${parsedUserRequest.libraryName}, modulePaths=${parsedUserRequest.modulePaths}")
    val modules: List<ModuleLocation> = parsedUserRequest.modulePaths.map {
      ModuleLocation(parsedUserRequest.libraryName, LocationKind.SOURCE, ModulePath.fromString(it.split("/").last()))
    }
//    println("[DEBUG_LOG] Created modules: $modules")
//    println("[DEBUG_LOG] Number of modules to typecheck: ${modules.size}")
    return executeTypecheckModules(project, modules)
  }

  private fun executeTypecheckModules(project: Project, modules: List<ModuleLocation>): String {
    println("[DEBUG_LOG] executeTypecheckModules started with ${modules.size} modules")
    val runnerService = project.service<RunnerService>()
    val server = project.service<ArendServerService>().server

    // Clear previous errors
    println("[DEBUG_LOG] Clearing previous errors from myFileListErrorReporter")
    val previousErrorCount = runnerService.myFileListErrorReporter.getErrorList().size
    println("[DEBUG_LOG] Previous error count before clear: $previousErrorCount")
    runnerService.myFileListErrorReporter.getErrorList().clear()

    // Remove modules from server cache to force re-typechecking
    println("[DEBUG_LOG] Removing modules from server cache to force re-typechecking")
    for (module in modules) {
      println("[DEBUG_LOG] Removing module from cache: $module")
      server.removeModule(module)
    }

    // Run typechecking synchronously using runBlocking
    println("[DEBUG_LOG] Starting runBlocking for typechecking")
    runBlocking {
      for (module in modules) {
        println("[DEBUG_LOG] Calling runCheckerForList for module: $module")
        runnerService.runCheckerForList(module)
        println("[DEBUG_LOG] runCheckerForList completed for module: $module")
      }
    }
    println("[DEBUG_LOG] runBlocking completed")

    // Retrieve errors from the server's ErrorService - this is where errors are actually stored
    val errorsReturn = mutableListOf<GeneralError>()
    for (module in modules) {
      val typecheckingErrors = server.getTypecheckingErrors(module)
      println("[DEBUG_LOG] Server.getTypecheckingErrors for $module returned ${typecheckingErrors.size} errors")
      for ((index, error) in typecheckingErrors.withIndex()) {
        println("[DEBUG_LOG] Typechecking error $index: $error")
      }
      errorsReturn.addAll(typecheckingErrors)
    }

    // Also check the error map for all errors
    val errorMap = server.errorMap
    println("[DEBUG_LOG] Server.errorMap has ${errorMap.size} modules with errors")
    for ((errorModule, errors) in errorMap) {
      println("[DEBUG_LOG] ErrorMap module $errorModule has ${errors.size} errors")
      if (modules.any { it.modulePath == errorModule.modulePath }) {
        for (error in errors) {
          if (!errorsReturn.contains(error)) {
            println("[DEBUG_LOG] Adding error from errorMap: $error")
            errorsReturn.add(error)
          }
        }
      }
    }

    println("[DEBUG_LOG] Total errors collected: ${errorsReturn.size}")

    return if (errorsReturn.isEmpty()) {
      println("[DEBUG_LOG] Returning success message (no errors found)")
      "Typechecking completed successfully. No errors found."
    } else {
      val result = errorsReturn.joinToString(separator = ",") { it.toString() }
      println("[DEBUG_LOG] Returning error message: $result")
      result
    }
  }




}