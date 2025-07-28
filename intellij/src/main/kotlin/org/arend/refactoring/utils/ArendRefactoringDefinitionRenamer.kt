package org.arend.refactoring.utils

import org.arend.ext.error.ErrorReporter
import org.arend.ext.module.LongName
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.reference.ArendRef
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ArendFile
import org.arend.refactoring.NsCmdRawModifierAction
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.server.ArendServer
import org.arend.server.RawAnchor
import org.arend.server.impl.MultipleReferenceResolver

class ServerBasedDefinitionRenamer(arendServer: ArendServer,
                                   errorReporter: ErrorReporter,
                                   anchorReferable: LocatedReferable,
                                   val anchorFile: ArendFile): DefinitionRenamer {
    private val anchorFileLocation = anchorFile.moduleLocation
    private val multipleReferenceResolver = MultipleReferenceResolver(arendServer, errorReporter,
        RawAnchor(anchorReferable, null),
        if (anchorFileLocation != null) arendServer.getRawGroup(anchorFileLocation) else null)

    override fun renameDefinition(ref: ArendRef?): LongName? {
        if (ref !is LocatedReferable) return null

        return multipleReferenceResolver.makeTargetAvailable(ref)
    }

    fun getRawModifier() = multipleReferenceResolver.modifier

    fun getAction(): NsCmdRefactoringAction = NsCmdRawModifierAction(getRawModifier(), anchorFile)
}