package org.arend.aifeatures.mcpTools.common

import com.intellij.openapi.project.Project
import org.arend.ext.module.ModuleLocation

val numberOfAllowedLines = 500

//TODO: drop in the file implementations of Props
fun outputFile( project : Project,  moduleLocation: ModuleLocation) = moduleLocation.toString()