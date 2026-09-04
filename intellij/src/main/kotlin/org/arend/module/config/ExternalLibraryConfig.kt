package org.arend.module.config

import com.intellij.openapi.vfs.VirtualFile
import org.arend.util.Range
import org.arend.util.Version
import org.arend.yaml.*
import org.jetbrains.yaml.psi.YAMLFile


class ExternalLibraryConfig(override val name: String, val yaml: YAMLFile, libraryRoot: VirtualFile? = yaml.virtualFile?.parent) : LibraryConfig(yaml.project) {
    override val sourcesDir = yaml.sourcesDir ?: ""
    override val binariesDir = yaml.binariesDir
    override val testsDir = yaml.testsDir
    override val extensionsDir = yaml.extensionsDir
    private val extensionMainClass = yaml.extensionMainClass
    override val modules = yaml.modules
    override val dependencies = yaml.dependencies
    override val version: Version? = yaml.version.let {
        if (it.isEmpty()) null else Version.fromString(it)
    }
    override val langVersion: Range<Version> = parseLangVersion(yaml.langVersion)

    override fun getLibraryVersion(): Version? = version

    override fun getExtensionMainClass() = extensionMainClass

    override fun isExternalLibrary() = true

    override var root = libraryRoot
        get() =
            if (field?.isValid == false) {
                field = null
                null
            } else field
        private set
}