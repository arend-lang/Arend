package org.arend.starters

import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.invokeLater
import org.arend.documentation.generateHtmlForArendLib
import kotlin.system.exitProcess

class GenerateArendLibHtmlStarter : ModernApplicationStarter() {
  override val commandName: String
    get() = "generateArendLibHtml"

  override suspend fun start(args: List<String>) {
    val (pathToArendLibInArendSite, versionArendLib, updateColorScheme, projectDir, psiProject) = parseArgsForGeneratingArendLib(args, false)

    invokeLater {
      generateHtmlForArendLib(psiProject, pathToArendLibInArendSite, versionArendLib, updateColorScheme, projectDir, true)
      exitProcess(0)
    }
  }
}
