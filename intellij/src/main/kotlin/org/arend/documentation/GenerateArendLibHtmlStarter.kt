package org.arend.documentation

import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.invokeLater
import kotlin.system.exitProcess

class GenerateArendLibHtmlStarter : ModernApplicationStarter() {
    override val commandName: String
        get() = "generateArendLibHtml"

    override suspend fun start(args: List<String>) {
        val arguments = args.map { it.ifEmpty { null } }

        val pathToArendLib = arguments.getOrNull(1) ?: run {
            println("The path to the Arend library is not specified")
            return
        }
        val pathToArendLibInArendSite = arguments.getOrNull(2) ?: run {
            println("The path to the Arend library in Arend site is not specified")
            return
        }
        val thirdArgument = arguments.getOrNull(3)
        val versionArendLib = if (thirdArgument == "null") null else thirdArgument
        val updateColorScheme = arguments.getOrNull(4).toBoolean()
        val projectDir = arguments.getOrNull(5)

        invokeLater {
            generateHtmlForArendLib(pathToArendLib, pathToArendLibInArendSite, versionArendLib, updateColorScheme, projectDir)
            exitProcess(0)
        }
    }
}