package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.FullModuleReferable
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.scope.NamespaceCommandNamespace
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendNsId
import org.arend.psi.ext.ArendNsUsing
import org.arend.psi.ext.ArendStatCmd
import org.arend.psi.ext.ReferableBase
import org.arend.refactoring.doAddIdToUsing
import org.arend.refactoring.doRemoveRefFromStatCmd
import org.arend.server.ArendServerService
import org.arend.term.group.ConcreteNamespaceCommand
import org.arend.util.ArendBundle

class RenameDuplicateNameQuickFix(private val cause: Any,
                                  private val currentNsCmd : ConcreteNamespaceCommand,
                                  private val referable: Referable?) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true //causeRef.element != null

    override fun getText(): String = ArendBundle.message("arend.import.rename")

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        when (cause) {
            is ConcreteNamespaceCommand.NameRenaming -> {
                val nsId = cause.data() as? ArendNsId
                val using = nsId?.parent
                val statCmd = using?.parent
                if (using is ArendNsUsing && statCmd is ArendStatCmd) {
                    val oldName = nsId.oldReference.textRepresentation()
                    val newName = nsId.newName
                    doRemoveRefFromStatCmd(nsId.refIdentifier, false)
                    doRenameDuplicateName(currentNsCmd, referable, oldName, newName)
                }
            }
            is ConcreteNamespaceCommand -> {
                if (referable != null) {
                    val statCmd = cause.data() as? ArendStatCmd
                    if (statCmd != null) doRenameDuplicateName(cause, referable, referable.textRepresentation(), null)
                }
            }
        }
    }

    companion object {
        fun doRenameDuplicateName(cnc: ConcreteNamespaceCommand, ref: Referable?, oldName: String, newName: String?) {
            val statCmd = cnc.data() as? ArendStatCmd ?: return
            val arendServer = statCmd.project.service<ArendServerService>().server
            val containingGroup = statCmd.ancestor<ReferableBase<*>>()
            val referable = containingGroup?.tcReferable ?: FullModuleReferable((statCmd.containingFile as? ArendFile)?.moduleLocation ?: return)
            val statCmdScope = NamespaceCommandNamespace.resolveNamespace(arendServer.getReferableScope(referable), cnc)

            val referablesInScope = statCmdScope.getElements(null).map { VariableImpl(it.textRepresentation()) }
            val variable = Variable { newName ?: oldName }
            val freshName = StringRenamer().generateFreshName(variable, referablesInScope)
            val renamings = ArrayList<Pair<String, String?>>(); renamings.add(Pair(oldName, freshName))
            doAddIdToUsing(statCmd, ref, renamings)
        }
    }
}