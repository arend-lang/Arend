package org.arend.starters

import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.invokeLater
import org.arend.documentation.generateHtmlForArendLib
import org.arend.graph.generateArendLibClassGraph
import kotlin.system.exitProcess

class GenerateArendLibStarter : ModernApplicationStarter() {
    override val commandName: String
        get() = "generateArendLib"

    override suspend fun start(args: List<String>) {
        val (pathToArendLibInArendSite, versionArendLib, updateColorScheme, projectDir, psiProject, classes) = parseArgsForGeneratingArendLib(args, false)

        invokeLater {
          val version =
            generateHtmlForArendLib(psiProject, pathToArendLibInArendSite, versionArendLib, updateColorScheme, projectDir, false)
          version?.let { generateArendLibClassGraph(psiProject, pathToArendLibInArendSite, classes, it, false) }
            ?: exitProcess(0)
        }
    }
}
