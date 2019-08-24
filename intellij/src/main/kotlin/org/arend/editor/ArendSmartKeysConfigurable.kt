package org.arend.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.Label
import com.intellij.ui.layout.panel
import org.arend.settings.ArendSettings
import javax.swing.JPanel


class ArendSmartKeysConfigurable : UnnamedConfigurable {
    private val arendSettings = service<ArendSettings>()
    private var comboBox: ComboBox<ArendSettings.MatchingCommentStyle>? = null

    override fun isModified() = comboBox?.selectedItem != arendSettings.matchingCommentStyle

    override fun apply() {
        arendSettings.matchingCommentStyle = comboBox?.selectedItem as? ArendSettings.MatchingCommentStyle ?: return
    }

    override fun reset() {
        comboBox?.selectedItem = arendSettings.matchingCommentStyle
    }

    override fun createComponent(): JPanel {
        val combo = ComboBox(arrayOf(
            ArendSettings.MatchingCommentStyle.DO_NOTHING,
            ArendSettings.MatchingCommentStyle.REPLACE_BRACE,
            ArendSettings.MatchingCommentStyle.INSERT_MINUS))
        comboBox = combo

        val panel = panel {
            row(Label("On typing '-' between {}: ")) { combo() }
        }
        panel.border = IdeBorderFactory.createTitledBorder("Arend")
        return panel
    }
}