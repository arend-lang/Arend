package org.arend.educational

import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.jetbrains.edu.learning.CourseInfoHolder
import com.jetbrains.edu.learning.EduCourseBuilder
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.courseFormat.EduFile
import com.jetbrains.edu.learning.newproject.CourseProjectGenerator
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.AREND_LIB
import org.arend.module.Reason
import org.arend.module.showDownloadNotification
import org.arend.util.FileUtils
import org.arend.util.findExternalLibrary
import org.arend.yaml.YamlFileService

class ArendCourseProjectGenerator(
    builder: EduCourseBuilder<ArendEduProjectSettings>,
    course: Course
) : CourseProjectGenerator<ArendEduProjectSettings>(builder, course) {
    override fun autoCreatedAdditionalFiles(holder: CourseInfoHolder<Course>): List<EduFile> =
        listOf(EduFile("arend.yaml", """
            name: "${holder.course.name}"
            langVersion: 1.11
            sourcesDir: .
            dependencies: [arend-lib]
        """.trimIndent()))

    override suspend fun afterProjectGenerated(project: Project, projectSettings: ArendEduProjectSettings, openCourseParams: Map<String, String>, onConfigurationFinished: () -> Unit) {
        super.afterProjectGenerated(project, projectSettings, openCourseParams, onConfigurationFinished)
        val arendYaml = project.guessProjectDir()?.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return
        val module = ModuleUtilCore.findModuleForFile(arendYaml, project) ?: return
        module.setModuleType("AREND_MODULE")
        val service = ArendModuleConfigService.getInstance(module) ?: return
        project.service<YamlFileService>().updateIdea(arendYaml, service)
        service.copyFromYAML(true)

        if (project.findExternalLibrary(AREND_LIB) == null) {
            showDownloadNotification(project, Reason.MISSING)
        }
    }
}
