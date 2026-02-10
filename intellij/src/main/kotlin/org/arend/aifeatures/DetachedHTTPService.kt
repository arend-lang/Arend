package org.arend.aifeatures

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.launch
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModuleLocation.LocationKind
import org.arend.ext.module.ModulePath
import org.arend.search.proof.ProofSearchEntry
import org.arend.search.proof.generateProofSearchResults
import org.arend.server.ArendServerService
import org.arend.typechecking.runner.RunnerService
import org.jetbrains.ide.RestService
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class DetachedHTTPService : RestService() {
  private val delimiter = "%%"
  private val doneMarker = "TYPECHECK_DONE"
  private val proofSearchDoneMarker = "PROOF_SEARCH_DONE"

  companion object {
    private const val SERVICE_NAME = "detachedService"
    private const val TYPECHECK_ACTION = "typecheck"
    private const val PROOF_SEARCH_ACTION = "proofSearch"
  }

  override fun getServiceName(): String = SERVICE_NAME

  override fun isSupported(request: FullHttpRequest): Boolean {
    return isMethodSupported(request.method()) && request.uri().startsWith("/api/$SERVICE_NAME")
  }

  override fun isMethodSupported(method: HttpMethod): Boolean {
    return method == HttpMethod.GET || method == HttpMethod.POST
  }

  override fun execute(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext
  ): String? {
    val actionType = urlDecoder.parameters()["type"]?.firstOrNull() ?: ""
    val actionPayload = urlDecoder.parameters()["action"]?.firstOrNull() ?: ""

    val project = getLastFocusedOrOpenedProject()
    if (project == null) {
      return "IntelliJ is not running with an Arend project open"
    }
    
    when (actionType) {
      TYPECHECK_ACTION -> executeTypecheckAction(project, actionPayload)
      PROOF_SEARCH_ACTION -> executeProofSearchAction(project, actionPayload)
    }
    sendOk(request, context)
    return null
  }

  // ==================== Typecheck functionality ====================

  private fun executeTypecheckAction(project: Project, encodedPayload: String) {
    val parsedUserRequest = parseTypecheckData(encodedPayload)
    val modules: List<ModuleLocation> = parsedUserRequest.modulePaths.map {
      ModuleLocation(parsedUserRequest.libraryName, LocationKind.SOURCE, ModulePath.fromString(it.split("/").last()))
    }
    executeTypecheckModules(project, modules)
  }

  private fun executeTypecheckModules(project: Project, modules: List<ModuleLocation>) {
    File(project.basePath!! + "/.junieCommunication/errorFile.txt").writeText("")
    for (module in modules) {
      project.service<ArendServerService>().server.removeModule(module)
    }
    project.service<RunnerService>().coroutineScope.launch {
      for (module in modules) {
        project.service<RunnerService>().runCheckerWithFile(module).join()
        File(project.basePath!! + "/.junieCommunication/errorFile.txt").appendText("\n$doneMarker")
      }
    }
  }

  private fun parseTypecheckData(encodedPayload: String): TypecheckRequestData {
    if (encodedPayload.isBlank()) return TypecheckRequestData(emptyList(), "")
    val parts = encodedPayload.split(delimiter)
    val extraData = parts.last()
    val items = parts.dropLast(1)
    return TypecheckRequestData(items, extraData)
  }

  // ==================== Proof Search functionality ====================

  private fun executeProofSearchAction(project: Project, query: String) {
    val resultsOfProofSearch = executeProofSearch(project, query)
    val outputFile = File(project.basePath!! + "/.junieCommunication/proofSearchResults.txt")
    outputFile.writeText(resultsOfProofSearch)
    outputFile.appendText("\n$proofSearchDoneMarker")
  }

  private fun executeProofSearch(project: Project, query: String): String {
    val results: Sequence<ProofSearchEntry?> = generateProofSearchResults(project, query)
    return runReadAction {
      results.filterNotNull().joinToString("\n") { entry ->
        "${entry.def.refName} at ${entry.def.containingFile.virtualFile?.path}"
      }
    }
  }

  // ==================== Data classes ====================

  data class TypecheckRequestData(
    val modulePaths: List<String>,
    val libraryName: String
  )
}
