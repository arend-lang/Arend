package org.arend.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.fullNameText
import org.arend.server.ArendServerService
import java.lang.StringBuilder

fun StringBuilder.addImports(project: Project, referables: Set<PsiLocatedReferable>): StringBuilder {
    val filesToDefinitions = mutableMapOf<ArendFile, MutableList<String>>()
    val definitionsToFiles = mutableSetOf<String>()
    for (referable in referables) {
        val file = referable.containingFile as ArendFile
        if (file == project.service<ArendServerService>().prelude) {
            continue
        }
        filesToDefinitions.getOrPut(file) { mutableListOf() }.add(referable.fullNameText)
        definitionsToFiles.add(referable.fullNameText)
    }

    val fullFiles = mutableMapOf<ArendFile, Boolean>()
    filesLoop@ for ((file, fileDefinitions) in filesToDefinitions) {
        for (statement in file.statements) {
            val fullName = statement.group?.fullNameText ?: continue
            if (!fileDefinitions.contains(fullName) && definitionsToFiles.contains(fullName)) {
                fullFiles[file] = false
                continue@filesLoop
            }
            fullFiles[file] = true
        }
    }

    for ((file, importedDefinitions) in filesToDefinitions) {
        if (fullFiles[file] == true) {
            append("\\import ${file.fullName}\n")
        } else {
            append("\\import ${file.fullName}(${importedDefinitions.joinToString(",")})\n")
        }
    }
    if (filesToDefinitions.isNotEmpty()) {
        append("\n")
    }
    return this
}
