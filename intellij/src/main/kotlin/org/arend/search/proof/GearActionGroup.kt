package org.arend.search.proof

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.popup.PopupState
import org.arend.settings.ArendProjectSettings
import org.arend.settings.ArendProjectSettingsState
import java.awt.event.MouseEvent
import kotlin.reflect.KMutableProperty1


class GearActionGroup(private val searchUI: ProofSearchUI, val project: Project) :
    AnAction(AllIcons.General.GearPlain), DumbAware {

    private val myPopupState = PopupState.forPopupMenu()

    override fun actionPerformed(e: AnActionEvent) {
        if (myPopupState.isRecentlyHidden) return // do not show new popup
        val popup = ActionManager.getInstance()
            .createActionPopupMenu(ActionPlaces.TOOLWINDOW_POPUP, ActualGearActionGroup(searchUI, project))
        val inputEvent = e.inputEvent
        val x = if (inputEvent is MouseEvent) inputEvent.x else 0
        val y = if (inputEvent is MouseEvent) inputEvent.y else 0
        myPopupState.prepareToShow(popup.component)
        popup.component.show(inputEvent.component, x, y)
    }

    private class ActualGearActionGroup(searchUI: ProofSearchUI, project: Project) : DefaultActionGroup(), DumbAware {
        init {
            templatePresentation.icon = AllIcons.General.GearPlain
            templatePresentation.text = IdeBundle.message("show.options.menu")
            addAction(IncludeTests(searchUI, project))
            addAction(IncludeNonProjectFiles(searchUI, project))
        }
    }
}

private abstract class ProofSearchToggleSettingsAction(
    val searchUI: ProofSearchUI,
    val project: Project,
    val settingsProperty: KMutableProperty1<ArendProjectSettingsState, Boolean>,
    actionText: @NlsActions.ActionText String
) : ToggleAction(actionText) {
    override fun isSelected(e: AnActionEvent): Boolean =
        settingsProperty.invoke(project.service<ArendProjectSettings>().data)

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        settingsProperty.set(project.service<ArendProjectSettings>().data, state)
        searchUI.runProofSearch(null)
    }
}

private class IncludeNonProjectFiles(searchUI: ProofSearchUI, project: Project) :
    ProofSearchToggleSettingsAction(
        searchUI,
        project,
        ArendProjectSettingsState::includeNonProjectLocations,
        "Include non-project locations"
    )

private class IncludeTests(searchUI: ProofSearchUI, project: Project) :
    ProofSearchToggleSettingsAction(
        searchUI,
        project,
        ArendProjectSettingsState::includeTestLocations,
        "Include non-project locations"
    )
