package org.arend.aifeatures.mcpTools

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.module.ModuleLocation
import org.arend.mcp.tools.ShowAiGuideTool
import org.arend.server.ArendServerService
import org.arend.util.findLibrary

class ShowAiGuideTool(private val project: Project) : ShowAiGuideTool(project.service<ArendServerService>().server) {
  override fun readAiGuide(moduleLocation: ModuleLocation): String {
    return runReadAction {
      val libraryConfig = project.findLibrary(moduleLocation.libraryName)
        ?: return@runReadAction "Error: Library '${moduleLocation.libraryName}' not found"

      val root = libraryConfig.root
        ?: return@runReadAction "Error: Library root not found for '${moduleLocation.libraryName}'"

      val aiGuideDir = root.findChild(".aiGuide")
        ?: return@runReadAction "Error: .aiGuide directory not found in library '${moduleLocation.libraryName}'"

      val moduleParts = moduleLocation.modulePath.toString().split(".")
      val mdRelativePath = moduleParts.joinToString("/") + ".md"
      val mdFile = aiGuideDir.findFileByRelativePath(mdRelativePath)
        ?: return@runReadAction "Error: AI guide not found for module '${moduleLocation.modulePath}' in library '${moduleLocation.libraryName}'"

      String(mdFile.contentsToByteArray())
    }
  }

  override fun readAiGuideReadme(libraryName: String, pathParts: List<String>): String {
    return runReadAction {
      val libraryConfig = project.findLibrary(libraryName)
        ?: return@runReadAction "Error: Library '$libraryName' not found"

      val root = libraryConfig.root
        ?: return@runReadAction "Error: Library root not found for '$libraryName'"

      val aiGuideDir = root.findChild(".aiGuide")
        ?: return@runReadAction "Error: .aiGuide directory not found in library '$libraryName'"

      var dir = aiGuideDir
      for (part in pathParts) {
        dir = dir.findChild(part)
          ?: return@runReadAction "Error: Directory '$part' not found in .aiGuide"
      }

      val readmeFile = dir.findChild("README.md")
        ?: return@runReadAction "Error: README.md not found in ${if (pathParts.isEmpty()) ".aiGuide" else pathParts.joinToString("/")}"

      String(readmeFile.contentsToByteArray())
    }
  }
}
