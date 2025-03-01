package org.arend.refactoring

import com.intellij.openapi.application.runReadAction
import org.arend.ext.module.LongName
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.reference.ArendRef
import org.arend.ext.reference.DataContainer
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable

class PsiLocatedRenamer(private val element: ArendCompositeElement, private val file: ArendFile = element.containingFile as ArendFile) : DefinitionRenamer {
    private val deferredNsCmdActions = ArrayList<NsCmdRefactoringAction>()

    override fun renameDefinition(arendRef: ArendRef): LongName? = runReadAction {
        val ref = (arendRef as? DataContainer)?.data as? PsiLocatedReferable ?: return@runReadAction null
        val locationData = LocationData.createLocationData(ref) ?: return@runReadAction null
        val pair = calculateReferenceName(locationData, file, element, deferredNsCmdActions) ?: return@runReadAction null
        val action = pair.first
        if (action != null) deferredNsCmdActions.add(action)
        LongName(pair.second)
    }

    fun writeAllImportCommands() {
        for (l in deferredNsCmdActions) l.execute()
        deferredNsCmdActions.clear()
    }
}