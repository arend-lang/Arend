package org.arend.toolWindow.server

import com.intellij.openapi.vfs.VirtualFile
import org.arend.ArendIcons
import org.arend.core.definition.Definition
import org.arend.ext.module.ModuleLocation
import org.arend.naming.reference.TCDefReferable
import javax.swing.Icon

sealed interface ServerTreeNode

// Top-level library node
 data class LibraryNode(val name: String) : ServerTreeNode

// Module node known to the server
 data class ModuleNode(val location: ModuleLocation) : ServerTreeNode

// File system nodes for folder-grouped view
 data class FolderNode(val libraryName: String, val isTest: Boolean, val dir: VirtualFile) : ServerTreeNode

 data class FileNode(val libraryName: String, val isTest: Boolean, val file: VirtualFile, val module: ModuleLocation?) : ServerTreeNode

// Optional grouping node for definitions (by inner groups/namespaces)
 data class GroupNode(val name: String) : ServerTreeNode

// Definition node
 data class DefinitionNode(val definition: TCDefReferable) : ServerTreeNode

enum class NodeStatus { OK, WARN, ERROR, UNKNOWN }

fun statusToIcon(status: NodeStatus): Icon = when (status) {
    NodeStatus.OK -> ArendIcons.OK
    NodeStatus.WARN -> ArendIcons.WARNING
    NodeStatus.ERROR -> ArendIcons.ERROR
    NodeStatus.UNKNOWN -> ArendIcons.UNKNOWN
}

fun definitionStatus(def: TCDefReferable): NodeStatus = when (def.typechecked?.status()) {
    Definition.TypeCheckingStatus.HAS_ERRORS -> NodeStatus.ERROR
    Definition.TypeCheckingStatus.HAS_WARNINGS -> NodeStatus.WARN
    Definition.TypeCheckingStatus.NO_ERRORS -> NodeStatus.OK
    else -> NodeStatus.UNKNOWN
}
