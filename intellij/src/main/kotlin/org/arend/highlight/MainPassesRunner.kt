package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeHighlighting.TextEditorHighlightingPassFactory
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * MainPassesRunner is responsible for orchestrating the execution of highlighting passes.
 * It collects all registered highlighting pass factories and runs their passes on demand.
 *
 * Unlike the daemon-based approach where passes are run automatically,
 * this runner allows programmatic control over when passes are executed.
 */
class MainPassesRunner(private val project: Project) {

    private val passFactories = mutableListOf<TextEditorHighlightingPassFactory>()

    /**
     * Registers a highlighting pass factory with this runner.
     */
    fun registerPassFactory(factory: TextEditorHighlightingPassFactory) {
        passFactories.add(factory)
    }

    /**
     * Runs all registered highlighting passes for the given file and editor.
     * This method executes synchronously on the current thread.
     *
     * @param file The PSI file to run passes on
     * @param editor The editor associated with the file
     * @param progress The progress indicator for tracking execution
     * @return List of HighlightInfo results from all passes
     */
    fun runPasses(file: PsiFile, editor: Editor, progress: ProgressIndicator): List<HighlightInfo> {
        println("MainPassesRunner.runPasses called for file: ${file.name}")
        Thread.dumpStack()
        val results = mutableListOf<HighlightInfo>()

        for (factory in passFactories) {
            val pass = factory.createHighlightingPass(file, editor) ?: continue

            progress.checkCanceled()

            // Collect information
            pass.collectInformation(progress)

            // Apply information and collect highlights
            pass.doApplyInformationToEditor()

            // If the pass is a BasePass, we can get the highlights directly
            if (pass is BasePass) {
                results.addAll(pass.getHighlights())
            }
        }

        return results
    }

    /**
     * Runs all registered highlighting passes asynchronously in the background.
     *
     * @param file The PSI file to run passes on
     * @param editor The editor associated with the file
     * @param onComplete Callback invoked when all passes complete, with the list of highlights
     */
    fun runPassesAsync(
        file: PsiFile,
        editor: Editor,
        onComplete: (List<HighlightInfo>) -> Unit
    ) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Running highlighting passes", true) {
            override fun run(indicator: ProgressIndicator) {
                val results = runPasses(file, editor, indicator)
                onComplete(results)
            }
        })
    }

    /**
     * Runs a single pass for the given file and editor.
     *
     * @param pass The highlighting pass to run
     * @param progress The progress indicator for tracking execution
     * @return List of HighlightInfo results from the pass
     */
    fun runSinglePass(pass: TextEditorHighlightingPass, progress: ProgressIndicator): List<HighlightInfo> {
        println("MainPassesRunner.runSinglePass called for pass: $pass")
        Thread.dumpStack()
        progress.checkCanceled()

        // Collect information
        pass.collectInformation(progress)

        // Apply information
        pass.doApplyInformationToEditor()

        // If the pass is a BasePass, we can get the highlights directly
        return if (pass is BasePass) {
            pass.getHighlights()
        } else {
            emptyList()
        }
    }

    /**
     * Creates and runs a specific pass type for the given file and editor.
     *
     * @param factory The factory to create the pass from
     * @param file The PSI file to run the pass on
     * @param editor The editor associated with the file
     * @param progress The progress indicator for tracking execution
     * @return List of HighlightInfo results, or empty list if pass couldn't be created
     */
    fun runPassFromFactory(
        factory: TextEditorHighlightingPassFactory,
        file: PsiFile,
        editor: Editor,
        progress: ProgressIndicator
    ): List<HighlightInfo> {
        val pass = factory.createHighlightingPass(file, editor) ?: return emptyList()
        return runSinglePass(pass, progress)
    }

    companion object {
        /**
         * Creates a MainPassesRunner with all standard Arend highlighting pass factories registered.
         */
        fun createWithDefaultFactories(project: Project): MainPassesRunner {
            val runner = MainPassesRunner(project)
            // Factories are typically registered via plugin.xml and the daemon,
            // but you can manually add them here if needed for programmatic use
            return runner
        }
    }
}
