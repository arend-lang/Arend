package org.arend.starters

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import org.arend.documentation.LOG
import org.arend.module.config.ArendModuleConfigService
import org.arend.util.allModules
import org.arend.util.register
import kotlin.system.exitProcess

data class ArgsForGeneratingArendLib(val pathToArendLibInArendSite: String, val versionArendLib: String?, val updateColorScheme: Boolean, val projectDir: String?, val psiProject: Project, val classes: List<String>)

fun parseArgsForGeneratingArendLib(args: List<String>, isGraph: Boolean): ArgsForGeneratingArendLib {
  val arguments = args.map { it.ifEmpty { null } }
  val pathToArendLib = arguments.getOrNull(1) ?: run {
    println("The path to the Arend library is not specified")
    exitProcess(0)
  }
  val pathToArendLibInArendSite = arguments.getOrNull(2) ?: run {
    println("The path to the Arend library in Arend site is not specified")
    exitProcess(0)
  }
  val thirdArgument = arguments.getOrNull(3)
  val versionArendLib = if (thirdArgument == "null") null else thirdArgument
  val (updateColorScheme, projectDir, classes) = if (isGraph) {
    Triple(false, arguments.getOrNull(4), arguments.getOrNull(5)?.split(",") ?: emptyList())
  } else {
    Triple(arguments.getOrNull(4).toBoolean(), arguments.getOrNull(5), arguments.getOrNull(6)?.split(",") ?: emptyList())
  }

  val projectManager = ProjectManager.getInstance()
  val psiProject = projectManager.loadAndOpenProject(pathToArendLib) ?: run {
    LOG.warn("Can't open arend-lib on this path=$pathToArendLib")
    exitProcess(0)
  }
  return ArgsForGeneratingArendLib(pathToArendLibInArendSite, versionArendLib, updateColorScheme, projectDir, psiProject, classes)
}

fun getConfigService(psiProject: Project): ArendModuleConfigService {
  val module = psiProject.allModules.find { it.name == psiProject.name } ?: run {
    LOG.warn("Can't find the arend-lib module")
    exitProcess(0)
  }
  module.register()

  val configService = ArendModuleConfigService.getInstance(module) ?: run {
    LOG.warn("Can't load information from the YAML file about arend-lib. You need to initialize arend-lib as an Arend module before starting html file generation")
    exitProcess(0)
  }
  return configService
}
