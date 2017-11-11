package org.vclang.typechecking.execution

import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.Label
import com.intellij.ui.layout.LCFlags
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.ui.layout.Row
import com.intellij.ui.layout.panel
import org.vclang.typechecking.execution.configurations.TypeCheckConfiguration
import javax.swing.JComponent
import javax.swing.JTextField

class TypeCheckRunConfigurationEditor(
        private val project: Project
) : SettingsEditor<TypeCheckConfiguration>() {
    // TODO: replace text fields with some structure browser
    private val modulePathComponent = JTextField()
    private val definitionNameComponent = JTextField()

    override fun resetEditorFrom(configuration: TypeCheckConfiguration) {
        with(configuration.vclangTypeCheckCommand) {
            modulePathComponent.text = modulePath
            definitionNameComponent.text = definitionFullName
        }
    }

    override fun applyEditorTo(configuration: TypeCheckConfiguration) {
        configuration.vclangTypeCheckCommand = TypeCheckCommand(
                modulePathComponent.text,
                definitionNameComponent.text
        )
    }

    override fun createEditor(): JComponent = panel {
        labeledRow("Vclang module:", modulePathComponent) {
            modulePathComponent()
        }
        labeledRow("Definition:", definitionNameComponent) {
            definitionNameComponent()
        }
    }

    private fun LayoutBuilder.labeledRow(
            labelText: String,
            component: JComponent,
            init: Row.() -> Unit
    ) {
        val label = Label(labelText)
        label.labelFor = component
        row(label) { init() }
    }
}
