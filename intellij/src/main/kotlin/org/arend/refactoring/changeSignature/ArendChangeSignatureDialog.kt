package org.arend.refactoring.changeSignature

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.executeCommand
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.descendantsOfType
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.ChangeSignatureDialogBase
import com.intellij.refactoring.ui.CodeFragmentTableCellEditorBase
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.refactoring.ui.StringTableCellEditor
import com.intellij.ui.EditorTextField
import com.intellij.ui.TableUtil
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.TableView
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileType
import org.arend.naming.scope.ListScope
import org.arend.naming.scope.Scope
import org.arend.psi.ext.*
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.resolving.ArendResolveCache
import java.util.Collections.singletonList
import javax.swing.DefaultListSelectionModel
import javax.swing.JPanel
import javax.swing.event.TableModelEvent

class ArendChangeSignatureDialog(project: Project, val descriptor: ArendChangeSignatureDescriptor) :
    ChangeSignatureDialogBase<ArendParameterInfo, PsiElement, String, ArendChangeSignatureDescriptor, ArendChangeSignatureDialogParameterTableModelItem, ArendParameterTableModel>(project, descriptor, false, descriptor.method.context) {
    override fun updatePropagateButtons() {
        super.updatePropagateButtons()
        updateToolbarButtons()
    }

    override fun customizeParametersTable(table: TableView<ArendChangeSignatureDialogParameterTableModelItem>?) {
        super.customizeParametersTable(table)
    }

    private var parametersPanel: JPanel? = null
    override fun getFileType() = ArendFileType

    override fun createParametersInfoModel(descriptor: ArendChangeSignatureDescriptor) =
        ArendParameterTableModel( descriptor, this, {item: ArendChangeSignatureDialogParameterTableModelItem -> getParametersScope(item)}, myDefaultValueContext)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor =
        ArendChangeSignatureProcessor(project, evaluateChangeInfo(myParametersTableModel))

    override fun createReturnTypeCodeFragment(): PsiCodeFragment {
        val referable = myMethod.method
        val returnExpression = when (referable) {
            is ArendDefFunction -> referable.returnExpr?.text ?: ""
            else -> ""
        }
        return ArendChangeSignatureDialogCodeFragment(myProject, returnExpression, getParametersScope(null), referable)
    }

    override fun createCallerChooser(title: String?, treeToReuse: Tree?, callback: Consumer<in MutableSet<PsiElement>>?) = null

    // TODO: add information about errors
    override fun validateAndCommitData(): String? = null

    private fun evaluateChangeInfo(parametersModel: ArendParameterTableModel): ArendChangeInfo {
        return ArendChangeInfo(parametersModel.items.map {  it.parameter }.toMutableList(), myReturnTypeCodeFragment?.text, myMethod.method)
    }

    override fun calculateSignature(): String =
        evaluateChangeInfo(myParametersTableModel).signature()

    override fun createVisibilityControl() = object : ComboBoxVisibilityPanel<String>("", arrayOf()) {}

    override fun createParametersPanel(hasTabsInDialog: Boolean): JPanel {
        val result = super.createParametersPanel(hasTabsInDialog)
        parametersPanel = result
        for (i in 0 until myParametersTable.items.size) invokeTypeHighlighting(i)

        val selectionModel = (this.myParametersTable.selectionModel as DefaultListSelectionModel)
        val oldSelectionListeners = selectionModel.listSelectionListeners
        val oldModelListeners = this.myParametersTableModel.tableModelListeners

        for (l in oldSelectionListeners) selectionModel.removeListSelectionListener(l)
        for (l in oldModelListeners) this.myParametersTableModel.removeTableModelListener(l)

        this.myParametersTable.selectionModel.addListSelectionListener { ev ->
            for (l in oldSelectionListeners) l.valueChanged(ev)
            updateToolbarButtons()
        }

        this.myParametersTableModel.addTableModelListener { ev ->
            for (l in oldModelListeners) l.tableChanged(ev)
            if (ev.type == TableModelEvent.DELETE) {
                project.service<ArendPsiChangeService>().modificationTracker.incModificationCount()
                for (j in ev.lastRow until this.myParametersTable.items.size) invokeTypeHighlighting(j)
                invokeTypeHighlighting(-1)
            }
            updateToolbarButtons()
        }
        return result
    }



    private fun getParametersScope(item: ArendChangeSignatureDialogParameterTableModelItem?): () -> Scope = { ->
        val items = this.myParametersTableModel.items
        val limit = items.indexOfFirst { it == item }.let { if (it == -1) items.size else it }
        val params = items.take(limit).map { it.associatedReferable }
        ListScope(params)
    }

    private fun getTypeTextField(index: Int) = (this.myParametersTable.getCellEditor(index, 1) as? CodeFragmentTableCellEditorBase?)?.getTableCellEditorComponent(myParametersTable, myParametersTableModel.items[index].typeCodeFragment, false, 0, 0) as? EditorTextField

    //private fun getNameTextField(index: Int) = this.myParametersTable.getCellEditor(index, 0) as? StringTableCellEditor
    fun refactorParameterNames(item: ArendChangeSignatureDialogParameterTableModelItem, newName: String) {
        val index = myParametersTableModel.items.indexOf(item)
        val resolveCache = project.service<ArendResolveCache>()
        for (i in index+1 until myParametersTable.items.size) {
            val codeFragment = myParametersTableModel.items[i].typeCodeFragment
            val affectedRefs = codeFragment.descendantsOfType<ArendReferenceElement>().filter {
                it.referenceName == item.associatedReferable.refName
                //resolveCache.getCached(it) == item.associatedReferable
            }.toList()
            val docManager = PsiDocumentManager.getInstance(project)
            ApplicationManager.getApplication().invokeLater({
                executeCommand {
                    runWriteAction {
                        val textFile = docManager.getDocument(codeFragment)
                        if (textFile != null) {
                            for (ref in affectedRefs) {
                                val range = ref.textRange
                                textFile.replaceString(range.startOffset, range.endOffset, newName)
                            }
                            docManager.commitDocument(textFile)
                        }
                    }
                }
            }, ModalityState.current())

            myParametersTable.updateUI()
        }
    }

    private fun invokeTypeHighlighting(index: Int) {
        val fragment = if (index == -1) this.myReturnTypeCodeFragment else myParametersTableModel.items[index].typeCodeFragment
        //val editorTextField = if (index == -1) this.myReturnTypeField else getTypeTextField(index)
        val codeAnalyzer = DaemonCodeAnalyzer.getInstance(project) as? DaemonCodeAnalyzerImpl
        //val document = fragment?.let{ PsiDocumentManager.getInstance(project).getDocument(it) }
        if (fragment != null && codeAnalyzer != null /*&& editorTextField != null && document != null */) {
            codeAnalyzer.restart(fragment)
            //val textEditor = editorTextField.editor?.let{ TextEditorProvider.getInstance().getTextEditor(it) }
            //codeAnalyzer.runPasses(fragment, document, singletonList(textEditor), IntArray(0), true, null)
        }
    }

    private fun updateToolbarButtons() {
        val resolveCache = project.service<ArendResolveCache>()
        val parametersPanel = parametersPanel ?: return
        val downButton = ToolbarDecorator.findDownButton(parametersPanel) ?: return
        val upButton = ToolbarDecorator.findUpButton(parametersPanel) ?: return

        val selectedIndices = this.myParametersTable.selectionModel.selectedIndices
        if (selectedIndices.size == 1) {
            val selectedIndex = selectedIndices.first()
            val currentItem = this.myParametersTableModel.items[selectedIndex]
            val dependencyChecker = { pI: ArendChangeSignatureDialogParameterTableModelItem, cI: ArendChangeSignatureDialogParameterTableModelItem ->
                cI.typeCodeFragment.descendantsOfType<ArendReferenceElement>().filter {
                    resolveCache.getCached(it) == pI.associatedReferable
                }.toList().isEmpty()
            }
            if (selectedIndex > 0) {
                upButton.isEnabled = dependencyChecker.invoke(this.myParametersTableModel.items[selectedIndex - 1], currentItem)
            }
            if (selectedIndex < this.myParametersTableModel.items.size - 1) {
                downButton.isEnabled = dependencyChecker.invoke(currentItem, this.myParametersTableModel.items[selectedIndex + 1])
            }
        }
    }
}