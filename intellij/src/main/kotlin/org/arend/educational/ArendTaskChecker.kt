package org.arend.educational
 
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.jetbrains.edu.learning.checker.*
import com.jetbrains.edu.learning.courseFormat.CheckResult
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.tasks.EduTask
import com.jetbrains.edu.learning.courseFormat.ext.getVirtualFile
import org.arend.psi.ArendFile
import org.arend.server.ArendServerService
import org.arend.typechecking.ProgressCancellationIndicator
import org.arend.server.ProgressReporter
import org.arend.ext.error.GeneralError
 
class ArendTaskCheckerProvider : TaskCheckerProvider {
    override fun getEduTaskChecker(task: EduTask, project: Project): TaskChecker<EduTask> = ArendEduTaskChecker(task, project)
}
 
class ArendEduTaskChecker(task: EduTask, project: Project) : TaskChecker<EduTask>(task, project) {
    override fun check(indicator: ProgressIndicator): CheckResult {
        val arendFiles = runReadAction {
            task.taskFiles.values.mapNotNull { taskFile ->
                val virtualFile = taskFile.getVirtualFile(project) ?: return@mapNotNull null
                PsiManager.getInstance(project).findFile(virtualFile) as? ArendFile
            }
        }
 
        if (arendFiles.isEmpty()) {
            return CheckResult(CheckStatus.Unchecked, "No Arend files found to check")
        }
 
        val serverService = project.service<ArendServerService>()
        val server = serverService.server
        val moduleLocations = arendFiles.mapNotNull { it.moduleLocation }
 
        if (moduleLocations.isEmpty()) {
            return CheckResult(CheckStatus.Failed, "Cannot determine module locations for Arend files. Make sure arend.yaml is correct.")
        }

        val checker = server.getCheckerFor(moduleLocations)
        val cancellationIndicator = ProgressCancellationIndicator(indicator)

        checker.resolveAll(cancellationIndicator, ProgressReporter.empty())
        checker.typecheck(cancellationIndicator, ProgressReporter.empty())
 
        val errors = moduleLocations.flatMap { server.errorMap[it] ?: emptyList() }
            .filter { it.level == GeneralError.Level.ERROR }
 
        return if (errors.isEmpty()) {
            CheckResult(CheckStatus.Solved, "All tests passed")
        } else {
            val message = errors.joinToString("\n") { it.toString() }
            CheckResult(CheckStatus.Failed, message)
        }
    }
}
