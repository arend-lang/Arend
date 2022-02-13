package org.arend.search.proof

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.hint.actions.QuickPreviewAction
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.BigPopupUI
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.stubs.StubIndex
import com.intellij.ui.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.castSafelyTo
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import net.miginfocom.swing.MigLayout
import org.arend.ArendIcons
import org.arend.psi.navigate
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.util.ArendBundle
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.*
import javax.swing.*
import javax.swing.border.Border

class ProofSearchUI(private val project: Project) : BigPopupUI(project) {

    private val searchAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private val model: CollectionListModel<ProofSearchUIEntry> = CollectionListModel()

    private val loadingIcon: JBLabel = JBLabel(EmptyIcon.ICON_16)

    private val previewAction: QuickPreviewAction = QuickPreviewAction()

    private val myEditorTextField: MyEditorTextField = MyEditorTextField().also {
        it.setFontInheritedFromLAF(false)
    }

    @Volatile
    private var progressIndicator: ProgressIndicator? = null
        get() = synchronized(this) {
            field
        }
        set(value) = synchronized(this) {
            field = value
        }

    init {
        init()
        initActions()
    }

    override fun dispose() {
        close()
        model.removeAll()
    }

    override fun createList(): JBList<Any> {
        @Suppress("UNCHECKED_CAST")
        addListDataListener(model as AbstractListModel<Any>)

        return JBList(model)
    }

    override fun createCellRenderer(): ListCellRenderer<Any> {
        @Suppress("UNCHECKED_CAST")
        return ArendProofSearchRenderer() as ListCellRenderer<Any>
    }

    override fun createTopLeftPanel(): JPanel {
        @Suppress("DialogTitleCapitalization")
        val title = JBLabel(ArendBundle.message("arend.proof.search.title"))
        val topPanel = JPanel(MigLayout("flowx, ins 0, gap 0, fillx, filly"))
        val foregroundColor = when {
            StartupUiUtil.isUnderDarcula() -> when {
                UIUtil.isUnderWin10LookAndFeel() -> JBColor.WHITE
                else -> JBColor(Gray._240, Gray._200)
            }
            else -> UIUtil.getLabelForeground()
        }

        title.foreground = foregroundColor
        title.border = BorderFactory.createEmptyBorder(3, 5, 5, 0)
        if (SystemInfo.isMac) {
            title.font = loadingIcon.font.deriveFont(Font.BOLD, title.font.size - 1f)
        } else {
            title.font = loadingIcon.font.deriveFont(Font.BOLD)
        }
        topPanel.add(title, "gapright 4")
        topPanel.add(loadingIcon, "w 24, wmin 24")
        return topPanel
    }

    override fun createSettingsPanel(): JPanel {
        val res = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        res.isOpaque = false

        val actionGroup = DefaultActionGroup()
        actionGroup.addAction(GearActionGroup(this, project))
        actionGroup.addAction(ShowInFindWindowAction(this, project))
        val toolbar = ActionManager.getInstance().createActionToolbar("proof.search.top.toolbar", actionGroup, true)
        toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
        toolbar.setTargetComponent(this)
        val toolbarComponent = toolbar.component
        toolbarComponent.isOpaque = false
        res.add(toolbarComponent)
        return res
    }

    override fun getAccessibleName(): String = ArendBundle.message("arend.proof.search.title")

    // a hack, sorry IJ platform
    override fun init() {
        super.init()
        synchronized(this.treeLock) {
            for (it in components) {
                val field = it.castSafelyTo<JPanel>()?.components?.find { it is ExtendableTextField }
                if (field != null && it is JPanel) {
                    it.castSafelyTo<JPanel>()?.remove(field)
                    it.add(myEditorTextField, BorderLayout.SOUTH)
                }
            }
        }
    }

    override fun installScrollingActions() {
        ScrollingUtil.installActions(myResultsList, myEditorTextField)
    }

    val editorSearchField: EditorTextField
        get() = myEditorTextField

    private inner class MyEditorTextField :
        TextFieldWithAutoCompletion<String>(project, ProofSearchTextCompletionProvider(project), false, "") {

        init {
            val empty: Border = JBUI.Borders.empty(-1, -1, -1, -1)
            val topLine = JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.searchFieldBorderColor(), 0, 0, 0, 0)
            border = JBUI.Borders.merge(empty, topLine, true)
            background = JBUI.CurrentTheme.BigPopup.searchFieldBackground()
            focusTraversalKeysEnabled = false
        }

        override fun getPreferredSize(): Dimension? {
            val size = super.getPreferredSize()
            size.height = Integer.max(JBUIScale.scale(29), size.height)
            return size
        }

        override fun shouldHaveBorder(): Boolean {
            return false
        }
    }

    private class ProofSearchTextCompletionProvider(private val project: Project) :
        TextFieldWithAutoCompletionListProvider<String>(listOf()) {

        override fun createPrefixMatcher(prefix: String): PrefixMatcher {
            return CamelHumpMatcher(prefix, true, true)
        }

        override fun getItems(
            prefix: String?,
            cached: Boolean,
            parameters: CompletionParameters?
        ): Collection<String> {
            if (prefix == null || prefix.length < 3) {
                return emptyList()
            }
            val matcher = CamelHumpMatcher(prefix, true, true)

            return runReadAction {
                val container = ArrayList<String>()
                StubIndex.getInstance().processAllKeys(ArendDefinitionIndex.KEY, project) { name ->
                    if (matcher.prefixMatches(name)) {
                        container.add(name)
                    }
                    true
                }
                container
            }
        }

        override fun getLookupString(item: String): String = item

    }

    private fun initActions() {
        myResultsList.expandableItemsHandler.isEnabled = false

        registerSearchAction()
        registerEscapeAction()
        registerEnterAction()
        registerGoToDefinitionAction()
        registerMouseActions()
    }

    private val keywordAttributes = TextAttributes().apply {
        copyFrom(DefaultLanguageHighlighterColors.KEYWORD.defaultAttributes)
        fontType = Font.BOLD
    }

    private fun registerSearchAction() {
        myEditorTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val shouldSearch = refreshHighlighting()
                if (shouldSearch) {
                    scheduleSearch()
                }
            }
        })
    }

    /**
     * @return if search query is OK
     */
    fun refreshHighlighting(): Boolean {
        val editor = myEditorTextField.editor ?: return false
        val text = myEditorTextField.text
        val markupModel = editor.markupModel
        DocumentMarkupModel.forDocument(editor.document, project, false)?.removeAllHighlighters()

        for (i in text.indices) {
            tryHighlightKeyword(i, text, markupModel, "\\and")
            tryHighlightKeyword(i, text, markupModel, "->")
        }
        val (msg, range) = ProofSearchQuery.fromString(text).castSafelyTo<ParsingResult.Error<ProofSearchQuery>>()
            ?: return true
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .descriptionAndTooltip(msg)
            .textAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)
            .range(range.first, range.last)
            .create()

        UpdateHighlightersUtil.setHighlightersToEditor(project, editor.document, range.first, range.last, listOf(info), null, -1)
        if (text.isNotEmpty()) {
            loadingIcon.setIconWithAlignment(AllIcons.General.Error, SwingConstants.LEFT, SwingConstants.TOP)
            loadingIcon.toolTipText = msg
        } else {
            loadingIcon.icon = null
            loadingIcon.toolTipText = ""
        }
        return false
    }

    private fun tryHighlightKeyword(
        index: Int,
        text: String,
        markupModel: MarkupModel,
        keyword: String,
    ) {
        val size = keyword.length
        if (index + size <= text.length && text.subSequence(index, index + size) == keyword &&
            (index + size == text.length || text[index + size].isWhitespace()) &&
            (index == 0 || text[index - 1].isWhitespace())
        ) {
            markupModel.addRangeHighlighter(
                index, index + size, HighlighterLayer.SYNTAX,
                keywordAttributes, HighlighterTargetArea.EXACT_RANGE
            )
        }
    }

    override fun getSearchPattern(): String {
        return myEditorTextField.text
    }

    private fun registerMouseActions() {
        val mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onMouseClicked(e)
            }
        }
        myResultsList.addMouseListener(mouseListener)
        myResultsList.addMouseMotionListener(mouseListener)
    }

    private fun registerEnterAction() {
        DumbAwareAction.create {
            val lookup = LookupManager.getActiveLookup(editorSearchField.editor)
            if (lookup?.component?.isVisible == true) {
                ChooseItemAction.FocusedOnly().actionPerformed(it)
            } else {
                val indices = myResultsList.selectedIndices
                if (indices.isNotEmpty()) {
                    onEntrySelected(model.getElementAt(indices[0]))
                }
            }
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, this, this)
    }


    private fun registerGoToDefinitionAction() {
        DumbAwareAction.create {
            val indices = myResultsList.selectedIndices
            if (indices.isNotEmpty()) {
                goToDeclaration(model.getElementAt(indices[0]))
            }
        }.registerCustomShortcutSet(CommonShortcuts.getViewSource(), this, this)
    }

    private fun registerEscapeAction() {
        val escapeAction = ActionManager.getInstance().getAction("EditorEscape")
        DumbAwareAction
            .create {
                val lookup = LookupManager.getActiveLookup(editorSearchField.editor)
                if (lookup?.component?.isVisible == true) {
                    lookup.hideLookup(true)
                } else {
                    close()
                }
            }
            .registerCustomShortcutSet(escapeAction?.shortcutSet ?: CommonShortcuts.ESCAPE, this)
    }

    internal fun scheduleSearch() {
        if (!searchAlarm.isDisposed && searchAlarm.activeRequestCount == 0) {
            searchAlarm.addRequest({ runProofSearch(null) }, 100)
        }
    }

    private fun onMouseClicked(e: MouseEvent) {
        if (e.button == MouseEvent.BUTTON1) {
            e.consume()
            val i = myResultsList.locationToIndex(e.point)
            if (i > -1) {
                myResultsList.selectedIndex = i
                onEntrySelected(model.getElementAt(i))
            }
        }
    }

    private fun onEntrySelected(element: ProofSearchUIEntry) = when (element) {
        is DefElement -> {
            previewAction.performForContext({
                when (it) {
                    CommonDataKeys.PROJECT.name -> project
                    CommonDataKeys.PSI_ELEMENT.name -> element.entry.def
                    else -> null
                }
            }, false)
        }
        is MoreElement -> {
            model.remove(element)
            runProofSearch(element.sequence)
        }
    }

    private fun goToDeclaration(element: ProofSearchUIEntry) = when (element) {
        is DefElement -> {
            close()
            element.entry.def.navigationElement.navigate()
        }
        else -> Unit
    }

    fun runProofSearch(results: Sequence<ProofSearchEntry>?) {
        cleanupCurrentResults(results)

        val settings = ProofSearchUISettings(project)

        runBackgroundableTask(ArendBundle.message("arend.proof.search.title"), myProject) { progressIndicator ->
            runWithLoadingIcon {
                this.progressIndicator = progressIndicator
                val elements = results ?: generateProofSearchResults(project, searchPattern)
                var counter = PROOF_SEARCH_RESULT_LIMIT
                for (element in elements) {
                    if (progressIndicator.isCanceled) {
                        break
                    }
                    invokeLater {
                        model.add(DefElement(element))
                        if (results != null && counter == PROOF_SEARCH_RESULT_LIMIT && myResultsList.selectedIndex == -1) {
                            myResultsList.selectedIndex = myResultsList.itemsCount - 1
                        }
                    }
                    --counter
                    if (settings.shouldLimitSearch() && counter == 0) {
                        invokeLater {
                            model.add(MoreElement(elements.drop(PROOF_SEARCH_RESULT_LIMIT)))
                        }
                        break
                    }
                }
            }
        }
    }

    private inline fun runWithLoadingIcon(action: () -> Unit) {
        runInEdt {
            loadingIcon.icon = AnimatedIcon.Default.INSTANCE
            loadingIcon.toolTipText = ArendBundle.message("arend.proof.search.loading.tooltip")
        }
        try {
            action()
        } finally {
            runInEdt {
                loadingIcon.setIconWithAlignment(ArendIcons.CHECKMARK, SwingConstants.LEFT, SwingConstants.TOP)
                loadingIcon.toolTipText = ArendBundle.message("arend.proof.search.completed.tooltip")
            }
        }
    }

    private fun cleanupCurrentResults(results: Sequence<ProofSearchEntry>?) {
        progressIndicator?.cancel()
        if (results == null) {
            invokeLater {
                model.removeAll()
            }
        }
    }

    override fun getInitialHints(): Array<String> = arrayOf(
        ArendBundle.message("arend.proof.search.quick.preview.tip"),
        ArendBundle.message("arend.proof.search.go.to.definition.tip", KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_VIEW_SOURCE))
    )

    fun close() {
        stopSearch()
        searchFinishedHandler.run()
    }

    private fun stopSearch() {
        progressIndicator?.cancel()
    }
}

const val PROOF_SEARCH_RESULT_LIMIT = 20