package org.arend.starters

import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.invokeLater
import org.arend.graph.generateArendLibClassGraph

class GenerateGraphStarter : ModernApplicationStarter() {
  override val commandName: String
    get() = "generateArendLibGraph"

  override suspend fun start(args: List<String>) {
    val (pathToArendLibInArendSite, versionArendLib, _, _, psiProject, classes) = parseArgsForGeneratingArendLib(args, true)

    val configService = getConfigService(psiProject)
    val version = versionArendLib ?: ("v" + configService.version?.longString)

    invokeLater {
      generateArendLibClassGraph(psiProject, pathToArendLibInArendSite, classes, version, true)
    }
  }
}
