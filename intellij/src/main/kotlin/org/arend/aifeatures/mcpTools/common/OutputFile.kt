package org.arend.aifeatures.mcpTools.common

import com.intellij.openapi.project.Project
import org.arend.ext.module.ModuleLocation
import org.arend.util.findLibrary

val numberOfAllowedLines = 500

//TODO: drop in the file implementations of Props
fun outputFile(project: Project, moduleLocation: ModuleLocation): String {
    val libraryConfig = project.findLibrary(moduleLocation.libraryName)
        ?: return "Error: Library '${moduleLocation.libraryName}' not found"
    
    val arendFile = libraryConfig.findArendFile(moduleLocation)
        ?: return "Error: File for module '${moduleLocation.modulePath}' not found in library '${moduleLocation.libraryName}'"
    
    return arendFile.text
}