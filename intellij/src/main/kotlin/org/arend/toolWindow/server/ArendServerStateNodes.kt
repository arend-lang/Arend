package org.arend.toolWindow.server

import org.arend.ArendIcons
import org.arend.core.definition.Definition
import org.arend.ext.module.ModuleLocation
import org.arend.naming.reference.TCDefReferable
import javax.swing.Icon

sealed interface ServerTreeNode

data class LibraryNode(val name: String) : ServerTreeNode

data class ModuleNode(val location: ModuleLocation) : ServerTreeNode

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
