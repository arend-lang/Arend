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
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.TreePath
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import org.arend.psi.navigate
import org.arend.util.findLibrary

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
        // Do not expand/collapse nodes on double-click; we handle double-click for navigation only
        tree.toggleClickCount = 0

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

        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                when (e.mouseButton) {
                    MouseButton.Left -> if (e.clickCount >= 2) {
                        navigate()
                    }
                    MouseButton.Right -> {}
                    else -> {}
                }
            }
        })

        // Initial fill
        refresh()
    }

    fun refresh() {
        ApplicationManager.getApplication().invokeLater {
            rebuildTree()
        }
    }

    private fun navigate() {
        val path: TreePath = tree.selectionPath ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        when (val obj = node.userObject) {
            is DefinitionNode -> {
                val tcRef = obj.definition
                val ok = runReadAction {
                    val psi = tcRef.data as? PsiElement
                    if (psi != null && psi.isValid) {
                        psi.navigationElement.navigate(true)
                        true
                    } else {
                        false
                    }
                }
                // Fallback: open the containing file by module location
                if (!ok) {
                    val location = tcRef.location
                    if (location != null) {
                        val file = project.findLibrary(location.libraryName)?.findArendFile(location)
                        file?.navigate(true)
                    }
                }
            }
            is ModuleNode -> {
                val loc = obj.location
                val file = project.findLibrary(loc.libraryName)?.findArendFile(loc)
                file?.navigate(true)
            }
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
