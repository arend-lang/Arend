package org.arend

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import org.arend.core.definition.*
import org.arend.ext.core.definition.CoreFunctionDefinition
import org.arend.ext.error.GeneralError
import javax.swing.Icon

object ArendIcons {
    private fun getIcon(path: String) = IconLoader.getIcon(path, ArendIcons::class.java)

    val AREND = getIcon("/icons/arend.svg")
    val AREND_MODULE = AllIcons.Nodes.AnonymousClass
    val AREND_FILE = getIcon("/icons/arend_file.svg")
    val DIRECTORY = AllIcons.Nodes.Package
    val LIBRARY_ICON = AllIcons.Nodes.PpLib
    val YAML_KEY = AllIcons.Nodes.FieldPK

    val RUN_CONFIGURATION = AllIcons.RunConfigurations.Application
    val LIBRARY_CONFIG = AllIcons.General.GearPlain

    val MESSAGES = AllIcons.Toolwindows.ToolWindowMessages
    val FILTER = AllIcons.General.Filter
    val CONSOLE = AllIcons.Nodes.Console
    val CLEAR = AllIcons.Actions.GC
    val CANCEL = AllIcons.Actions.Back
    val CLOSE = AllIcons.Actions.Cancel

    val SHOW_FIELDS_IMPL = getIcon("/icons/showFieldImpl.svg")
    val SHOW_NON_IMPLEMENTED = getIcon("/icons/showNonImpl.svg")
    val SHOW_IMPLICITS = getIcon("/icons/showImplicits.svg")
    val SHOW_NORMALIZED = AllIcons.Debugger.EvaluateExpression

    val MOVE_LEFT = getIcon("/icons/moveLeft.svg")
    val MOVE_RIGHT = getIcon("/icons/moveRight.svg")

    // Source code elements

    val CLASS_DEFINITION = AllIcons.Nodes.Class
    val RECORD_DEFINITION = AllIcons.Nodes.Record
    val CLASS_FIELD = getIcon("/icons/field.svg")
    val IMPLEMENTATION = AllIcons.General.Show_to_implement
    val CLASS_INSTANCE = AllIcons.Nodes.Interface
    val CONSTRUCTOR = getIcon("/icons/constructor.svg")
    val DATA_DEFINITION = getIcon("/icons/dataStructure.svg")
    val FUNCTION_DEFINITION = AllIcons.Nodes.Function
    val MODULE_DEFINITION = AllIcons.Nodes.AbstractMethod
    val META_DEFINITION = AllIcons.Nodes.Method
    val COCLAUSE_DEFINITION = FUNCTION_DEFINITION

    // Errors

    val ERROR = AllIcons.RunConfigurations.ToolbarError
    val WARNING = AllIcons.RunConfigurations.ToolbarFailed
    val GOAL = getIcon("/icons/goal.svg")
    val INFO = AllIcons.General.NotificationInfo

    fun getErrorLevelIcon(level: GeneralError.Level) = when (level) {
        GeneralError.Level.INFO -> INFO
        GeneralError.Level.WARNING_UNUSED -> WARNING
        GeneralError.Level.WARNING -> WARNING
        GeneralError.Level.GOAL -> GOAL
        GeneralError.Level.ERROR -> ERROR
    }

    val SHOW = AllIcons.Actions.Show
    val PIN = AllIcons.General.Pin_tab

    fun definitionToIcon(definition: Definition): Icon? = when (definition) {
        is DataDefinition -> DATA_DEFINITION
        is Constructor -> CONSTRUCTOR
        is FunctionDefinition -> if (definition.kind == CoreFunctionDefinition.Kind.INSTANCE) CLASS_INSTANCE else FUNCTION_DEFINITION
        is ClassDefinition -> if (definition.isRecord) RECORD_DEFINITION else CLASS_DEFINITION
        is ClassField -> CLASS_FIELD
        else -> null
    }
}
