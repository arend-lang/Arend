package org.arend.aifeatures.mcpTools.common

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import org.arend.ext.module.ModuleLocation
import org.arend.util.findLibrary

//TODO: drop in the file implementations of Props
fun outputFile(project: Project, moduleLocation: ModuleLocation): String {
    return runReadAction {
        val libraryConfig = project.findLibrary(moduleLocation.libraryName)
            ?: return@runReadAction "Error: Library '${moduleLocation.libraryName}' not found"

        val arendFile = libraryConfig.findArendFile(moduleLocation)
            ?: return@runReadAction "Error: File for module '${moduleLocation.modulePath}' not found in library '${moduleLocation.libraryName}'"

        arendFile.text
    }
}