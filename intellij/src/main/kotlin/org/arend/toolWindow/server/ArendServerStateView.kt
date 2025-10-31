package org.arend.toolWindow.server

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import org.arend.ArendIcons
import org.arend.server.ArendServerService
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ArendServerStateView(private val project: Project, toolWindow: ToolWindow) {
    private val root = DefaultMutableTreeNode("Arend Server")
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)

    private val panel = SimpleToolWindowPanel(false)

    init {
        toolWindow.setIcon(ArendIcons.SERVER)
        val contentManager = toolWindow.contentManager
        panel.setContent(ScrollPaneFactory.createScrollPane(tree, true))
        tree.cellRenderer = ArendServerStateTreeCellRenderer(project)
        tree.isRootVisible = false

        val toolbarGroup = DefaultActionGroup()
        val actions = CommonActionsManager.getInstance()
        val treeExpander = DefaultTreeExpander(tree)
        toolbarGroup.add(actions.createExpandAllAction(treeExpander, tree))
        toolbarGroup.add(actions.createCollapseAllAction(treeExpander, tree))
        toolbarGroup.addSeparator()
        toolbarGroup.add(ArendServerStateRefreshAction { refresh() })

        val toolbar = ActionManager.getInstance().createActionToolbar("ArendServerStateView.toolbar", toolbarGroup, false)
        toolbar.targetComponent = panel
        panel.toolbar = toolbar.component

        contentManager.addContent(contentManager.factory.createContent(panel, "", false))

        // Initial fill
        refresh()
    }

    fun refresh() {
        ApplicationManager.getApplication().invokeLater {
            rebuildTree()
        }
    }

    private fun rebuildTree() {
        val server = project.service<ArendServerService>().server
        val libraries = server.libraries
        root.removeAllChildren()

        val modulesByLibrary = server.modules.groupBy { it.libraryName }
        for (lib in libraries.sorted()) {
            val libNode = DefaultMutableTreeNode(LibraryNode(lib))
            val locations = modulesByLibrary[lib].orEmpty().sortedBy { it.modulePath.toString() }
            for (loc in locations) {
                val moduleNode = DefaultMutableTreeNode(ModuleNode(loc))
                // Resolve definitions and add them
                val defs = server.getResolvedDefinitions(loc)
                for (def in defs) {
                    moduleNode.add(DefaultMutableTreeNode(DefinitionNode(def.definition.data)))
                }
                libNode.add(moduleNode)
            }
            root.add(libNode)
        }
        treeModel.reload(root)
        for (i in 0 until tree.rowCount) {
            tree.expandRow(i)
        }
    }
}
