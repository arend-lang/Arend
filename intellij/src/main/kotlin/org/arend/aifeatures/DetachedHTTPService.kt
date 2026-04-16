package org.arend.aifeatures

import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.QueryStringDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.ide.RestService
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.*
import java.nio.charset.StandardCharsets



class DetachedHTTPService : RestService() {
  companion object {
    private const val SERVICE_NAME = "detachedService"
  }

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
    if (input == null) {
      sendContent(request, context, "Error: input cannot be parsed", "text/plain")
      return null
    }

    val project = ProjectManager.getInstance().openProjects.firstOrNull { it.basePath == input.libPath
      || it.basePath?.let { base -> input.libPath.startsWith(base) } == true }
    if (project == null) {
      sendContent(request, context, "Error: No project open", "text/plain")
      return null
    }

    scope.launch {
      try {
        val registry = project.service<McpToolRegistryService>()
        val resultString = registry.execute(actionType ?: "", input.arguments)
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

    response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType)
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes())

    val keepAlive = HttpUtil.isKeepAlive(request)
    if (keepAlive) {
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
      context.writeAndFlush(response)
    } else {
      context.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
    }
  }

  data class MCPInput(val libPath: String, val arguments: String)

  fun initialParseInput(actionPayload: String): MCPInput? {
    if (actionPayload.isBlank()) return null
    return try {
      val json = Json.parseToJsonElement(actionPayload).jsonObject
      val libPath = json["libraryPath"]?.jsonPrimitive?.content ?: return null
      MCPInput(libPath, actionPayload)
    } catch (e: Exception) {
      null
    }
  }
}
