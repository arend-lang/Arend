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

        fun getProposedFix(target: PsiLocatedReferable, anchor: ArendReferenceElement): ResolveReferenceAction? {
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

          val fix = arendServer.makeReferencesAvailable(singletonList(targetReferable), concreteGroup, rawAnchor, errorReporter)
          val name = fix.proj2.firstOrNull() ?: return null

          return ResolveReferenceAction(target, name.toList(), NsCmdRawModifierAction(fix.proj1, anchorFile), RenameReferenceAction(anchor, name.toList(), target))
        }

        fun getTargetName(target: PsiLocatedReferable, element: ArendCompositeElement, deferredImports: List<NsCmdRefactoringAction>? = null): Pair<String, NsCmdRefactoringAction?> {
            val containingFile = element.containingFile as? ArendFile ?: return Pair("", null)
            val location = LocationData.createLocationData(target)
            if (location != null) {
                val (importAction, resultName) = doCalculateReferenceName(location, containingFile, element, deferredImports = deferredImports)
                return Pair(LongName(resultName.ifEmpty { listOf(target.name) }).toString(), importAction)
            }

            return Pair("", null)
        }
    }
}