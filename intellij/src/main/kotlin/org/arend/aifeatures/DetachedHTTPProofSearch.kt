package org.arend.aifeatures

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import com.intellij.openapi.project.Project
import org.arend.search.proof.ProofSearchEntry
import org.arend.search.proof.generateProofSearchResults
import org.jetbrains.ide.RestService
import com.intellij.openapi.application.runReadAction
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class DetachedHTTPProofSearch() : RestService() {
  val delimiter = "%%"
  val generalBound = 20
  override fun getServiceName(): String  = "detachedProofSearch"

  override fun isSupported(request: FullHttpRequest): Boolean {
    return isMethodSupported(request.method()) && request.uri().startsWith("/api/${getServiceName()}")
  }

  fun executeProofSearch(project : Project, query: String) : String {
    val results: Sequence<ProofSearchEntry?> = generateProofSearchResults(project, query)
    return results.filterNotNull().joinToString("\n") { entry ->
      "${entry.def.refName} at ${entry.def.containingFile.virtualFile?.path}"
    }
  }

  override fun isMethodSupported(method: HttpMethod): Boolean {
    return method == HttpMethod.GET || method == HttpMethod.POST
  }

  fun parseServerData(encodedPayload: String): String = encodedPayload

  override fun execute(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext
  ): String? {
    val encodedPayload = urlDecoder.parameters()["action"]?.firstOrNull() ?: ""
    val parsedUserRequest : String = parseServerData(encodedPayload)

    val project = getLastFocusedOrOpenedProject()
    project?.let{
      runReadAction {
        val resultsOfProofSearch = executeProofSearch(project, parsedUserRequest)
        File(project.basePath!! + "/.junieCommunication/proofSearchResults.txt").writeText(resultsOfProofSearch)
      }
    }
    sendOk(request, context)
    return null
  }
}