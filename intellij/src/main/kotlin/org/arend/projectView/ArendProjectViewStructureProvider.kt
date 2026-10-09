package org.arend.projectView

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.*
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElementNode
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.arend.ArendIcons
import org.arend.arc.ArcFileSystem
import org.arend.arc.ArcVirtualFile
import org.arend.ext.module.ModulePath
import org.arend.ext.module.ModuleLocation
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.LibraryConfig
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService
import org.arend.util.FileUtils
import org.arend.util.arendModules
import org.arend.util.findLibrary

class ArendProjectViewStructureProvider : TreeStructureProvider {
    override fun modify(parent: AbstractTreeNode<*>,
                        children: MutableCollection<AbstractTreeNode<*>>,
                        settings: ViewSettings?)
            : MutableCollection<AbstractTreeNode<*>> {
        if (parent is PsiDirectoryNode) {
            return withArcViews(parent, children, settings)
        }
        if (parent !is NamedLibraryElementNode) {
            return children
        }
        val name = (parent.value?.orderEntry as? LibraryOrderEntry)?.library?.name ?: return children
        val project = parent.project
        val server = project?.service<ArendServerService>()?.server ?: return children
        val config = project.findLibrary(name) ?: return children
        val modules = server.modules.filter { it.locationKind == ModuleLocation.LocationKind.GENERATED && it.libraryName == name }
        if (modules.isEmpty()) return children
        return mutableListOf(ArendMetasNode(project, config, modules, settings), *children.toTypedArray())
    }
}

/**
 * The binaries directory of an Arend module is shown as the .arc views of the module, which mirror its sources: an
 * .arc view for every .ard file, whether its .arc on disk exists or not (see ArcFileSystem).
 */
private fun withArcViews(parent: PsiDirectoryNode, children: MutableCollection<AbstractTreeNode<*>>, settings: ViewSettings?): MutableCollection<AbstractTreeNode<*>> {
    val project = parent.project ?: return children
    val dir = parent.virtualFile?.takeIf { it.isInLocalFileSystem } ?: return children
    val configs = project.arendModules.mapNotNull { ArendModuleConfigService.getInstance(it) }.filter {
        // A module without a binaries directory has no binary cache, and so no .arc views
        !it.binariesDir.isNullOrEmpty() && it.sourcesDirFile != null && it.binariesDirPath?.parent == dir.toNioPath()
    }
    if (configs.isEmpty()) return children

    val binariesDirs = configs.mapNotNull { it.binariesDirPath }
    val result = children.filterTo(ArrayList()) { child ->
        val file = (child as? PsiDirectoryNode)?.virtualFile
        file == null || !file.isInLocalFileSystem || file.toNioPath() !in binariesDirs
    }
    for (config in configs) {
        result.add(ArcDirectoryNode(project, config, emptyList(), config.binariesDirPath!!.fileName.toString(), settings))
    }
    return result
}

private data class ArcDirectory(val libraryName: String, val path: List<String>)

// The .arc views of the .ard files of a directory of the sources, and its subdirectories that have .ard files
private class ArcDirectoryNode(project: Project,
                               val config: ArendModuleConfigService,
                               val path: List<String>,
                               private val name: String,
                               settings: ViewSettings?) : ProjectViewNode<ArcDirectory>(project, ArcDirectory(config.name, path), settings) {
    override fun update(presentation: PresentationData) {
        presentation.presentableText = name
        presentation.setIcon(AllIcons.Nodes.Folder)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        val sources = config.sourcesDirFile ?: return mutableListOf()
        val dir = (if (path.isEmpty()) sources else sources.findFileByRelativePath(path.joinToString("/"))) ?: return mutableListOf()
        val fileSystem = ArcFileSystem.getInstance()
        return dir.children.mapNotNullTo(ArrayList()) { child ->
            if (child.isDirectory) {
                if (hasArendFiles(child)) ArcDirectoryNode(myProject, config, path + child.name, child.name, settings) else null
            } else if (child.name.endsWith(FileUtils.EXTENSION)) {
                ArcViewNode(myProject, fileSystem.findFile(myProject, config.name, ModulePath(path + child.name.removeSuffix(FileUtils.EXTENSION))), settings)
            } else null
        }
    }

    private fun hasArendFiles(dir: VirtualFile): Boolean =
        !VfsUtilCore.iterateChildrenRecursively(dir, null) { it.isDirectory || !it.name.endsWith(FileUtils.EXTENSION) }

    override fun contains(file: VirtualFile): Boolean {
        if (file !is ArcVirtualFile || file.libraryName != config.name) return false
        val modulePath = file.modulePath.toList()
        return modulePath.size > path.size && modulePath.subList(0, path.size) == path
    }

    @Suppress("UnstableApiUsage")
    override fun getSortOrder(settings: NodeSortSettings): NodeSortOrder =
            if (settings.isFoldersAlwaysOnTop) NodeSortOrder.FOLDER
            else super.getSortOrder(settings)

    override fun getTypeSortWeight(sortByType: Boolean): Int = 3

    override fun getWeight(): Int = if (settings.isFoldersAlwaysOnTop) 20 else super.getWeight()
}

private class ArcViewNode(project: Project, file: ArcVirtualFile, settings: ViewSettings?) : ProjectViewNode<ArcVirtualFile>(project, file, settings) {
    override fun update(presentation: PresentationData) {
        presentation.presentableText = value.name
        presentation.setIcon(ArendIcons.ARC_FILE)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> = mutableListOf()

    override fun isAlwaysLeaf() = true

    override fun getVirtualFile(): VirtualFile = value

    override fun contains(file: VirtualFile) = file == value

    override fun canNavigate() = true

    override fun canNavigateToSource() = true

    override fun navigate(requestFocus: Boolean) {
        FileEditorManager.getInstance(myProject).openFile(value, requestFocus)
    }
}

private class ArendMetasNode(project: Project?,
                             val config: LibraryConfig,
                             val modules: List<ModuleLocation>,
                             settings: ViewSettings?) : ProjectViewNode<String>(project, "ext", settings) {
    override fun update(presentation: PresentationData) {
        presentation.presentableText = "ext"
        presentation.setIcon(AllIcons.Modules.GeneratedFolder)
    }

    override fun getChildren(): MutableCollection<out AbstractTreeNode<*>> {
        return modules
                .mapNotNull {
                    config.findArendFile(it)
                            ?.let { file -> ArendMetaModuleNode(parent.project, it.modulePath, file, settings) }
                }.toMutableList()
    }

    /**
     * @see com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.getSortOrder
     */
    @Suppress("UnstableApiUsage")
    override fun getSortOrder(settings: NodeSortSettings): NodeSortOrder =
            if (settings.isFoldersAlwaysOnTop) NodeSortOrder.FOLDER
            else super.getSortOrder(settings)

    /**
     * @see com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.getTypeSortWeight
     */
    override fun getTypeSortWeight(sortByType: Boolean): Int = 3

    /**
     * @see com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode.getWeight
     */
    override fun getWeight(): Int = if (settings.isFoldersAlwaysOnTop) 20 else super.getWeight()

    /**
     * Used to calculate background color of the node.
     * @see com.intellij.ide.projectView.impl.ProjectViewTree.getFileColorFor
     */
    override fun getVirtualFile(): VirtualFile? = config.extensionDirFile

    override fun contains(file: VirtualFile): Boolean = false
}

private open class ArendMetaModuleNode(project: Project?,
                                       protected val modulePath: ModulePath,
                                       file: ArendFile,
                                       settings: ViewSettings?)
    : PsiFileNode(project, file, settings) {
    override fun updateImpl(data: PresentationData) {
        data.presentableText = modulePath.toString() + FileUtils.EXTENSION
    }
}