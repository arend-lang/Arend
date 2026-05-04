package org.arend.mcp.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.frontend.library.FileSourceLibrary
import org.arend.mcp.McpTool
import org.arend.server.ArendServer
import java.nio.file.Files

open class ShowAiGuideTool(private val server: ArendServer? = null) : McpTool {
  override val name: String = "ShowAiGuide"
  override val description: String = "Shows the AI guide (markdown summary) for a given module. " +
      "The .aiGuide directory contains concise markdown descriptions of Arend modules. " +
      "You must provide the library path and the module path (e.g. 'Algebra.Field'). " +
      "You can also access README.md for the whole library (empty modulePath) or for top-level directories (e.g. 'Algebra'). " +
      "Returns the content of the corresponding .md file from the .aiGuide directory."

  @Serializable
  private data class ShowAiGuideInput(val libraryPath: String = "", val modulePath: String = "")

  override fun getInputSchema(): JsonObject = buildJsonObject {
    putJsonObject("libraryPath") {
      put("type", "string")
    }
    putJsonObject("modulePath") {
      put("type", "string")
    }
  }

  override fun execute(arguments: String): String {
    val input = try {
      Json.decodeFromString<ShowAiGuideInput>(arguments)
    } catch (e: Exception) {
      return "Error: Failed to parse arguments: ${e.message}"
    }

    val module = input.modulePath
    val libPath = input.libraryPath

    if (libPath.isBlank()) {
      return "Error: No library path provided"
    }

    val libraryName = java.io.File(libPath).name
    var fileContent: String
    if (module.isBlank()) {
      fileContent = readAiGuideReadme(libraryName, emptyList())
    } else {
      val path = ModulePath.fromString(module.split("/").last())
      val sourceLocation = ModuleLocation(libraryName, ModuleLocation.LocationKind.SOURCE, path)
      fileContent = readAiGuide(sourceLocation)
      if (fileContent.startsWith("Error:")) {
        // Try as a directory README
        val parts = module.split(".")
        val readmeContent = readAiGuideReadme(libraryName, parts)
        if (!readmeContent.startsWith("Error:")) {
          fileContent = readmeContent
        }
      }
    }

    if (fileContent.startsWith("Error:")) {
      val allLibraries = server?.getLibraries() ?: emptySet()
      for (lib in allLibraries) {
        if (lib == libraryName) continue
        if (module.isBlank()) {
          val depContent = readAiGuideReadme(lib, emptyList())
          if (!depContent.startsWith("Error:")) {
            fileContent = depContent
            break
          }
        } else {
          val path = ModulePath.fromString(module.split("/").last())
          val depLocation = ModuleLocation(lib, ModuleLocation.LocationKind.SOURCE, path)
          val depContent = readAiGuide(depLocation)
          if (!depContent.startsWith("Error:")) {
            fileContent = depContent
            break
          }
          val readmeContent = readAiGuideReadme(lib, module.split("."))
          if (!readmeContent.startsWith("Error:")) {
            fileContent = readmeContent
            break
          }
        }
      }
    }

    return fileContent
  }

  protected open fun readAiGuide(moduleLocation: ModuleLocation): String {
    return server?.let { readAiGuide(it, moduleLocation) } ?: "Server is not initialized"
  }

  protected open fun readAiGuideReadme(libraryName: String, pathParts: List<String>): String {
    return server?.let { readAiGuideReadme(it, libraryName, pathParts) } ?: "Server is not initialized"
  }
}

fun readAiGuide(server: ArendServer, moduleLocation: ModuleLocation): String {
  val library = server.getLibrary(moduleLocation.libraryName)
    ?: return "Error: Library '${moduleLocation.libraryName}' not found"
  val fileSourceLibrary = library as? FileSourceLibrary
    ?: return "Error: Library '${moduleLocation.libraryName}' is not a file-based library"
  val sourceBasePath = fileSourceLibrary.getSourceBasePath()
    ?: return "Error: Library '${moduleLocation.libraryName}' has no source base path"

  val libraryRoot = sourceBasePath.parent
    ?: return "Error: Cannot determine library root for '${moduleLocation.libraryName}'"
  val aiGuidePath = libraryRoot.resolve(".aiGuide")

  val moduleParts = moduleLocation.modulePath.toString().split(".")
  val mdFilePath = aiGuidePath.resolve(moduleParts.joinToString("/") + ".md")

  if (!Files.isRegularFile(mdFilePath)) {
    return "Error: AI guide not found for module '${moduleLocation.modulePath}' in library '${moduleLocation.libraryName}' (looked at $mdFilePath)"
  }

  return Files.readString(mdFilePath)
}

fun readAiGuideReadme(server: ArendServer, libraryName: String, pathParts: List<String>): String {
  val library = server.getLibrary(libraryName)
    ?: return "Error: Library '$libraryName' not found"
  val fileSourceLibrary = library as? FileSourceLibrary
    ?: return "Error: Library '$libraryName' is not a file-based library"
  val sourceBasePath = fileSourceLibrary.getSourceBasePath()
    ?: return "Error: Library '$libraryName' has no source base path"

  val libraryRoot = sourceBasePath.parent
    ?: return "Error: Cannot determine library root for '$libraryName'"
  val aiGuidePath = libraryRoot.resolve(".aiGuide")

  val readmePath = if (pathParts.isEmpty()) {
    aiGuidePath.resolve("README.md")
  } else {
    aiGuidePath.resolve(pathParts.joinToString("/")).resolve("README.md")
  }

  if (!Files.isRegularFile(readmePath)) {
    return "Error: README.md not found at $readmePath"
  }

  return Files.readString(readmePath)
}
