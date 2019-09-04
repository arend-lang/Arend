package org.arend.module

import com.intellij.framework.library.LibraryVersionProperties
import com.intellij.ide.util.ChooseElementsDialog
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.libraries.LibraryType
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.roots.libraries.ui.LibraryEditorComponent
import com.intellij.openapi.roots.libraries.ui.LibraryPropertiesEditor
import com.intellij.openapi.roots.ui.configuration.FacetsProvider
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ArendIcons
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.orderRoot.ArendConfigOrderRootType
import org.arend.module.orderRoot.ArendLibraryRootsComponentDescriptor
import org.arend.util.FileUtils
import org.arend.util.findPsiFileByPath
import org.jetbrains.yaml.psi.YAMLFile
import java.nio.file.Files
import java.nio.file.Paths
import javax.swing.Icon
import javax.swing.JComponent

object ArendLibraryKind: PersistentLibraryKind<LibraryVersionProperties>("Arend") {
    override fun createDefaultProperties() = LibraryVersionProperties()

    override fun getAdditionalRootTypes() = arrayOf(ArendConfigOrderRootType)
}

class ArendLibraryType: LibraryType<LibraryVersionProperties>(ArendLibraryKind) {

    override fun createPropertiesEditor(editorComponent: LibraryEditorComponent<LibraryVersionProperties>): LibraryPropertiesEditor? = null

    override fun getCreateActionName() = "Arend library"

    override fun createLibraryRootsComponentDescriptor() = ArendLibraryRootsComponentDescriptor

    override fun createNewLibrary(parentComponent: JComponent, contextDirectory: VirtualFile?, project: Project): NewLibraryConfiguration? {
        val libHome = ProjectRootManager.getInstance(project).projectSdk?.homePath?.let { Paths.get(it) } ?: return null
        val externalLibs =
            Files.newDirectoryStream(libHome) { Files.isDirectory(it) }.use { stream ->
                stream.mapNotNull { subDir -> if (Files.isRegularFile(subDir.resolve(FileUtils.LIBRARY_CONFIG_FILE))) subDir.fileName.toString() else null }
            }

        val libName = ChooseLibrariesDialog(project, externalLibs).apply { show() }.chosenElements.firstOrNull() ?: return null
        val yaml = project.findPsiFileByPath(libHome.resolve(Paths.get(libName, FileUtils.LIBRARY_CONFIG_FILE))) as? YAMLFile ?: return null
        val library = ExternalLibraryConfig(libName, yaml)

        return object : NewLibraryConfiguration(libName, this, kind.createDefaultProperties()) {
            override fun addRoots(editor: LibraryEditor) {
                editor.addRoot(VfsUtil.pathToUrl(yaml.virtualFile.path), ArendConfigOrderRootType)
                val srcDir = if (library.sourcesDir.isNotEmpty()) library.sourcesPath else null
                if (srcDir != null) {
                    editor.addRoot(VfsUtil.pathToUrl(srcDir.toString()), OrderRootType.SOURCES)
                }
            }
        }
    }

    override fun getExternalRootTypes() = arrayOf(ArendConfigOrderRootType, OrderRootType.SOURCES)

    override fun getIcon(properties: LibraryVersionProperties?) = ArendIcons.LIBRARY_ICON

    override fun isSuitableModule(module: Module, facetsProvider: FacetsProvider) = ArendModuleType.has(module)

    class ChooseLibrariesDialog(project: Project, items: List<String>): ChooseElementsDialog<String>(project, items, "Libraries to add", null) {
        init {
            myChooser.setSingleSelectionMode()
        }

        override fun getItemIcon(item: String): Icon? = ArendIcons.LIBRARY_ICON

        override fun getItemText(item: String) = item
    }
}