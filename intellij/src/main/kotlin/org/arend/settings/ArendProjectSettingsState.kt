package org.arend.settings

import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import org.arend.ext.core.ops.NormalizationMode

// This class is needed since EnumSet<MessageType> does not serialize correctly for some reason
class ArendProjectSettingsState {
    // Hierarchy
    var showImplFields = true
    var showNonImplFields = true
    var hierarchyViewType = TypeHierarchyBrowserBase.getSubtypesHierarchyType()

    // Messages
    var autoScrollToSource = true

    var autoScrollFromErrors = true
    var autoScrollFromWarnings = true
    var autoScrollFromGoals = true
    var autoScrollFromTypechecking = true
    var autoScrollFromShort = false
    var autoScrollFromResolving = false
    var autoScrollFromParsing = false

    var showErrors = true
    var showWarnings = true
    var showGoals = true
    var showTypechecking = true
    var showShort = true
    var showResolving = true
    var showParsing = false

    var isShowImplicitGoals = false
    var isShowErrorsOrInfoPanel = true
    var isShowGoalsInErrorsPanel = false

    // Printing options
    var consolePrintingOptions = ArendPrintingOptions()
    var errorPrintingOptions = ArendPrintingOptions()
    var goalPrintingOptions = ArendPrintingOptions()
    var tracerPrintingOptions = ArendPrintingOptions()
    var popupPrintingOptions = ArendPrintingOptions()
    var replPrintingOptions = ArendPrintingOptions()
    var arcViewPrintingOptions = ArendPrintingOptions()

    var popupNormalize = true
    var popupFontSize = 12

    // Repl options
    var replNormalizationMode = NormalizationMode.ENF.toString()

    // Libraries
    var librariesRoot: String? = null

    // Proof search
    var includeTestLocations : Boolean = false
    var includeNonProjectLocations : Boolean = true
    var truncateSearchResults : Boolean = true

    // Daemon: whether opening the project also lets agents and the CLI reach its server as a daemon
    var startDaemon : Boolean = false
}

class ArendPrintingOptions {
    var showCoerceDefinitions = false
    var showConstructorParameters = false
    var showTupleType = false
    var showLocalFieldInstance = true
    var showGlobalFieldInstance = false
    var showImplicitArgs = false
    var showTypesInLambda = false
    var showPrefixPath = false
    var showBinOpImplicitArgs = false
    var showCaseResultType = false
    var showLevels = false
    var showProofs = false
    var enableWrap = false
}