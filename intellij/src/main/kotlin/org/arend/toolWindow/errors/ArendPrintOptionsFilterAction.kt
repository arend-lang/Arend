package org.arend.toolWindow.errors

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.settings.ArendProjectSettings
import org.arend.ui.console.ArendConsoleService

class ArendPrintOptionsFilterAction(private val project: Project,
                                    private val printOptionKind: PrintOptionKind,
                                    private val flag: PrettyPrinterFlag)
    : ToggleAction(flagToString(flag), null, null), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean = isSelected

    private val isSelected: Boolean
        get() = getFilterSet(project, printOptionKind).contains(flag)

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val printOptionSet = getFilterSet(project, printOptionKind)
        if (printOptionSet.contains(flag) == state) {
            return
        }

        if (state)
            printOptionSet.add(flag)
        else
            printOptionSet.remove(flag)

        when (printOptionKind) {
            PrintOptionKind.CONSOLE_PRINT_OPTIONS -> project.service<ArendConsoleService>().updateText()
            PrintOptionKind.ERROR_PRINT_OPTIONS, PrintOptionKind.GOAL_PRINT_OPTIONS -> project.service<ArendMessagesService>().updateErrorText()
            else -> {}
        }
    }



    companion object {
        fun getFilterSet(project: Project, printOptionsKind: PrintOptionKind) = project.service<ArendProjectSettings>().let {
            when (printOptionsKind) {
                PrintOptionKind.CONSOLE_PRINT_OPTIONS -> it.consolePrintingOptionsFilterSet
                PrintOptionKind.ERROR_PRINT_OPTIONS -> it.errorPrintingOptionsFilterSet
                PrintOptionKind.POPUP_PRINT_OPTIONS -> it.popupPrintingOptionsFilterSet
                PrintOptionKind.REPL_PRINT_OPTIONS -> it.replPrintingOptionsFilterSet
                PrintOptionKind.GOAL_PRINT_OPTIONS -> it.goalPrintingOptionsFilterSet
            }
        }

        fun flagToString(flag: PrettyPrinterFlag): String = when (flag) {
            PrettyPrinterFlag.SHOW_COERCE_DEFINITIONS -> "Show coerce definitions"
            PrettyPrinterFlag.SHOW_CON_PARAMS -> "Show constructor parameters"
            PrettyPrinterFlag.SHOW_TUPLE_TYPE -> "Show tuple type"
            PrettyPrinterFlag.SHOW_FIELD_INSTANCE -> "Show field instances"
            PrettyPrinterFlag.SHOW_IMPLICIT_ARGS -> "Show implicit arguments"
            PrettyPrinterFlag.SHOW_TYPES_IN_LAM -> "Show types in lambda expressions"
            PrettyPrinterFlag.SHOW_PREFIX_PATH -> "Show prefix path"
            PrettyPrinterFlag.SHOW_BIN_OP_IMPLICIT_ARGS -> "Show infix operators' implicit arguments"
            PrettyPrinterFlag.SHOW_CASE_RESULT_TYPE -> "Show result types of case expressions"
            PrettyPrinterFlag.SHOW_LEVELS -> "Show levels"
        }
    }
}

enum class PrintOptionKind(val kindName: String) {
    CONSOLE_PRINT_OPTIONS("Console"),
    GOAL_PRINT_OPTIONS("Goal"),
    POPUP_PRINT_OPTIONS("Pop-up"),
    REPL_PRINT_OPTIONS("REPL"),
    ERROR_PRINT_OPTIONS("Error")
}