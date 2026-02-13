package org.arend.aifeatures

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.ide.RestService
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.*
import java.nio.charset.StandardCharsets



class DetachedHTTPService : RestService() {
  companion object {
    private const val SERVICE_NAME = "detachedService"
  }
  val registry = ApplicationManager.getApplication().getService(McpToolRegistryService::class.java)

  override fun getServiceName(): String = SERVICE_NAME

  private val scope = CoroutineScope(Dispatchers.Default)

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
    val actionType = urlDecoder.parameters()["type"]?.firstOrNull()
    val actionPayload = urlDecoder.parameters()["action"]?.firstOrNull() ?: ""
    val input = initialParseInput(actionPayload)
    System.err.println("execute MCP called with arguments: actionType: $actionType, actionPayload: $actionPayload, input: $input")
    if (input == null){
      sendContent(request, context, "Error: input cannot be parsed", "text/plain")
      return null
    }

    val project = ProjectManager.getInstance().openProjects.firstOrNull { it.basePath == input.libPath }
    if (project == null) {
      sendContent(request, context, "Error: No project open", "text/plain")
      return null
    }
    scope.launch {
      try {
        println("calling execute with arguments: actionType: $actionType, actionPayload: $actionPayload, project: $project")
        val resultString = registry.execute(actionType ?: "", actionPayload, project)

        // --- SEND CONTENT BACK ON SAME PORT ---
        // We manually send the content back to the waiting client
        sendContent(request, context, resultString, "text/plain")

      } catch (e: Exception) {
        val errorMessage = "Error: ${e.message}"
        sendContent(request, context, errorMessage, "text/plain")
      }
    }
    return null
  }

  private fun sendContent(
    request: FullHttpRequest,
    context: ChannelHandlerContext,
    content: String,
    contentType: String = "application/json"
  ) {
    val responseBytes = content.toByteArray(StandardCharsets.UTF_8)

    val response = DefaultFullHttpResponse(
      HttpVersion.HTTP_1_1,
      HttpResponseStatus.OK,
      Unpooled.wrappedBuffer(responseBytes)
    )

    // 3. Set standard headers
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())

    // 4. Handle Keep-Alive (optional but good practice)
    // If the client requested keep-alive, we shouldn't close the connection immediately.
    // However, for simple tool executions, closing is often safer to ensure the client stops waiting.
    val keepAlive = HttpUtil.isKeepAlive(request)
    if (keepAlive) {
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
      context.writeAndFlush(response)
    } else {
      // 5. Write and Close
      context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }
  }
  fun initialParseInput(actionPayload : String) : MCPInput?{
    if (actionPayload.isBlank()) return null
    val libPath = actionPayload.split(registry.getDelimiter()).last()
    val unparsedArguments = actionPayload.replace(libPath, "").substringBeforeLast(registry.getDelimiter())
    return MCPInput(libPath, unparsedArguments)
  }

  data class MCPInput(val libPath : String, val unparsedArguments : String)

}