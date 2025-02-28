package org.arend.typechecking.execution

import com.intellij.execution.lineMarker.ExecutorAction
import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.core.definition.Definition.TypeCheckingStatus
import org.arend.core.definition.Definition.TypeCheckingStatus.*
import org.arend.naming.reference.MetaReferable
import org.arend.psi.ArendElementTypes
import org.arend.psi.ext.*
import org.arend.scratch.SCRATCH_SUFFIX

class TypeCheckRunLineMarkerContributor : RunLineMarkerContributor() {
    // Store previous definition status to prevent flickering during resolving
    private val statusKey: Key<TypeCheckingStatus> = Key.create("AREND_DEFINITION_STATUS")

    override fun getInfo(element: PsiElement): Info? {
        if (!(element is LeafPsiElement && element.node.elementType == ArendElementTypes.ID) ||
                element.containingFile.virtualFile.extension == SCRATCH_SUFFIX) {
            return null
        }

        val parent = when (val id = element.parent) {
            is ArendDefIdentifier -> id.parent as? ArendDefinition<*>
            is ArendRefIdentifier -> ((id.parent as? ArendLongName)?.parent as? ArendCoClause)?.functionReference
            else -> null
        } ?: return null

        val ref = parent.tcReferable
        if (ref is MetaReferable) return null
        val status = if (ref == null) {
            parent.getUserData(statusKey)
        } else {
            val status = ref.typechecked?.status()
            if (status != null) {
                parent.putUserData(statusKey, status)
            }
            status
        }

        val icon = when (status) {
            NO_ERRORS -> AllIcons.RunConfigurations.TestState.Green2
            HAS_WARNINGS -> AllIcons.RunConfigurations.TestState.Yellow2
            null, TYPE_CHECKING, NEEDS_TYPE_CHECKING -> AllIcons.RunConfigurations.TestState.Run
            else -> AllIcons.RunConfigurations.TestState.Red2
        }

        return Info(icon, ExecutorAction.getActions(1)) {
            runReadAction {
                if (parent.isValid) "Typecheck ${parent.fullNameText}" else "Typecheck definition"
            }
        }
    }
}
