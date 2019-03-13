package org.arend.module.config

import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import org.arend.library.LibraryDependency
import org.arend.module.ModulePath
import org.arend.psi.libraryConfig
import org.arend.util.FileUtils
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence


const val SOURCES = "sourcesDir"
const val BINARIES = "binariesDir"
const val MODULES = "modules"
const val DEPENDENCIES = "dependencies"

val KEYS = setOf(SOURCES, BINARIES, MODULES, DEPENDENCIES)

private fun YAMLFile.getProp(name: String) = (documents?.firstOrNull()?.topLevelValue as? YAMLMapping)?.getKeyValueByKey(name)?.value

private fun yamlSeqFromList(lst: List<String>): String =  "[" + lst.reduce { acc, x -> "$acc, $x" } + "]"

private fun createFromText(code: String, project: Project): YAMLFile? =
    PsiFileFactory.getInstance(project).createFileFromText("DUMMY.yaml", YAMLFileType.YML, code) as? YAMLFile

private fun YAMLFile.setProp(name: String, value: String) {
    val mapping = documents?.firstOrNull()?.topLevelValue as? YAMLMapping ?: return
    val keyValue = (createFromText("$name: $value", project)?.documents?.firstOrNull()?.topLevelValue as? YAMLMapping)?.getKeyValueByKey(name) ?: return
    runUndoTransparentWriteAction { mapping.putKeyValue(keyValue) }
}

val YAMLFile.sourcesDir
    get() = (getProp(SOURCES) as? YAMLScalar)?.textValue

val YAMLFile.binariesDir
    get() = (getProp(BINARIES) as? YAMLScalar)?.textValue

val YAMLFile.modules
    get() = (getProp(MODULES) as? YAMLSequence)?.items?.mapNotNull { item -> (item.value as? YAMLScalar)?.textValue?.let { ModulePath.fromString(it) } }

var YAMLFile.dependencies
    get() = (getProp(DEPENDENCIES) as? YAMLSequence)?.items?.mapNotNull { item -> (item.value as? YAMLScalar)?.textValue?.let { LibraryDependency(it) } } ?: emptyList()
    set(deps) {
        if (deps.isEmpty()) {
            if ((getProp(DEPENDENCIES) as? YAMLSequence)?.items?.isEmpty() == false) {
                setProp(DEPENDENCIES, "[]")
            }
        } else {
            setProp(DEPENDENCIES, yamlSeqFromList(deps.map { it.name }))
        }
    }

val PsiFile.isYAMLConfig: Boolean
    get() {
        if (this !is YAMLFile) {
            return false
        }
        if (name != FileUtils.LIBRARY_CONFIG_FILE) {
            return false
        }

        val rootPath = libraryConfig?.rootPath ?: return false
        return virtualFile.parent.path == FileUtil.toSystemIndependentName(rootPath.toString())
    }
