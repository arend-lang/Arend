package org.arend.toolWindow.server

import java.awt.Component
import javax.swing.Icon
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import org.arend.ArendIcons
import org.arend.ext.error.GeneralError
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.server.ArendServerService

class ArendServerStateTreeCellRenderer(private val project: Project) : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
        val comp = super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
        icon = null
        if (value is DefaultMutableTreeNode) {
            when (val obj = value.userObject) {
                is LibraryNode -> {
                    text = obj.name
                    icon = ArendIcons.LIBRARY_ICON
                }
                is FolderNode -> {
                    text = obj.dir.name
                    icon = ArendIcons.DIRECTORY
                }
                is FileNode -> {
                    val module = obj.module
                    if (module == null) {
                        text = obj.file.name
                        icon = ArendIcons.AREND_FILE
                    } else {
                        val server = project.service<ArendServerService>().server
                        // In folder-grouped view show only the last part (file name without extension)
                        val moduleName = obj.file.nameWithoutExtension
                        val resolvedDefs = server.getResolvedDefinitions(module).size
                        val resolved = resolvedDefs > 0

                        val errorList = server.errorMap[module].orEmpty()
                        val severity = errorList.maxOfOrNull { it.level }
                        val statusIcon: Icon = when (severity) {
                            GeneralError.Level.ERROR -> ArendIcons.ERROR
                            GeneralError.Level.GOAL -> ArendIcons.GOAL
                            GeneralError.Level.WARNING, GeneralError.Level.WARNING_UNUSED -> ArendIcons.WARNING
                            else -> if (resolved) ArendIcons.OK else ArendIcons.UNKNOWN
                        }
                        val statusSuffix = when {
                            severity == GeneralError.Level.ERROR -> " [errors]"
                            severity == GeneralError.Level.GOAL -> " [goals]"
                            severity == GeneralError.Level.WARNING || severity == GeneralError.Level.WARNING_UNUSED -> " [warnings]"
                            resolved -> " [resolved]"
                            else -> " [unresolved]"
                        }
                        text = moduleName + statusSuffix
                        icon = statusIcon
                    }
                }
                is ModuleNode -> {
                    val server = project.service<ArendServerService>().server
                    val moduleName = obj.location.modulePath.toString()
                    val resolvedDefs = server.getResolvedDefinitions(obj.location).size
                    val resolved = resolvedDefs > 0

                    val errorList = server.errorMap[obj.location].orEmpty()
                    val severity = errorList.maxOfOrNull { it.level }
                    val statusIcon: Icon = when (severity) {
                        GeneralError.Level.ERROR -> ArendIcons.ERROR
                        GeneralError.Level.GOAL -> ArendIcons.GOAL
                        GeneralError.Level.WARNING, GeneralError.Level.WARNING_UNUSED -> ArendIcons.WARNING
                        else -> if (resolved) ArendIcons.OK else ArendIcons.UNKNOWN
                    }
                    val statusSuffix = when {
                        severity == GeneralError.Level.ERROR -> " [errors]"
                        severity == GeneralError.Level.GOAL -> " [goals]"
                        severity == GeneralError.Level.WARNING || severity == GeneralError.Level.WARNING_UNUSED -> " [warnings]"
                        resolved -> " [resolved]"
                        else -> " [unresolved]"
                    }
                    text = moduleName + statusSuffix
                    icon = statusIcon
                }
                is DefinitionNode -> {
                    val def = obj.definition
                    text = def.refLongName.toString()
                    icon = statusToIcon(definitionStatus(def))
                }
                is GroupNode -> {
                    text = obj.name
                    icon = ArendIcons.DIRECTORY
                }
                is String -> {
                    text = obj
                    icon = ArendIcons.SERVER
                }
            }
        }
        return comp
    }
}
