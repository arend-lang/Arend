package org.arend.codeInsight.hints

import org.arend.core.definition.Definition

class ArendAxiomsInlayProvider : ArendDefinitionInlayProvider() {
    override fun getText(definition: Definition): String? {
        val axioms = definition.axioms
        return if (axioms.isEmpty() || axioms.size == 1 && axioms.contains(definition)) null else "Axioms: " + axioms.joinToString()
    }
}