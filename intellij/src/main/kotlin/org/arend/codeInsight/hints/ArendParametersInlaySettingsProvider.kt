package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.declarative.InlayHintsCustomSettingsProvider
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class ArendParametersInlaySettingsProvider : InlayHintsCustomSettingsProvider<ArendParametersInlaySettingsProvider.Settings> {
    data class Settings(var showTypes: Boolean = false)

    private var showTypesBox = JBCheckBox("Show types")

    override fun createComponent(
        project: Project,
        language: Language
    ): JComponent {
        showTypesBox.addChangeListener {
            SHOW_TYPE_SETTINGS.showTypes = showTypesBox.isSelected
        }
        return panel {
            row { cell(showTypesBox) }
        }
    }

    override fun isDifferentFrom(
        project: Project,
        settings: Settings
    ): Boolean {
        return SHOW_TYPE_SETTINGS.showTypes != settings.showTypes
    }

    override fun getSettingsCopy(): Settings {
        return SHOW_TYPE_SETTINGS
    }

    override fun putSettings(
        project: Project,
        settings: Settings,
        language: Language
    ) {
        SHOW_TYPE_SETTINGS = settings
    }

    override fun persistSettings(
        project: Project,
        settings: Settings,
        language: Language
    ) {
        SHOW_TYPE_SETTINGS.showTypes = settings.showTypes
    }

    companion object {
        var SHOW_TYPE_SETTINGS = Settings()
    }
}