package org.arend.documentation

import com.intellij.openapi.application.ModernApplicationStarter
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.ProjectManager
import org.arend.graph.generateArendLibClassGraph

class GenerateArendLibStarter : ModernApplicationStarter() {
    override val commandName: String
        get() = "generateArendLib"

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

        val projectManager = ProjectManager.getInstance()
        val psiProject = projectManager.loadAndOpenProject(pathToArendLib) ?: run {
            LOG.warn("Can't open arend-lib on this path=$pathToArendLib")
            return
        }
        val classes = arguments.getOrNull(6)?.split(",") ?: emptyList()

        invokeLater {
            val version = generateHtmlForArendLib(psiProject, pathToArendLibInArendSite, versionArendLib, updateColorScheme, projectDir)
            version?.let { generateArendLibClassGraph(psiProject, pathToArendLibInArendSite, classes, it) }
        }
    }
}