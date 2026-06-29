package org.arend.educational

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.jetbrains.edu.learning.EduCourseBuilder
import com.jetbrains.edu.learning.checker.TaskCheckerProvider
import com.jetbrains.edu.learning.configuration.EduConfigurator
import com.jetbrains.edu.learning.courseFormat.Course
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.psi.PsiManager
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.newproject.EduProjectSettings
import com.jetbrains.edu.learning.configuration.CourseViewVisibility
import com.jetbrains.edu.learning.configuration.attributesEvaluator.AttributesEvaluator
import org.arend.ArendIcons
import javax.swing.Icon
import com.jetbrains.edu.learning.courseFormat.Language
import org.arend.module.config.ExternalLibraryConfig
import org.arend.util.FileUtils
import org.jetbrains.yaml.psi.YAMLFile

class ArendConfigurator : EduConfigurator<ArendEduProjectSettings> {
    companion object {
        @JvmStatic
        fun registerArendLanguage() {
            try {
                val languagesField = Language::class.java.getDeclaredField("languages")
                languagesField.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                val languages = languagesField.get(null) as MutableMap<String, String>
                if (!languages.containsKey("Arend")) {
                    languages["Arend"] = "Arend"
                }
            } catch (_: Exception) {
                // Ignore if Educational plugin is not available or reflection fails
            }
        }

        fun getStudyLibrary(project: Project): ExternalLibraryConfig? {
            val configFile = project.guessProjectDir()?.findChild(FileUtils.LIBRARY_CONFIG_FILE)
            if (configFile != null) {
                val yamlFile = PsiManager.getInstance(project).findFile(configFile) as? YAMLFile
                if (yamlFile != null) {
                    return ExternalLibraryConfig(StudyTaskManager.getInstance(project).course?.name!!, yamlFile)
                }
            }
            return null
        }
    }

    override val courseBuilder: EduCourseBuilder<ArendEduProjectSettings>
        get() = ArendCourseBuilder()

    override val sourceDir: String
        get() = "src"

    override val testFileName: String
        get() = "Test.ard"

    override val taskCheckerProvider: TaskCheckerProvider
        get() = ArendTaskCheckerProvider()

    override val courseFileAttributesEvaluator: AttributesEvaluator = AttributesEvaluator(null) {
        name("task-remote-info.yaml") {
            courseViewVisibility(CourseViewVisibility.INVISIBLE_FOR_ALL)
        }
    }

    override fun getMockFileName(course: Course, text: String): String = "Solution.ard"

    override val logo: Icon
        get() = ArendIcons.AREND

    override val defaultPlaceholderText: String
        get() = "-- write your solution here"
}

class ArendEduProjectSettings : EduProjectSettings