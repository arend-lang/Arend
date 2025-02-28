package org.arend.codeInsight.hints

import com.intellij.codeInsight.hints.NoSettings
import com.intellij.codeInsight.hints.SettingsKey
import org.arend.core.definition.Definition

@Suppress("UnstableApiUsage")
class ArendAxiomsInlayProvider : ArendDefinitionInlayProvider() {
    override val key: SettingsKey<NoSettings>
        get() = SettingsKey("arend.inlays.axioms")

    override val name: String
        get() = "Axioms"

    override val previewText: String
        get() = """
            \axiom axiom : 0 = 0
            
            \func foo => axiom
        """.trimIndent()

    override val description
        get() = "Shows axioms used by a definition"

    override fun getText(definition: Definition): String? {
        val axioms = definition.axioms
        return if (axioms.isEmpty() || axioms.size == 1 && axioms.contains(definition)) null else "Axioms: " + axioms.joinToString()
    }
}