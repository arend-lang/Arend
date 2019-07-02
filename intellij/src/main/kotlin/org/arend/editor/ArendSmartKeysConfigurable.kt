package org.arend.editor

import com.intellij.openapi.options.UnnamedConfigurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.layout.panel
import javax.swing.JPanel


class ArendSmartKeysConfigurable : UnnamedConfigurable {
    private var comboBox: ComboBox<ArendOptions.MatchingCommentStyle>? = null

    override fun isModified() = comboBox?.selectedItem != ArendOptions.getInstance().matchingCommentStyle

    override fun apply() {
        ArendOptions.getInstance().matchingCommentStyle = comboBox?.selectedItem as? ArendOptions.MatchingCommentStyle ?: return
    }

    override fun reset() {
        comboBox?.selectedItem = ArendOptions.getInstance().matchingCommentStyle
    }

    override fun createComponent(): JPanel {
        val combo = ComboBox(arrayOf(
            ArendOptions.MatchingCommentStyle.DO_NOTHING,
            ArendOptions.MatchingCommentStyle.REPLACE_BRACE,
            ArendOptions.MatchingCommentStyle.INSERT_MINUS))
        comboBox = combo

        val panel = panel {
            row("On typing '-' between {}: ") { combo() }
        }
        panel.border = IdeBorderFactory.createTitledBorder("Arend")
        return panel
    }
}