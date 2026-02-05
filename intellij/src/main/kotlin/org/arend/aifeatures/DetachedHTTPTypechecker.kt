package org.arend.aifeatures

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
import org.arend.server.ArendServerService
import org.arend.typechecking.runner.RunnerService
import org.jetbrains.ide.RestService
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class DetachedTypecheckerService() : RestService() {
  val delimiter = "%%"
  private val doneMarker = "TYPECHECK_DONE"

  override fun getServiceName(): String {
    return "detachedTypechecker"
  }

  override fun isSupported(request: FullHttpRequest): Boolean {
    return isMethodSupported(request.method()) && request.uri().startsWith("/api/${getServiceName()}")
  }

  fun executeTypecheckModules(project : Project, modules : List<ModuleLocation>){
    File(project.basePath!! + "/.junieCommunication/errorFile.txt").writeText("")
    for (module in modules){
      println("removing module $module")
      project.service<ArendServerService>().server.removeModule(module)
    }
    project.service<RunnerService>().coroutineScope.launch {
      for (module in modules) {
        project.service<RunnerService>().runCheckerWithFile(module).join()
        File(project.basePath!! + "/.junieCommunication/errorFile.txt").appendText("\n$doneMarker")
      }
    }
  }

  override fun execute(
    urlDecoder: QueryStringDecoder,
    request: FullHttpRequest,
    context: ChannelHandlerContext
  ): String? {
    val encodedPayload = urlDecoder.parameters()["action"]?.firstOrNull() ?: ""
    val parsedUserRequest : DecodedRequestData = parseServerData(encodedPayload)
    val modules : List<ModuleLocation> = parsedUserRequest.modulePaths.map{
      ModuleLocation(parsedUserRequest.libraryName, LocationKind.SOURCE, ModulePath.fromString(it.split("/").last()))
    }

    println("modules $modules , ${parsedUserRequest.libraryName}")
    val project = getLastFocusedOrOpenedProject()
    project?.let{
      executeTypecheckModules(project, modules)

//      ApplicationManager.getApplication().invokeLater {
//        FileTypecheckAction(it).typeCheckModules(modules)
//        val errorFilePath = ensureCommunicationFile(project.basePath)
//        if (errorFilePath != null) {
//          try {
//            Files.write(
//              errorFilePath,
//              (doneMarker + "\n").toByteArray(StandardCharsets.UTF_8),
//              StandardOpenOption.CREATE,
//              StandardOpenOption.APPEND
//            )
//          } catch (_: Exception) {
//          }
//        }
//      }
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