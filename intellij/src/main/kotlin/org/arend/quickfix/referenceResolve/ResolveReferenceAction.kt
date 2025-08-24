package org.arend.quickfix.referenceResolve

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.module.LongName
import org.arend.naming.reference.DataModuleReferable
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
import org.arend.server.impl.MultiFileReferenceResolver
import org.arend.server.modifier.RawModifier
import org.arend.server.modifier.RawSequenceModifier
import org.arend.term.group.AccessModifier
import org.arend.toolWindow.repl.ArendReplService
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
            if (!target.isValid) return false
            val containingFile = element.containingFile as? ArendFile ?: return false
            if (target.accessModifier == AccessModifier.PRIVATE) return false
            return isVisible(target.containingFile as ArendFile, containingFile)
        }

        fun getTargetLongName(anchorReferenceElement: PsiElement, multiFileReferenceResolver: MultiFileReferenceResolver, targetReferable: PsiLocatedReferable): LongName? {
            val anchorModuleLocation = (anchorReferenceElement.containingFile as? ArendFile)?.moduleLocation ?: return null
            val singleFileResolver = multiFileReferenceResolver.getFileResolver(anchorModuleLocation)
            val anchorReferable: LocatedReferable = (anchorReferenceElement.ancestor<ReferableBase<*>>())?.tcReferable
                ?: FullModuleReferable(anchorModuleLocation)

            return when (targetReferable) {
                is ReferableBase<*> ->  targetReferable.tcReferable?.let { tcReferable ->
                    val result = singleFileResolver.calculateLongName(tcReferable, RawAnchor(anchorReferable, anchorReferenceElement))
                    result
                }

                is ArendFile -> targetReferable.moduleLocation?.let { LongName(it.modulePath.toList()) }
                else -> null
            }
        }

         fun fixLongName(anchorReferenceElement: ArendReferenceElement, multiFileReferenceResolver: MultiFileReferenceResolver, targetReferable: PsiLocatedReferable,
                         sink: MutableList<RenameReferenceAction>) {
            val name = getTargetLongName(anchorReferenceElement, multiFileReferenceResolver, targetReferable) ?: return
            sink.add(RenameReferenceAction(anchorReferenceElement, name.toList(), targetReferable))
        }

        fun flushNamespaceCommands(multiFileReferenceResolver: MultiFileReferenceResolver) {
            for (modifier in multiFileReferenceResolver.multiResolverMap.values ) {
                val file = (modifier?.currentFile?.referable as? DataModuleReferable)?.data
                val m = modifier.modifier
                if (file is ArendFile && m != null)
                    NsCmdRawModifierAction(m, file).execute()
                modifier.reset()
            }

        }


        private fun doGetProposedFix(target: PsiLocatedReferable, anchor: ArendCompositeElement): org.arend.ext.util.Pair<RawModifier, List<LongName>>? {
            val project = target.project
            val targetFile : ArendFile = (target.containingFile as? ArendFile) ?: return null
            val targetFileLocation = targetFile.moduleLocation ?: return null

            val errorReporter = ListErrorReporter()
            val arendServer = project.service<ArendServerService>().server

            val referableBase = anchor.ancestor<ReferableBase<*>>()
            val anchorFile = anchor.containingFile as? ArendFile ?: return null

            ArendServerRequesterImpl(project).doUpdateModule(arendServer, targetFileLocation, targetFile)

            val anchorReferable: LocatedReferable = (if (anchorFile.isRepl) project.service<ArendReplService>().getRepl()?.moduleReferable else referableBase?.tcReferable ?: anchorFile.moduleLocation?.let { FullModuleReferable(it) }) ?: return null
            val rawAnchor = RawAnchor(anchorReferable, anchor)
            val targetReferable : LocatedReferable = (target as? ReferableBase<*>)?.tcReferable ?: return null
            val concreteGroup = anchorFile.moduleLocation?.let { arendServer.getRawGroup(it) }

            return arendServer.makeReferencesAvailable(singletonList(targetReferable), concreteGroup, rawAnchor, errorReporter)
        }

        @Deprecated("Use fixLongName instead") fun getProposedFix(target: PsiLocatedReferable, anchor: ArendReferenceElement): ResolveReferenceAction? {
            if (!target.isValid) return null
            val anchorFile = anchor.containingFile as? ArendFile ?: return null
            val fix: org.arend.ext.util.Pair<RawModifier, List<LongName>>? = doGetProposedFix(target, anchor) ?: return null
            val name = fix?.proj2?.firstOrNull() ?: return null
            return ResolveReferenceAction(target, name.toList(), NsCmdRawModifierAction(fix.proj1, anchorFile), RenameReferenceAction(anchor, name.toList(), target))
        }

        @Deprecated("Use getTargetLongName instead") fun getTargetName(target: PsiLocatedReferable, element: ArendCompositeElement): Pair<String, NsCmdRefactoringAction?>? {
            if (!target.isValid) return null
            val anchorFile = element.containingFile as? ArendFile ?: return null
            val fix: org.arend.ext.util.Pair<RawModifier, List<LongName>>? = doGetProposedFix(target, element) ?: return null
            val name = fix?.proj2?.firstOrNull()?.toString() ?: return null
            val action = fix.proj1.let { nsFix -> if (nsFix is RawSequenceModifier && nsFix.sequence.isEmpty()) null else
                if (nsFix is RawSequenceModifier && nsFix.sequence.size == 1) NsCmdRawModifierAction(nsFix.sequence.first(), anchorFile) else
                    NsCmdRawModifierAction(nsFix, anchorFile) }

            return Pair(name, action)
        }
    }
}