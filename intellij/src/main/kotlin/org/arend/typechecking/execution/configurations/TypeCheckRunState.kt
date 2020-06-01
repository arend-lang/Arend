package org.arend.typechecking.execution.configurations

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.PatchedRunnableState
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerUIActionsHandler
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.components.service
import org.arend.typechecking.execution.TypeCheckCommand
import org.arend.typechecking.execution.TypeCheckProcessHandler
import org.arend.typechecking.execution.TypecheckingEventsProcessor

class TypeCheckRunState(
        environment: ExecutionEnvironment,
        private val command: TypeCheckCommand
) : CommandLineState(environment) {

    override fun startProcess() = TypeCheckProcessHandler(environment.project.service(), command)

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor)
        console?.attachToProcess(processHandler)
        ProcessTerminatedListener.attach(processHandler)
        return DefaultExecutionResult(
                console,
                processHandler,
                *createActions(console, processHandler, executor)
        )
    }

    override fun createConsole(executor: Executor): ConsoleView? {
        val runConfiguration = environment.runnerAndConfigurationSettings?.configuration
                ?: return null
        val testFrameworkName = "ArendTypeCheckRunner"
        val consoleProperties = SMTRunnerConsoleProperties(
                runConfiguration,
                testFrameworkName,
                executor
        )

        val splitterPropertyName = "$testFrameworkName.Splitter.Proportion"
        val consoleView = SMTRunnerConsoleView(consoleProperties, splitterPropertyName)
        initConsoleView(consoleView, testFrameworkName)

        return consoleView
    }

    private fun initConsoleView(consoleView: SMTRunnerConsoleView, testFrameworkName: String) {
        consoleView.addAttachToProcessListener { processHandler ->
            attachEventsProcessors(
                    consoleView.properties,
                    consoleView.resultsViewer,
                    processHandler,
                    testFrameworkName
            )
        }
        consoleView.setHelpId("reference.runToolWindow.testResultsTab")
        consoleView.initUI()
    }

    private fun attachEventsProcessors(
            consoleProperties: TestConsoleProperties,
            resultsViewer: SMTestRunnerResultsForm,
            processHandler: ProcessHandler,
            testFrameworkName: String
    ) {
        if (processHandler !is TypeCheckProcessHandler) error("Invalid process handler")

        val eventsProcessor = TypecheckingEventsProcessor(
                consoleProperties.project,
                resultsViewer.testsRootNode,
                testFrameworkName
        )
        eventsProcessor.addEventsListener(resultsViewer)
        processHandler.eventsProcessor = eventsProcessor

        val uiActionsHandler = SMTRunnerUIActionsHandler(consoleProperties)
        resultsViewer.addEventsListener(uiActionsHandler)

        processHandler.addProcessListener(object : ProcessAdapter() {

            override fun processTerminated(event: ProcessEvent) {
                eventsProcessor.onFinishTesting()
            }

            override fun startNotified(event: ProcessEvent) = eventsProcessor.onStartTesting()
        })
    }
}
