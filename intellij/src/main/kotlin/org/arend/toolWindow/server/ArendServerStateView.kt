package org.arend.toolWindow.server

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
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
import com.intellij.openapi.actionSystem.ActionUpdateThread
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.tree.TreePath
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import org.arend.psi.navigate
import org.arend.util.findLibrary
import org.arend.ext.module.ModuleLocation
import org.arend.naming.reference.TCDefReferable
import org.arend.typechecking.runner.RunnerService

class ArendServerStateView(private val project: Project, toolWindow: ToolWindow) {
    private val root = DefaultMutableTreeNode("Arend Server")
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)

    private val panel = SimpleToolWindowPanel(false)

    // Helpers to extract current selection
    private fun selectedModuleLocations(): List<ModuleLocation> {
        val paths = tree.selectionPaths?.toList().orEmpty()
        val result = ArrayList<ModuleLocation>()
        for (p in paths) {
            val node = (p.lastPathComponent as? DefaultMutableTreeNode)?.userObject
            if (node is ModuleNode) result.add(node.location)
        }
        return result
    }

    private fun selectedDefinition(): TCDefReferable? {
        val path = tree.selectionPath ?: return null
        val node = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject
        return (node as? DefinitionNode)?.definition
    }

    // --- State persistence helpers (expanded nodes and selection) ---
    private fun nodeId(obj: Any?): String? = when (obj) {
        is LibraryNode -> "lib:" + obj.name
        is ModuleNode -> "mod:" + obj.location.libraryName + ":" + obj.location.locationKind + ":" + obj.location.modulePath.toString()
        is DefinitionNode -> "def:" + obj.definition.refLongName.toString()
        is String -> "root"
        else -> null
    }

    private fun pathId(path: TreePath?): String? {
        if (path == null) return null
        val parts = path.path.mapNotNull { (it as? DefaultMutableTreeNode)?.userObject }.map { nodeId(it) ?: return null }
        return parts.joinToString("|")
    }

    private fun captureTreeState(): Pair<Set<String>, String?> {
        val expanded = LinkedHashSet<String>()
        var row = 0
        while (row < tree.rowCount) {
            val p = tree.getPathForRow(row)
            if (p != null && tree.isExpanded(p)) {
                pathId(p)?.let { expanded.add(it) }
            }
            row++
        }
        val selected = pathId(tree.selectionPath)
        return Pair(expanded, selected)
    }

    private fun restoreTreeState(expandedIds: Set<String>, selectedId: String?) {
        // Build a map from id to TreePath for current tree by traversing all nodes (not only visible rows)
        val idToPath = HashMap<String, TreePath>()
        val rootNode = treeModel.root as? DefaultMutableTreeNode
        if (rootNode != null) {
            val e = rootNode.depthFirstEnumeration()
            while (e.hasMoreElements()) {
                val n = e.nextElement() as? DefaultMutableTreeNode ?: continue
                val p = TreePath(n.path)
                val id = pathId(p)
                if (id != null) idToPath[id] = p
            }
        }

        // Expand saved paths (shortest first to ensure parents are expanded before children)
        expandedIds.sortedBy { it.length }.forEach { id ->
            idToPath[id]?.let { tree.expandPath(it) }
        }

        // Restore selection (use the deepest existing prefix)
        var candidate = selectedId
        var selectedPath: TreePath? = null
        while (candidate != null && selectedPath == null) {
            selectedPath = idToPath[candidate]
            if (selectedPath == null) {
                val idx = candidate.lastIndexOf('|')
                candidate = if (idx > 0) candidate.take(idx) else null
            }
        }
        if (selectedPath != null) {
            tree.selectionPath = selectedPath
            tree.scrollPathToVisible(selectedPath)
        }
    }

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
        toolbarGroup.addSeparator()
        // Server actions
        val resolveModulesAction = ResolveSelectedModulesAction()
        val typecheckAction = TypecheckSelectedAction()
        toolbarGroup.add(resolveModulesAction)
        toolbarGroup.add(typecheckAction)

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
                    MouseButton.Right -> showContextMenu(e)
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
        // Capture expanded and selection state before rebuild
        val (expandedIds, selectedIdPath) = captureTreeState()

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

        // Restore expanded and selection state
        restoreTreeState(expandedIds, selectedIdPath)
    }

    // --- Actions ---
    private inner class ResolveSelectedModulesAction : AnAction("Resolve") {
        override fun update(e: AnActionEvent) {
            e.presentation.icon = ArendIcons.OK
            e.presentation.isEnabled = selectedModuleLocations().isNotEmpty()
        }

        override fun actionPerformed(e: AnActionEvent) {
            for (module in selectedModuleLocations()) {
                project.service<RunnerService>().runChecker(module, true)
            }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private inner class TypecheckSelectedAction : AnAction("Typecheck") {
        override fun update(e: AnActionEvent) {
            e.presentation.icon = ArendIcons.TURNSTILE
            e.presentation.isEnabled = selectedDefinition() != null || selectedModuleLocations().isNotEmpty()
        }

        override fun actionPerformed(e: AnActionEvent) {
            val def = selectedDefinition()
            if (def != null) {
                val fullName = def.refFullName
                project.service<RunnerService>().runChecker(fullName.module ?: return, fullName.longName)
            } else {
                for (module in selectedModuleLocations()) {
                    project.service<RunnerService>().runChecker(module, false)
                }
            }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private fun showContextMenu(e: MouseEvent) {
        val path = tree.getPathForLocation(e.x, e.y)
        if (path != null) tree.selectionPath = path
        val userObj = (path?.lastPathComponent as? DefaultMutableTreeNode)?.userObject
        val group = DefaultActionGroup().apply {
            when (userObj) {
                is DefinitionNode -> {
                    add(TypecheckSelectedAction())
                }
                is ModuleNode -> {
                    add(ResolveSelectedModulesAction())
                    add(TypecheckSelectedAction())
                }
            }
        }
        val popup = ActionManager.getInstance().createActionPopupMenu("ArendServerStateView.popup", group)
        popup.component.show(e.component, e.x, e.y)
    }
}
