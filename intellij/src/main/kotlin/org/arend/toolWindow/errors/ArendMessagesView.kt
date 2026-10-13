package org.arend.toolWindow.errors

import com.intellij.ide.CommonActionsManager
import com.intellij.ide.DefaultTreeExpander
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.psi.*
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.SingleHeightTabs
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendIcons
import org.arend.ext.error.GeneralError
import org.arend.ext.error.LocalError
import org.arend.ext.error.MissingClausesError
import org.arend.ext.module.ModuleLocation
import org.arend.ext.reference.ArendRef
import org.arend.ext.reference.DataContainer
import org.arend.psi.ArendElementTypes.BLOCK_COMMENT
import org.arend.psi.ArendElementTypes.LINE_COMMENT
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.ext.ArendGoal
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.server.ArendServerService
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendSettings
import org.arend.toolWindow.errors.tree.ArendErrorTree
import org.arend.toolWindow.errors.tree.ArendErrorTreeAutoScrollFromSource
import org.arend.toolWindow.errors.tree.ArendErrorTreeAutoScrollToSource
import org.arend.toolWindow.errors.tree.ArendErrorTreeCellRenderer
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement
import org.arend.util.ArendBundle
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeSelectionEvent
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ArendMessagesView(private val project: Project, private val toolWindow: ToolWindow) : TreeSelectionListener, ProjectManagerListener {
    private val root = DefaultMutableTreeNode("Errors")
    private val treeModel = DefaultTreeModel(root)
    val tree = ArendErrorTree(treeModel)
    private val autoScrollFromSource = ArendErrorTreeAutoScrollFromSource(project, tree)

    private val treePanel = SimpleToolWindowPanel(false)
    private val treeDetailsSplitter = OnePixelSplitter()
    private val goalsErrorsSplitter = OnePixelSplitter(!toolWindow.anchor.isHorizontal, 0.5f)

    private var goalEditor: ArendMessagesViewEditor? = null
    private val goalEmptyPanel =
        JBPanelWithEmptyText().withEmptyText(ArendBundle.message("arend.messages.view.empty.goal.panel.text"))
    private val goalsPanel = JBUI.Panels.simplePanel(goalEmptyPanel)
    private val defaultGoalsTabTitle = ArendBundle.message("arend.messages.view.latest.goal.title")
    private val goalsTabInfo = TabInfo(goalsPanel).apply {
        setText(defaultGoalsTabTitle)
        setTooltipText(ArendBundle.message("arend.messages.view.latest.goal.tooltip"))
    }

    private var errorEditor: ArendMessagesViewEditor? = null
    private val errorEmptyPanel =
        JBPanelWithEmptyText().withEmptyText(ArendBundle.message("arend.messages.view.empty.error.panel.text"))
    private val errorsPanel = JBUI.Panels.simplePanel(errorEmptyPanel)
    private val errorToolTipText = ArendBundle.message("arend.messages.view.error.tooltip")
    private val errorTabInfo = TabInfo(errorsPanel).apply {
        setText(ArendBundle.message("arend.messages.view.error.title"))
        setTooltipText(errorToolTipText)
    }

    private var infoEditor: ArendInfoViewEditor? = null
    private val infoEmptyPanel =
        JBPanelWithEmptyText().withEmptyText(ArendBundle.message("arend.messages.view.empty.info.panel.text"))
    private val infoPanel = JBUI.Panels.simplePanel(infoEmptyPanel)
    private val infoToolTipText = ArendBundle.message("arend.messages.view.info.tooltip")
    private val infoTabInfo = TabInfo(infoPanel).apply {
        setText(ArendBundle.message("arend.messages.view.info.title"))
        setTooltipText(infoToolTipText)
    }

    private var isCursorOnError = false
    private var isCursorOnComment = false
    private var lastComment: PsiComment? = null
    var lastDocCorrectness: Boolean = true

    private val secondTabs = SingleHeightTabs(project, toolWindow.disposable)

    init {
        ProjectManager.getInstance().addProjectManagerListener(project, this)

        toolWindow.setIcon(ArendIcons.MESSAGES)
        val contentManager = toolWindow.contentManager
        contentManager.addContent(contentManager.factory.createContent(treeDetailsSplitter, "", false))
        project.messageBus.connect(project)
            .subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
                override fun toolWindowShown(toolWindow: ToolWindow) {
                    updateOrientation(toolWindow)
                }
            })

        setupTreeDetailsSplitter(!toolWindow.anchor.isHorizontal)

        tree.cellRenderer = ArendErrorTreeCellRenderer()
        tree.addTreeSelectionListener(this)

        val actionGroup = DefaultActionGroup()
        val actionManager = CommonActionsManager.getInstance()
        val treeExpander = DefaultTreeExpander(tree)
        actionGroup.add(actionManager.createExpandAllAction(treeExpander, tree))
        actionGroup.add(actionManager.createCollapseAllAction(treeExpander, tree))
        actionGroup.addSeparator()

        actionGroup.add(ArendErrorTreeAutoScrollToSource(project, tree).createToggleAction())
        actionGroup.add(autoScrollFromSource.createActionGroup())
        actionGroup.addSeparator()
        actionGroup.add(ArendMessagesFilterActionGroup(project, autoScrollFromSource))
        actionGroup.addSeparator()
        actionGroup.add(ArendShowErrorsPanelAction())

        val toolbar = ActionManager.getInstance().createActionToolbar("ArendMessagesView.toolbar", actionGroup, false)
        toolbar.targetComponent = treePanel
        treePanel.toolbar = toolbar.component
        treePanel.setContent(ScrollPaneFactory.createScrollPane(tree, true))

        goalsErrorsSplitter.apply {
            firstComponent = SingleHeightTabs(project, toolWindow.disposable).apply {
                addTab(goalsTabInfo)
            }
            secondComponent = secondTabs.apply {
                addTab(errorTabInfo)
                addTab(infoTabInfo)
            }
            val isShowErrorsOrInfoPanel = project.service<ArendMessagesService>().isShowErrorsOrInfoPanel
            secondComponent.isVisible = isShowErrorsOrInfoPanel.get()
            isShowErrorsOrInfoPanel.afterSet {
                secondComponent.isVisible = true
                updateEditors()
            }
            isShowErrorsOrInfoPanel.afterReset { secondComponent.isVisible = false }
        }

        project.service<ArendMessagesService>().isShowImplicitGoals.afterChange { updateEditors() }
        project.service<ArendMessagesService>().isShowGoalsInErrorsPanel.afterChange { updateEditors() }

        EditorFactory.getInstance().eventMulticaster.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                if (event.editor.project != project) return
                val documentManager = PsiDocumentManager.getInstance(project)
                val isUncommitted = documentManager.isUncommited(event.editor.document)
                INFO_VIEW_LOG.debug {
                    val isSelected = event.editor == FileEditorManager.getInstance(project).selectedTextEditor
                    "caret: ${event.editor.virtualFile?.name} ${event.oldPosition} -> ${event.newPosition}, selected editor: $isSelected, uncommitted: $isUncommitted"
                }
                if (isUncommitted) {
                    // The caret moves because of typing, and the PSI does not have the text at the caret yet.
                    // When the caret is on a comment, the document listener updates the panel.
                    if (!isCursorOnComment) {
                        documentManager.performLaterWhenAllCommitted {
                            updateEditors(canClear = false, changeText = true, showNotification = false)
                        }
                    }
                    return
                }
                updateEditors(canClear = false, showNotification = false)
            }
        }, project)

        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (isCursorOnComment) {
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor
                    INFO_VIEW_LOG.debug { "document changed on a comment, in the selected editor: ${editor?.document == event.document}" }
                    if (editor != null && editor.document == event.document) {
                        PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted {
                            updateEditors(canClear = false, changeText = true, showNotification = false)
                        }
                    }
                }
            }
        }, project)

        project.messageBus.connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                INFO_VIEW_LOG.debug { "editor selection: ${event.oldFile?.name} -> ${event.newFile?.name}" }
                updateEditors(canClear = false)
            }
        })

        update()
    }

    private fun setErrorPanel() {
        secondTabs.select(errorTabInfo, false)
    }

    private fun setInfoPanel() {
        secondTabs.select(infoTabInfo, false)
    }

    private fun setupTreeDetailsSplitter(vertical: Boolean) {
        treeDetailsSplitter.orientation = vertical
        treeDetailsSplitter.proportion = if (vertical) 0.75f else 0.25f
        // We first set components to null to clear the splitter. Without that, it might not be repainted properly.
        treeDetailsSplitter.firstComponent = null
        treeDetailsSplitter.secondComponent = null
        treeDetailsSplitter.firstComponent = if (vertical) goalsErrorsSplitter else treePanel
        treeDetailsSplitter.secondComponent = if (vertical) treePanel else goalsErrorsSplitter
    }

    private fun updateOrientation(toolWindow: ToolWindow) {
        if (toolWindow.id == ArendMessagesFactory.TOOL_WINDOW_ID) {
            val isVertical = !toolWindow.anchor.isHorizontal
            if (treeDetailsSplitter.isVertical != isVertical) {
                goalsErrorsSplitter.orientation = isVertical
                setupTreeDetailsSplitter(isVertical)
            }
        }
    }

    override fun projectClosing(project: Project) {
        if (project == this.project) {
            root.removeAllChildren()
            goalsPanel.removeAll()
            errorsPanel.removeAll()
            goalEditor?.release()
            goalEditor = null
            errorEditor?.release()
            errorEditor = null
        }
    }

    override fun valueChanged(e: TreeSelectionEvent?) = updateEditors(updateCursor = false)

    private fun enclosingComment(element: PsiElement): PsiComment? =
        element.ancestor<ArendDocComment>() ?: (element as? PsiComment)?.takeIf { it.tokenType == LINE_COMMENT || it.tokenType == BLOCK_COMMENT }

    private fun updateCursor(canClear: Boolean, showNotification: Boolean, changeText: Boolean) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: run {
            INFO_VIEW_LOG.debug("cursor: no selected text editor")
            return
        }
        val document = editor.document
        val offset = editor.caretModel.offset
        val file = PsiDocumentManager.getInstance(project).getPsiFile(document) as? ArendFile ?: run {
            INFO_VIEW_LOG.debug { "cursor: ${editor.virtualFile?.name} is not an Arend file" }
            isCursorOnError = false
            isCursorOnComment = false
            return
        }
        val element = file.findElementAt(offset)
        val comment = element?.let { enclosingComment(it) }
            // The caret right after a comment, where it is when the comment has just been typed, is on the comment too
            ?: if (offset > 0 && (element == null || element is PsiWhiteSpace)) file.findElementAt(offset - 1)?.let { enclosingComment(it) } else null
        INFO_VIEW_LOG.debug {
            val owner = if (comment is ArendDocComment) ", documents ${comment.owner?.refName}" else ""
            "cursor: offset $offset of ${file.name}, element ${element.traceText}, comment ${comment.traceText}$owner"
        }
        if (element == null && comment == null) {
            isCursorOnError = false
            isCursorOnComment = false
            return
        }
        if (comment != null) {
            if (infoEditor == null) {
                infoEditor = ArendInfoViewEditor(project)
            }
            val showNotificationInfo = (!changeText || lastDocCorrectness) && (showNotification || lastComment != comment)
            infoEditor!!.updateHtml(Pair(comment, null), showNotificationInfo)
            lastComment = comment

            isCursorOnError = false
            isCursorOnComment = true
        } else {
            isCursorOnComment = false
            ApplicationManager.getApplication().executeOnPooledThread {
                val moduleLocation = runReadAction { file.moduleLocation }
                val error = project.service<ArendServerService>().server.errorMap[moduleLocation]
                    ?.find { (it.cause as? PsiElement)?.textRange?.containsOffset(offset) == true }
                    ?: return@executeOnPooledThread

                runInEdt {
                    val newEditor = FileEditorManager.getInstance(project).selectedTextEditor
                    val newOffset = newEditor?.caretModel?.offset
                    if (newEditor != editor || newOffset != offset) return@runInEdt

                    isCursorOnError = true
                    tree.select(error)
                    updateEditors(canClear, updateCursor = false)
                }
            }
        }
    }

    fun updateEditors(canClear: Boolean = true, updateCursor: Boolean = true, showNotification: Boolean = true, changeText: Boolean = false) {
        if (updateCursor) {
            runReadAction {
                updateCursor(canClear, showNotification, changeText)
            }
        }
        val treeElement = getSelectedMessage()
        INFO_VIEW_LOG.debug { "editors: on a comment: $isCursorOnComment, on an error: $isCursorOnError, errors/info panel shown: ${isShowErrorsOrInfoPanel()}, canClear: $canClear" }
        if (isShowErrorsOrInfoPanel() && isCursorOnComment) {
            if (infoEditor == null) {
                infoEditor = ArendInfoViewEditor(project)
            }
            setInfoPanel()
            updateActionGroup(infoEditor!!)
            updatePanel(infoPanel, infoEditor?.component ?: infoEmptyPanel)
        } else if (treeElement != null && isCursorOnError) {
            if (isGoal(treeElement) && !isGoalTextPinned()) {
                if (goalEditor == null) {
                    goalEditor = ArendMessagesViewEditor(project, treeElement, EditorType.GOAL)
                }
                if (!isImplicitGoal(treeElement) || isShowImplicitGoals()) {
                    updateEditor(goalEditor!!, treeElement)
                } else {
                    removeNotActionToolbars(goalEditor!!)
                }
                updateActionGroup(goalEditor!!)
                updateGoalsView(goalEditor?.component ?: goalEmptyPanel)
            }
            if (isShowErrorsOrInfoPanel() && !isErrorTextPinned() && (!isGoal(treeElement) || isShowGoalsInErrorsPanel())) {
                if (errorEditor == null) {
                    errorEditor = ArendMessagesViewEditor(project, treeElement, EditorType.ERROR)
                }
                setErrorPanel()
                updateActionGroup(errorEditor!!)
                updateEditor(errorEditor!!, treeElement)
                updatePanel(errorsPanel, errorEditor?.component ?: errorEmptyPanel)
            }
        } else if (canClear) {
            ApplicationManager.getApplication().executeOnPooledThread {
                runReadAction {
                    if (!isGoalTextPinned()) {
                        val currentGoal = goalEditor?.treeElement?.sampleError
                        if (currentGoal != null && isParentDefinitionPsiInvalid(currentGoal)) {
                            goalEditor?.clear()
                            updateGoalsView(goalEmptyPanel)
                        }
                    }
                    if (isShowErrorsOrInfoPanel() && !isErrorTextPinned()) {
                        val currentError = errorEditor?.treeElement?.sampleError
                        if (currentError != null && isParentDefinitionRemovedFromTree(currentError)) {
                            errorEditor?.clear()
                            updatePanel(errorsPanel, errorEmptyPanel)
                        }
                    }
                    val isValidDocComment = lastComment?.isValid
                    if (isShowErrorsOrInfoPanel() && (isValidDocComment == null || !isValidDocComment)) {
                        INFO_VIEW_LOG.debug { "editors: clearing the info panel, the last comment is ${if (isValidDocComment == null) "not set" else "no longer valid"}" }
                        lastComment = null
                        infoEditor?.clear()
                        updatePanel(infoPanel, infoEmptyPanel)
                    }
                }
            }
        }
    }

    private fun getSelectedMessage(): ArendErrorTreeElement? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? ArendErrorTreeElement

    private fun isImplicitGoal(treeElement: ArendErrorTreeElement?): Boolean {
        val error = treeElement?.highestError ?: return false
        return error.level == GeneralError.Level.GOAL && error.cause !is ArendGoal
    }

    private fun isGoal(treeElement: ArendErrorTreeElement?) =
        treeElement?.highestError?.level == GeneralError.Level.GOAL

    private fun isShowImplicitGoals() = project.service<ArendMessagesService>().isShowImplicitGoals.get()

    private fun isGoalTextPinned() = project.service<ArendMessagesService>().isGoalTextPinned

    private fun isErrorTextPinned() = project.service<ArendMessagesService>().isErrorTextPinned

    private fun isShowErrorsOrInfoPanel() = project.service<ArendMessagesService>().isShowErrorsOrInfoPanel.get()

    private fun isShowGoalsInErrorsPanel() = project.service<ArendMessagesService>().isShowGoalsInErrorsPanel.get()

    private fun updateEditor(editor: ArendMessagesViewEditor, treeElement: ArendErrorTreeElement) {
        if (editor.treeElement != treeElement) {
            for (error in treeElement.errors) {
                configureError(error)
            }
            editor.update(treeElement)
        }
        editor.addEditorComponent()
    }

    private fun updateActionGroup(editor: ArendMessagesViewEditor) {
        editor.updateActionGroup()
    }

    private fun removeNotActionToolbars(editor: ArendMessagesViewEditor) {
        val components = editor.component?.components
        val notActionToolbars = components?.filter { it !is ActionToolbar } ?: listOf()
        editor.removeUnnecessaryComponents(notActionToolbars)
    }

    private fun isParentDefinitionPsiInvalid(error: GeneralError): Boolean {
        val psi = ((error as? LocalError)?.definition as? DataContainer)?.data as? PsiElement
        return psi == null || !psi.isValid
    }

    private fun isParentDefinitionRemovedFromTree(error: GeneralError): Boolean {
        val psi = ((error as? LocalError)?.definition as? DataContainer)?.data as? PsiLocatedReferable
        return psi == null || !tree.containsNode(psi)
    }

    private fun updatePanel(panel: JPanel, component: JComponent) {
        runInEdt {
            panel.removeAll()
            panel.add(component)
            panel.revalidate()
            panel.repaint()
        }
    }

    private fun updateGoalsView(component: JComponent) {
        updatePanel(goalsPanel, component)
        runInEdt { goalsTabInfo.setText(defaultGoalsTabTitle) }
    }

    fun updateGoalText() {
        goalEditor?.updateErrorText()
        if (isGoal(errorEditor?.treeElement)) {
            errorEditor?.updateErrorText()
        }
    }

    fun updateErrorText() {
        val level = errorEditor?.treeElement?.highestError?.level
        if (level != null && level != GeneralError.Level.GOAL) {
            errorEditor?.updateErrorText()
        }
    }

    fun clearGoalEditor() {
        if (goalEditor?.treeElement == getSelectedMessage()) {
            tree.clearSelection()
        }
        goalEditor?.clear()
        updateGoalsView(goalEmptyPanel)
    }

    private fun configureError(error: GeneralError) {
        if (error is MissingClausesError) {
            error.setMaxListSize(service<ArendSettings>().clauseActualLimit)
        }
    }

    fun update() {
        val server = project.service<ArendServerService>().server
        val filterSet = project.service<ArendProjectSettings>().messagesFilterSet
        val errorMap = server.errorMap
        val expandedPaths = TreeUtil.collectExpandedPaths(tree)
        val selectedPath = tree.selectionPath

        val map = HashMap<ArendRef, HashMap<Any?, ArendErrorTreeElement>>()
        tree.update(root) { node ->
            if (node == root) {
                errorMap.keys
            }
            else when (val obj = node.userObject) {
                is ModuleLocation -> {
                    val errors = errorMap[obj]
                    val children = LinkedHashSet<Any>()
                    for (error in errors ?: emptyList()) {
                        if (!error.satisfies(filterSet)) {
                            continue
                        }

                        val ref = (error as? LocalError)?.definition
                        if (ref == null) {
                            children.add(ArendErrorTreeElement(error))
                        } else {
                            children.add(ref)
                            map.computeIfAbsent(ref) { LinkedHashMap() }
                                .computeIfAbsent(error.cause) { ArendErrorTreeElement() }.addError(error)
                        }
                    }
                    children
                }
                is ArendRef -> map[obj]?.values ?: emptySet()
                else -> emptySet()
            }
        }

        treeModel.reload()
        TreeUtil.restoreExpandedPaths(tree, expandedPaths)
        tree.selectionPath = tree.getExistingPrefix(selectedPath)
    }
}