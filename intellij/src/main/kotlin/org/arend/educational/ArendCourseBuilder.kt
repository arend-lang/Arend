package org.arend.educational

import com.jetbrains.edu.learning.EduCourseBuilder
import com.jetbrains.edu.learning.LanguageSettings
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.TaskFile
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkTypeId
import com.intellij.openapi.projectRoots.JavaSdkType
import com.intellij.openapi.roots.ui.configuration.JdkComboBox
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.UserDataHolder
import javax.swing.JComponent
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph

import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.coursecreator.actions.studyItem.NewStudyItemInfo
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator

class ArendCourseBuilder : EduCourseBuilder<ArendEduProjectSettings> {
    override fun getLanguageSettings(): LanguageSettings<ArendEduProjectSettings> = ArendLanguageSettings()

    override fun getCourseProjectGenerator(course: Course): CourseProjectGenerator<ArendEduProjectSettings> =
        ArendCourseProjectGenerator(this, course)

    override fun initNewTask(course: Course, task: Task, info: NewStudyItemInfo, withSources: Boolean) {
        task.addTaskFile(TaskFile("src/Solution.ard", ""))
    }

    override fun mainTemplateName(course: Course): String = "Solution.ard"
}

class ArendLanguageSettings : LanguageSettings<ArendEduProjectSettings>() {
    private val propertyGraph: PropertyGraph = PropertyGraph()
    private val sdkProperty: GraphProperty<Sdk?> = propertyGraph.lazyProperty {
        ProjectJdkTable.getInstance().allJdks.firstOrNull { isSuitableSdkType(it.sdkType) }
    }

    override fun getSettings(): ArendEduProjectSettings = ArendEduProjectSettings()

    override fun getLanguageSettingsComponents(course: Course, disposable: CheckedDisposable, context: UserDataHolder?): List<LabeledComponent<JComponent>> {
        val sdkModel = ProjectSdksModel()
        sdkModel.reset(null)
        val sdkComboBox = JdkComboBox(null, sdkModel, { isSuitableSdkType(it) }, null, null, null)
        sdkComboBox.selectedJdk = sdkProperty.get()
        sdkComboBox.addItemListener {
            sdkProperty.set(sdkComboBox.selectedJdk)
            notifyListeners()
        }

        return listOf(LabeledComponent.create(sdkComboBox, "Project SDK"))
    }

    private fun isSuitableSdkType(sdkType: SdkTypeId): Boolean {
        return sdkType is JavaSdkType && !sdkType.isDependent
    }
}
