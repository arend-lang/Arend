package org.arend.quickfix.referenceResolve

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.module.LongName
import org.arend.naming.reference.FullModuleReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.ReferableBase
import org.arend.refactoring.*
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.server.RawAnchor
import org.arend.server.modifier.RawModifier
import org.arend.server.modifier.RawSequenceModifier
import org.arend.term.group.AccessModifier
import java.util.Collections.singletonList

class ResolveReferenceAction(val target: PsiLocatedReferable,
                             private val targetFullName: List<String>,
                             val statCmdFixAction: NsCmdRefactoringAction?,
                             val nameFixAction: RenameReferenceAction?) {
    private val suffix: String = (target.containingFile as? ArendFile)?.moduleLocation?.toString() ?: "NULL"

    override fun toString(): String {
        val prefix = LongName(targetFullName).toString()
        return if (prefix.isNotEmpty()) "$prefix in $suffix" else suffix
    }

    fun execute(editor: Editor?) {
        statCmdFixAction?.execute()
        nameFixAction?.execute(editor)
    }

    companion object {
        fun checkIfAvailable(target: PsiLocatedReferable, element: ArendReferenceElement): Boolean { // should return true iff getProposedFix with the same arguments returns a nonnull value
            val containingFile = element.containingFile as? ArendFile ?: return false
            if (target.accessModifier == AccessModifier.PRIVATE) return false
            return isVisible(target.containingFile as ArendFile, containingFile)
        }


        private fun doGetProposedFix(target: PsiLocatedReferable, anchor: ArendCompositeElement): org.arend.ext.util.Pair<RawModifier, List<LongName>>? {
            val project = target.project
            val targetFile : ArendFile = (target.containingFile as? ArendFile) ?: return null
            val targetFileLocation = targetFile.moduleLocation ?: return null

            val errorReporter = ListErrorReporter()
            val arendServer = project.service<ArendServerService>().server

            val referableBase = anchor.ancestor<ReferableBase<*>>()
            val anchorFile = anchor.containingFile as? ArendFile ?: return null
            val anchorReferable: LocatedReferable = referableBase?.tcReferable ?: FullModuleReferable(anchorFile.moduleLocation)
            ArendServerRequesterImpl(project).doUpdateModule(arendServer, targetFileLocation, targetFile)

            val rawAnchor = RawAnchor(anchorReferable, anchor)
            val targetReferable : LocatedReferable? = (target as? ReferableBase<*>)?.tcReferable ?:
            return null
            val concreteGroup = arendServer.getRawGroup(anchorFile.moduleLocation ?: return null) ?: return null

            return arendServer.makeReferencesAvailable(singletonList(targetReferable), concreteGroup, rawAnchor, errorReporter)
        }

        fun getProposedFix(target: PsiLocatedReferable, anchor: ArendReferenceElement): ResolveReferenceAction? {
            val anchorFile = anchor.containingFile as? ArendFile ?: return null
            val fix: org.arend.ext.util.Pair<RawModifier, List<LongName>>? = doGetProposedFix(target, anchor) ?: return null
            val name = fix?.proj2?.firstOrNull() ?: return null
            return ResolveReferenceAction(target, name.toList(), NsCmdRawModifierAction(fix.proj1, anchorFile), RenameReferenceAction(anchor, name.toList(), target))
        }

        fun getTargetName(target: PsiLocatedReferable, element: ArendCompositeElement): Pair<String, NsCmdRefactoringAction?>? {
            val anchorFile = element.containingFile as? ArendFile ?: return null
            val fix: org.arend.ext.util.Pair<RawModifier, List<LongName>>? = doGetProposedFix(target, element) ?: return null
            val name = fix?.proj2?.firstOrNull()?.toString() ?: return null
            val action = fix.proj1.let{ nsFix -> if (nsFix is RawSequenceModifier && nsFix.sequence.isEmpty()) null else
                if (nsFix is RawSequenceModifier && nsFix.sequence.size == 1) NsCmdRawModifierAction(nsFix.sequence.first(), anchorFile) else
                    NsCmdRawModifierAction(nsFix, anchorFile) }

            return Pair(name, action)
        }
    }
}