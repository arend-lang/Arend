package org.arend.typechecking.execution

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.ComboboxSpeedSearch
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import org.arend.ArendFileTypeInstance
import org.arend.ArendIcons
import org.arend.refactoring.move.ArendMoveMembersDialog
import org.arend.refactoring.move.ArendMoveMembersDialog.Companion.simpleLocate
import org.arend.typechecking.execution.configurations.TypeCheckConfiguration
import org.arend.util.aligned
import org.arend.util.arendModules
import javax.swing.JComponent
import javax.swing.JList
import com.intellij.openapi.module.Module
import org.arend.psi.fragments.ArendFileChooserFragment
import org.arend.psi.fragments.ArendReferableChooserFragment

class TypeCheckRunConfigurationEditor(project: Project) : SettingsEditor<TypeCheckConfiguration>() {
    private val libraryComponent = ArendModuleComboBox(project.arendModules)
    private val isTestComponent = JBCheckBox()
    private val modulePathComponent: EditorTextField
    private val definitionNameComponent: EditorTextField

    init {
        val fileChooserFragment = object: ArendFileChooserFragment(project, MODULE_TEXT) {
            override fun getContextModule(): Module? = libraryComponent.getSelectedModule()

            override fun isInTests(): Boolean = isTestComponent.isSelected
        }
        val groupFragment = object: ArendReferableChooserFragment(project, DEFINITION_TEXT) {
            override fun getReferable(): PsiElement? {
                val module = libraryComponent.getSelectedModule() ?: return null
                val (lr, result) = simpleLocate(fileChooserFragment.text, "", module)
                return if (result == ArendMoveMembersDialog.LocateResult.LOCATE_OK) lr else null
                return null
            }
        }
        modulePathComponent = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(fileChooserFragment), project, ArendFileTypeInstance)
        definitionNameComponent = EditorTextField(PsiDocumentManager.getInstance(project).getDocument(groupFragment), project, ArendFileTypeInstance)
    }

    override fun resetEditorFrom(configuration: TypeCheckConfiguration) {
        with(configuration.arendTypeCheckCommand) {
            libraryComponent.selectedItem = configuration.project.arendModules.firstOrNull { it.name == library }
            isTestComponent.isSelected = isTest
            modulePathComponent.text = modulePath
            definitionNameComponent.text = definitionFullName
        }
    }

    override fun applyEditorTo(configuration: TypeCheckConfiguration) {
        configuration.arendTypeCheckCommand = TypeCheckCommand(
            libraryComponent.getText(),
            isTestComponent.isSelected,
            modulePathComponent.text,
            definitionNameComponent.text
        )
    }

    override fun createEditor(): JComponent = panel {
        aligned("$LIBRARY_TEXT:", libraryComponent)
        aligned("$IS_TEST_TEXT:", isTestComponent)
        aligned("$MODULE_TEXT:", modulePathComponent)
        aligned("$DEFINITION_TEXT:", definitionNameComponent)
    }

    companion object {
        private const val LIBRARY_TEXT = "Arend library"
        private const val IS_TEST_TEXT = "Search in the test directory"
        private const val MODULE_TEXT = "Arend module"
        private const val DEFINITION_TEXT = "Definition"

        class ArendModuleComboBox(arendModules: Collection<Module>) : ComboBox<Module>(arendModules.toTypedArray()) {
            init {
                // Set custom renderer to show module icon and name
                renderer = object : ColoredListCellRenderer<Module>() {
                    override fun customizeCellRenderer(
                        list: JList<out Module>,
                        value: Module?,
                        index: Int,
                        selected: Boolean,
                        hasFocus: Boolean
                    ) {
                        if (value != null) {
                            icon = ArendIcons.AREND
                            append(value.name)
                        }
                    }
                }
                
                ComboboxSpeedSearch.installSpeedSearch(this) { it.name }
            }

            fun getSelectedModule(): Module? {
                return selectedItem as? Module
            }

            fun getText() = getSelectedModule()?.name ?: ""
        }
    }
}
