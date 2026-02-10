package org.arend.toolWindow.server

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.TreeSpeedSearch
import org.arend.ArendIcons
import org.arend.server.ArendServerService
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import com.intellij.codeInsight.hints.presentation.MouseButton
import com.intellij.codeInsight.hints.presentation.mouseButton
import com.intellij.openapi.actionSystem.ActionUpdateThread
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import javax.swing.AbstractAction
import javax.swing.tree.TreePath
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiElement
import org.arend.psi.navigate
import org.arend.util.findLibrary
import org.arend.ext.module.ModuleLocation
import org.arend.naming.reference.TCDefReferable
import org.arend.term.concrete.Concrete
import org.arend.typechecking.runner.RunnerService
import javax.swing.JLabel
import javax.swing.JPanel
import java.awt.BorderLayout
import org.arend.term.group.ConcreteGroup
import org.arend.ext.error.GeneralError
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiDocumentManager
import org.arend.psi.ancestor
import org.arend.psi.ext.ReferableBase
import org.arend.module.config.LibraryConfig
import org.arend.util.FileUtils
import org.arend.psi.ArendFile
import org.arend.server.ArendServer
import com.intellij.icons.AllIcons
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ModalityState

class ArendServerStateView(private val project: Project, toolWindow: ToolWindow) {
    private var suppressSelectionEvents: Boolean = false
    private var groupByFolders: Boolean
        get() = project.service<ArendServerStateService>().groupByFolders
        set(value) {
            project.service<ArendServerStateService>().groupByFolders = value
        }
    private var groupDefinitions: Boolean
        get() = project.service<ArendServerStateService>().groupDefinitions
        set(value) {
            project.service<ArendServerStateService>().groupDefinitions = value
        }
    private val root = DefaultMutableTreeNode("Arend Server")
    private val treeModel = DefaultTreeModel(root)
    private val tree = Tree(treeModel)

    private val panel = SimpleToolWindowPanel(false)
    private val statusLabel = JLabel()

    // Helpers to extract current selection
    private fun selectedModuleLocations(): List<ModuleLocation> {
        val paths = tree.selectionPaths?.toList().orEmpty()
        val result = ArrayList<ModuleLocation>()
        for (p in paths) {
            when (val node = (p.lastPathComponent as? DefaultMutableTreeNode)?.userObject) {
                is ModuleNode -> result.add(node.location)
                is FileNode -> node.module?.let { result.add(it) }
            }
        }
        return result
    }

    private fun selectedLibraryNames(): List<String> {
        val paths = tree.selectionPaths?.toList().orEmpty()
        val result = ArrayList<String>()
        for (p in paths) {
            val node = (p.lastPathComponent as? DefaultMutableTreeNode)?.userObject
            if (node is LibraryNode) result.add(node.name)
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
        is FolderNode -> "dir:" + obj.libraryName + ":" + (if (obj.isTest) "test" else "src") + ":" + (obj.dir.path)
        is FileNode -> "file:" + obj.libraryName + ":" + (if (obj.isTest) "test" else "src") + ":" + obj.file.path
        is GroupNode -> "grp:" + obj.name
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

    private inner class GroupByFoldersToggle : ToggleAction("Group By Folders", null, ArendIcons.GROUP_BY_FOLDER) {
        override fun isSelected(e: AnActionEvent): Boolean = groupByFolders
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            groupByFolders = state
            refresh()
        }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private inner class GroupDefinitionsToggle : ToggleAction("Group Definitions", null, ArendIcons.GROUP_BY_DEFINITION) {
        override fun isSelected(e: AnActionEvent): Boolean = groupDefinitions
        override fun setSelected(e: AnActionEvent, state: Boolean) {
            groupDefinitions = state
            refresh()
        }
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
    }

    private inner class NavigateToFromSourceAction : AnAction("Navigate To/From Source", "Navigate depending on focus: tree → source, editor → tree", AllIcons.General.Locate) {
        override fun update(e: AnActionEvent) {
            val hasTreeSelection = tree.selectionPath != null
            val hasEditorFile = FileEditorManager.getInstance(project).selectedFiles.isNotEmpty()
            e.presentation.isEnabled = hasTreeSelection || hasEditorFile
        }
        override fun actionPerformed(e: AnActionEvent) {
            val focusOwner = com.intellij.openapi.wm.IdeFocusManager.getInstance(project).focusOwner
            val isTreeFocused = focusOwner != null && javax.swing.SwingUtilities.isDescendingFrom(focusOwner, tree)
            if (isTreeFocused) {
                val path: TreePath = tree.selectionPath ?: return
                val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                navigate(node.userObject)
            } else {
                val editor = FileEditorManager.getInstance(project).selectedTextEditor
                val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                if (editor != null && file != null) {
                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    if (psiFile != null) {
                        PsiDocumentManager.getInstance(project).commitAllDocuments()
                        val def = psiFile.findElementAt(editor.caretModel.offset)?.ancestor<ReferableBase<*>>()?.tcReferable
                        if (def != null) {
                            val path = findPathForDefinition(def)
                            if (path != null) {
                                selectNode(path)
                                return
                            }
                        }
                    }
                }
                // Fallback: select by file/module
                selectByFile(file ?: return)
            }
        }
        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private fun forEachTreeNode(action: (DefaultMutableTreeNode) -> Boolean): DefaultMutableTreeNode? {
        val rootNode = treeModel.root as? DefaultMutableTreeNode ?: return null
        val e = rootNode.depthFirstEnumeration()
        while (e.hasMoreElements()) {
            val n = e.nextElement() as? DefaultMutableTreeNode ?: continue
            if (action(n)) return n
        }
        return null
    }

    private fun selectNode(path: TreePath?) {
        if (path == null) return
        suppressSelectionEvents = true
        try {
            tree.selectionPath = path
            tree.scrollPathToVisible(path)
        } finally {
            suppressSelectionEvents = false
        }
    }

    private fun findPathForFile(file: VirtualFile): TreePath? {
        val node = forEachTreeNode { n ->
            when (val o = n.userObject) {
                is FileNode -> o.file == file
                else -> false
            }
        } ?: return null
        return TreePath(node.path)
    }

    private fun findPathForModule(module: ModuleLocation): TreePath? {
        val node = forEachTreeNode { n ->
            when (val o = n.userObject) {
                is ModuleNode -> o.location == module
                else -> false
            }
        } ?: return null
        return TreePath(node.path)
    }

    private fun findPathForDefinition(def: TCDefReferable): TreePath? {
        val node = forEachTreeNode { n ->
            when (val o = n.userObject) {
                is DefinitionNode -> o.definition == def
                else -> false
            }
        } ?: return null
        return TreePath(node.path)
    }

    private fun selectByFile(file: VirtualFile) {
        // Prefer FileNode in folder view
        var path = findPathForFile(file)
        if (path == null) {
            val psi = PsiManager.getInstance(project).findFile(file)
            val arend = psi as? ArendFile
            val loc = arend?.moduleLocation
            if (loc != null) path = findPathForModule(loc)
        }
        if (path != null) selectNode(path)
    }

    private fun navigate(obj: Any?) {
        when (obj) {
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
            is FileNode -> {
                PsiManager.getInstance(project).findFile(obj.file)?.navigate(true)
            }
        }
    }

    init {
        toolWindow.setIcon(ArendIcons.SERVER)
        val contentManager = toolWindow.contentManager
        // Build content panel with a status line on top and the tree in the center
        val contentPanel = JPanel(BorderLayout())
        statusLabel.text = "Modules: 0/0 • Definitions: 0/0 • Errors: 0 • Goals: 0"
        contentPanel.add(statusLabel, BorderLayout.NORTH)
        contentPanel.add(ScrollPaneFactory.createScrollPane(tree, true), BorderLayout.CENTER)
        panel.setContent(contentPanel)
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
        // View mode toggles
        toolbarGroup.add(GroupByFoldersToggle())
        toolbarGroup.add(GroupDefinitionsToggle())
        toolbarGroup.add(NavigateToFromSourceAction())
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

        // Type to search by node name/path
        TreeSpeedSearch.installOn(tree, true) { path: TreePath ->
            val dmtn = path.lastPathComponent as? DefaultMutableTreeNode ?: return@installOn ""
            when (val obj = dmtn.userObject) {
                is LibraryNode -> obj.name
                is ModuleNode -> buildString {
                    append(obj.location.modulePath.toList().lastOrNull() ?: "")
                    append(" ")
                    append(obj.location.modulePath)
                }
                is FolderNode -> obj.dir.name
                is FileNode -> obj.file.nameWithoutExtension
                is GroupNode -> obj.name
                is DefinitionNode -> obj.definition.refName
                else -> ""
            }
        }

        // Press Enter to navigate to the selected item
        tree.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "arendNavigate")
        tree.actionMap.put("arendNavigate", object : AbstractAction() {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                navigate()
            }
        })

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
    }

    fun refresh() {
        ApplicationManager.getApplication().invokeLater {
            rebuildTree()
        }
    }

    private fun navigate() {
        val path: TreePath = tree.selectionPath ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        navigate(node.userObject)
    }

    private fun rebuildTree() {
        if (groupByFolders) rebuildByFolders() else rebuildByLibraries()
    }

    private fun countDefs(group: ConcreteGroup?): Int {
        if (group == null) return 0
        var cnt = 0
        val def = group.definition
        if (def is Concrete.ResolvableDefinition) cnt++
        for (st in group.statements()) {
            cnt += countDefs(st.group())
        }
        for (dg in group.dynamicGroups()) {
            cnt += countDefs(dg)
        }
        return cnt
    }

    private fun addDefs(parent: DefaultMutableTreeNode, group: ConcreteGroup?, grouped: Boolean) {
        if (group == null) return
        if (!grouped) {
            // Flat listing
            val def = group.definition
            if (def is Concrete.ResolvableDefinition) parent.add(DefaultMutableTreeNode(DefinitionNode(def.data)))
            for (st in group.statements()) addDefs(parent, st.group(), false)
            for (dg in group.dynamicGroups()) addDefs(parent, dg, false)
        } else {
            // Grouped mode: internal nodes are DefinitionNode as well; nest subgroup definitions under the current definition node
            val def = group.definition
            val parentForChildren = if (def is Concrete.ResolvableDefinition) {
                val defNode = DefaultMutableTreeNode(DefinitionNode(def.data))
                parent.add(defNode)
                defNode
            } else parent
            for (st in group.statements()) addDefs(parentForChildren, st.group(), true)
            for (dg in group.dynamicGroups()) addDefs(parentForChildren, dg, true)
        }
    }

    private fun updateStatusLabel(server: ArendServer, resolvedModules: Int, totalModules: Int, typecheckedDefinitions: Int, totalDefinitions: Int) {
        // Collect goals and errors from the server
        var errorCount = 0
        var goalCount = 0
        for (errorList in server.errorMap.values) {
            for (error in errorList) {
                when (error.level) {
                    GeneralError.Level.GOAL -> goalCount++
                    GeneralError.Level.ERROR -> errorCount++
                    else -> {}
                }
            }
        }

        // Update status label
        statusLabel.text = "Modules: $resolvedModules/$totalModules • Definitions: $typecheckedDefinitions/$totalDefinitions • Errors: $errorCount • Goals: $goalCount"
    }

    private fun rebuildByLibraries() {
        // Note: library→module→definition view (existing behavior)
        // Capture expanded and selection state before rebuild
        val (expandedIds, selectedIdPath) = captureTreeState()

        val server = project.service<ArendServerService>().server
        val libraries = server.libraries
        root.removeAllChildren()

        // Stats counters
        val allModules = server.modules.toList()
        val totalModules = allModules.size
        var resolvedModules = 0
        var totalDefinitions = 0
        var typecheckedDefinitions = 0

        val modulesByLibrary = allModules.groupBy { it.libraryName }
        for (lib in libraries.sorted()) {
            val libNode = DefaultMutableTreeNode(LibraryNode(lib))
            val locations = modulesByLibrary[lib].orEmpty().sortedBy { it.modulePath.toString() }
            for (loc in locations) {
                val moduleNode = DefaultMutableTreeNode(ModuleNode(loc))

                // Count total definitions from raw group (available even if not resolved)
                val rawGroup = server.getRawGroup(loc)
                totalDefinitions += countDefs(rawGroup)

                // Definitions for the module:
                // - Always list definitions from the raw group (available even if the module is not resolved)
                // - Compute typechecked counters from resolved definitions, but do not rely on them for listing
                val resolvedDefs = server.getResolvedDefinitions(loc)
                if (resolvedDefs.isNotEmpty()) resolvedModules++
                for (def in resolvedDefs) {
                    if (def.definition.data.isTypechecked()) {
                        typecheckedDefinitions++
                    }
                }

                // Add definitions from the raw group; apply grouping according to the toggle
                addDefs(moduleNode, rawGroup, groupDefinitions)
                libNode.add(moduleNode)
            }
            root.add(libNode)
        }
        treeModel.reload(root)

        // Update status label
        updateStatusLabel(server, resolvedModules, totalModules, typecheckedDefinitions, totalDefinitions)

        // Restore expanded and selection state
        restoreTreeState(expandedIds, selectedIdPath)
    }

    private fun rebuildByFolders() {
        // Capture expanded and selection state before rebuild (EDT)
        val (expandedIds, selectedIdPath) = captureTreeState()

        data class FileVM(val libraryName: String, val isTest: Boolean, val file: VirtualFile, val moduleLoc: ModuleLocation?, val rawGroup: ConcreteGroup?)
        data class FolderVM(val libraryName: String, val isTest: Boolean, val dir: VirtualFile, val subFolders: List<FolderVM>, val files: List<FileVM>)
        data class LibraryVM(val name: String, val roots: List<FolderVM>)
        data class Stats(val resolvedModules: Int, val totalModules: Int, val typecheckedDefinitions: Int, val totalDefinitions: Int)

        val server = project.service<ArendServerService>().server

        ReadAction.nonBlocking<Pair<List<LibraryVM>, Stats>> {
            // Compute the entire model in a background read action
            fun countStats(): Stats {
                val allModules = server.modules.toList()
                val totalModules = allModules.size
                var resolvedModules = 0
                var totalDefinitions = 0
                var typecheckedDefinitions = 0
                for (loc in allModules) {
                    val raw = server.getRawGroup(loc)
                    totalDefinitions += countDefs(raw)
                    val resolvedDefs = server.getResolvedDefinitions(loc)
                    if (resolvedDefs.isNotEmpty()) resolvedModules++
                    for (def in resolvedDefs) if (def.definition.data.isTypechecked()) typecheckedDefinitions++
                }
                return Stats(resolvedModules, totalModules, typecheckedDefinitions, totalDefinitions)
            }

            fun buildDirVM(libraryName: String, dir: VirtualFile, isTest: Boolean): FolderVM {
                val children = dir.children.orEmpty().sortedBy { it.name }
                val subFolders = ArrayList<FolderVM>()
                val files = ArrayList<FileVM>()
                for (child in children) {
                    if (child.isDirectory) {
                        subFolders.add(buildDirVM(libraryName, child, isTest))
                    }
                }
                val psiMgr = PsiManager.getInstance(project)
                for (child in children) {
                    if (!child.isDirectory && child.name.endsWith(FileUtils.EXTENSION)) {
                        val psi = psiMgr.findFile(child)
                        val arendFile = psi as? ArendFile
                        val moduleLoc = arendFile?.moduleLocation
                        val rawGroup = moduleLoc?.let { server.getRawGroup(it) }
                        files.add(FileVM(libraryName, isTest, child, moduleLoc, rawGroup))
                    }
                }
                return FolderVM(libraryName, isTest, dir, subFolders, files)
            }

            val libraries = server.libraries.sorted()
            val libraryVMs = ArrayList<LibraryVM>()
            for (lib in libraries) {
                val config: LibraryConfig? = project.findLibrary(lib)
                if (config != null) {
                    val roots = ArrayList<FolderVM>()
                    config.sourcesDirFile?.let { roots.add(buildDirVM(lib, it, false)) }
                    config.testsDirFile?.let { roots.add(buildDirVM(lib, it, true)) }
                    libraryVMs.add(LibraryVM(lib, roots))
                } else {
                    libraryVMs.add(LibraryVM(lib, emptyList()))
                }
            }
            Pair(libraryVMs, countStats())
        }
            .coalesceBy(this, "rebuildByFolders")
            .expireWith(project)
            .finishOnUiThread(ModalityState.any()) { (libs, stats) ->
                if (project.isDisposed) return@finishOnUiThread
                // Rebuild UI tree on EDT from the computed view-model
                root.removeAllChildren()
                for (lib in libs) {
                    val libNode = DefaultMutableTreeNode(LibraryNode(lib.name))
                    fun buildDir(parent: DefaultMutableTreeNode, vm: FolderVM) {
                        // Folders first
                        for (sf in vm.subFolders) {
                            val folderNode = DefaultMutableTreeNode(FolderNode(vm.libraryName, vm.isTest, sf.dir))
                            parent.add(folderNode)
                            buildDir(folderNode, sf)
                        }
                        // Then files
                        for (f in vm.files) {
                            val fileNode = DefaultMutableTreeNode(FileNode(f.libraryName, f.isTest, f.file, f.moduleLoc))
                            if (f.rawGroup != null) {
                                addDefs(fileNode, f.rawGroup, groupDefinitions)
                            }
                            parent.add(fileNode)
                        }
                    }
                    for (rootVm in lib.roots) buildDir(libNode, rootVm)
                    root.add(libNode)
                }

                treeModel.reload(root)

                // Update status label
                updateStatusLabel(server, stats.resolvedModules, stats.totalModules, stats.typecheckedDefinitions, stats.totalDefinitions)

                // Restore expanded and selection state
                restoreTreeState(expandedIds, selectedIdPath)
            }
            .submit(com.intellij.util.concurrency.AppExecutorUtil.getAppExecutorService())
    }

    // --- Actions ---
    private inner class ResolveSelectedModulesAction : AnAction("Resolve") {
        override fun update(e: AnActionEvent) {
            e.presentation.icon = ArendIcons.OK
            e.presentation.isEnabled = selectedModuleLocations().isNotEmpty() || selectedLibraryNames().isNotEmpty()
        }

        override fun actionPerformed(e: AnActionEvent) {
            val modules = selectedModuleLocations()
            val libraries = selectedLibraryNames()
            if (modules.isNotEmpty()) {
                for (module in modules) {
                    project.service<RunnerService>().runChecker(module, true)
                }
            } else {
                // Resolve whole libraries (both sources and tests)
                for (lib in libraries) {
                    project.service<RunnerService>().runChecker(lib, true, null, null, true)
                }
            }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
    }

    private inner class TypecheckSelectedAction : AnAction("Typecheck") {
        override fun update(e: AnActionEvent) {
            e.presentation.icon = ArendIcons.TURNSTILE
            e.presentation.isEnabled = selectedDefinition() != null || selectedModuleLocations().isNotEmpty() || selectedLibraryNames().isNotEmpty()
        }

        override fun actionPerformed(e: AnActionEvent) {
            val def = selectedDefinition()
            if (def != null) {
                val fullName = def.refFullName
                project.service<RunnerService>().runChecker(fullName.module ?: return, fullName.longName)
            } else {
                val modules = selectedModuleLocations()
                val libraries = selectedLibraryNames()
                if (modules.isNotEmpty()) {
                    for (module in modules) {
                        project.service<RunnerService>().runChecker(module, false)
                    }
                } else {
                    // Typecheck whole libraries (both sources and tests)
                    for (lib in libraries) {
                        project.service<RunnerService>().runChecker(lib, true, null, null, false)
                    }
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
                is FileNode -> {
                    if (userObj.module != null) {
                        add(ResolveSelectedModulesAction())
                        add(TypecheckSelectedAction())
                    }
                }
                is FolderNode -> {
                    // No actions for pure folders
                }
                is LibraryNode -> {
                    add(ResolveSelectedModulesAction())
                    add(TypecheckSelectedAction())
                }
            }
        }
        val popup = ActionManager.getInstance().createActionPopupMenu("ArendServerStateView.popup", group)
        popup.component.show(e.component, e.x, e.y)
    }
}
