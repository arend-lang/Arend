package org.arend.mcp.tools

import org.arend.ext.module.ModuleLocation
import org.arend.frontend.library.FileSourceLibrary
import org.arend.server.ArendServer
import org.arend.util.FileUtils
import java.nio.file.Files

fun outputFile(server: ArendServer, moduleLocation: ModuleLocation): String {
  val library = server.getLibrary(moduleLocation.libraryName)
    ?: return "Error: Library '${moduleLocation.libraryName}' not found"
  val fileSourceLibrary = library as? FileSourceLibrary
    ?: return "Error: Library '${moduleLocation.libraryName}' is not a file-based library"
  val basePath = fileSourceLibrary.getSourceBasePath()
    ?: return "Error: Library '${moduleLocation.libraryName}' has no source base path"
  val filePath = FileUtils.sourceFile(basePath, moduleLocation.modulePath)
  if (!Files.isRegularFile(filePath)) {
    return "Error: File for module '${moduleLocation.modulePath}' not found in library '${moduleLocation.libraryName}'"
  }
  return Files.readString(filePath)
}
