package org.arend.util

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.module.ModuleLocation
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.ScopeFactory
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.fullNameText
import org.arend.server.ArendServerService
import org.arend.term.group.ConcreteGroup
import java.lang.StringBuilder

fun StringBuilder.addImports(project: Project, referables: Set<PsiLocatedReferable>): StringBuilder {
    val filesToDefinitions = mutableMapOf<ArendFile, MutableList<String>>()
    val definitionsToFiles = mutableSetOf<String>()
    for (referable in referables) {
        val file = referable.containingFile
        if (file !is ArendFile || project.service<ArendServerService>().isPrelude(file)) {
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

fun getFileGroup(project: Project, moduleLocation: ModuleLocation): ConcreteGroup? {
    val server = project.service<ArendServerService>().server
    return server.getRawGroup(moduleLocation)
}

fun getFileScope(project: Project, moduleLocation: ModuleLocation): Scope {
    val server = project.service<ArendServerService>().server
    return CachingScope.make(ScopeFactory.forGroup(server.getRawGroup(moduleLocation), server.getModuleScopeProvider(moduleLocation.libraryName, true)))
}
