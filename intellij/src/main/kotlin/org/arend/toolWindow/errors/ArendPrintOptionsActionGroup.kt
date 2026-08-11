package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ArendIcons
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.injection.InjectedArendEditor

class ArendPrintOptionsActionGroup(
    project: Project,
    kind: PrintOptionKind,
    callback: Runnable? = null
) :
    DefaultActionGroup("${kind.kindName}'s pretty printer options", true), DumbAware {
    private var actionMap = HashMap<PrettyPrinterFlag, ArendPrintOptionsFilterAction>()

    init {
        templatePresentation.icon = ArendIcons.SHOW
        for (type in PrettyPrinterFlag.values()) {
            val action = ArendPrintOptionsFilterAction(project, kind, type, callback)
            add(action)
            actionMap[type] = action
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val injectedEditor = editor?.getUserData(InjectedArendEditor.AREND_GOAL_EDITOR)
        if (injectedEditor != null) {
            e.presentation.isEnabledAndVisible = injectedEditor.treeElement?.errors?.any { it.hasExpressions() } ?: false
        }
    }
}