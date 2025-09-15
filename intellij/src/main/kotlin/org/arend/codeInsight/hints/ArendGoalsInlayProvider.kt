package org.arend.codeInsight.hints

import org.arend.core.definition.Definition

class ArendGoalsInlayProvider : ArendDefinitionInlayProvider() {
    override fun getText(definition: Definition): String? {
        val goals = HashSet(definition.goals)
        goals.remove(definition)
        return if (goals.isEmpty()) null else "Goals: " + goals.map { it.ref.refLongName }.joinToString()
    }
}