package org.arend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.*
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModuleLocation.LocationKind
import org.arend.ext.module.ModulePath
import org.arend.typechecking.runner.RunnerService
import org.jetbrains.ide.RestService
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.atomic.AtomicInteger

class DetachedTypecheckerService() : RestService() {
  val delimiter = "%%"
  private val doneMarker = "TYPECHECK_DONE"

  override fun getServiceName(): String {
    return "detachedTypechecker"
  }

  override fun isSupported(request: FullHttpRequest): Boolean {
    return isMethodSupported(request.method()) && request.uri().startsWith("/api/${getServiceName()}")
  }

  override fun execute(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext
  ): String? {
    System.err.println(request.uri())
    val encodedPayload = urlDecoder.parameters()["action"]?.firstOrNull() ?: ""
    val parsedUserRequest : DecodedRequestData = parseServerData(encodedPayload)
    val modules : List<ModuleLocation> = parsedUserRequest.modulePaths.map{
      ModuleLocation(parsedUserRequest.libraryName, LocationKind.SOURCE, ModulePath.fromString(it.split("/").last()))
    }
    println("modules $modules , ${parsedUserRequest.libraryName}")
    val project = getLastFocusedOrOpenedProject()
    val errorFilePath = ensureCommunicationFile(project?.basePath)

    ApplicationManager.getApplication().invokeLater {
      if (project == null) return@invokeLater
      val runnerService = project.service<RunnerService>()
      val remaining = if (errorFilePath != null) AtomicInteger(modules.size) else null

      if (modules.isEmpty() && errorFilePath != null) {
        try {
          Files.write(
            errorFilePath,
            (doneMarker + "\n").toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
          )
        } catch (_: Exception) {
        }
      }

      for (module in modules){
        val job = runnerService.runCheckerWithFile(module)
        if (errorFilePath != null) {
          job.invokeOnCompletion {
            val shouldSignal = remaining?.decrementAndGet() == 0
            if (shouldSignal) {
              try {
                Files.write(
                  errorFilePath,
                  (doneMarker + "\n").toByteArray(StandardCharsets.UTF_8),
                  StandardOpenOption.CREATE,
                  StandardOpenOption.APPEND
                )
              } catch (_: Exception) {
              }
            }
          }
        }
      }
    }

    sendOk(request, context)
    return null
  }

  private fun ensureCommunicationFile(basePath: String?): Path? {
    if (basePath == null) return null
    val dirPath = Path.of(basePath, ".junieCommunication")
    val filePath = dirPath.resolve("errorFile.txt")
    try {
      Files.createDirectories(dirPath)
      if (Files.notExists(filePath)) {
        Files.createFile(filePath)
      }
    } catch (_: Exception) {
    }
    return filePath
  }

  fun parseServerData(encodedPayload: String): DecodedRequestData {
    if (encodedPayload.isBlank()) return DecodedRequestData(emptyList(), "")
    val parts = encodedPayload.split(delimiter)
    val extraData = parts.last()
    val items = parts.dropLast(1)
    return DecodedRequestData(items, extraData)
  }

  override fun isMethodSupported(method: HttpMethod): Boolean {
    return method == HttpMethod.GET || method == HttpMethod.POST
  }

  data class DecodedRequestData(
    val modulePaths: List<String>,
    val libraryName: String
  )

}