package org.arend.typechecking.runner

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import kotlinx.coroutines.*
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.FileListErrorReporter
import org.arend.ext.error.GeneralError
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.module.LongName
import org.arend.ext.module.ModuleLocation
import org.arend.server.ArendServerRequesterImpl
import org.arend.server.ArendServerService
import org.arend.toolWindow.errors.ArendMessagesService
import org.arend.typechecking.CoroutineCancellationIndicator
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.ext.module.FullName
import org.arend.naming.reference.TCDefReferable
import org.arend.term.concrete.Concrete
import org.arend.typechecking.visitor.ArendCheckerFactory

@Service(Service.Level.PROJECT)
class RunnerService(private val project: Project, val coroutineScope: CoroutineScope) {
    val myFileListErrorReporter = FileListErrorReporter(project.basePath!!)

    private fun runChecker(library: String?, isTest: Boolean, module: ModuleLocation?, definition: LongName?, onlyResolve: Boolean, checkerFactory: ArendCheckerFactory?, renamed: Map<TCDefReferable, TCDefReferable>?, bgAction: (() -> Unit)?, edtAction: (() -> Unit)?) =
        coroutineScope.launch {
            val message = module?.toString() ?: (library ?: "project")
            val server = project.service<ArendServerService>().server
            withBackgroundProgress(project, "Checking $message") { reportSequentialProgress { reporter ->
                val checker = reporter.nextStep(if (onlyResolve) 100 else 5, "Resolving $message") { reportRawProgress { reporter ->
                    if (module == null) {
                        ArendServerRequesterImpl(project).requestUpdate(server, library, isTest)
                    }
                    val checker = server.getCheckerFor(if (module == null) server.modules.filter { (library == null || it.libraryName == library) && (it.locationKind == ModuleLocation.LocationKind.SOURCE || isTest && it.locationKind == ModuleLocation.LocationKind.TEST) } else listOf(module))
                    checker.resolveAll(CoroutineCancellationIndicator(this), IntellijProgressReporter(reporter) { it.toString() })
                    checker
                } }

                if (checkerFactory == null) withContext(Dispatchers.EDT) {
                    project.service<ArendMessagesService>().update()
                }

                val updated = if (onlyResolve) false else reporter.nextStep(100, "Typechecking $message") {
                    reportRawProgress { reporter ->
                        val indicator = IntellijProgressReporter<List<Concrete.ResolvableDefinition>>(reporter) {
                            val ref = it.firstOrNull()?.data ?: return@IntellijProgressReporter null
                            val location = if (module == null) ref.location else null
                            (if (location == null) "" else "$location ") + ref.refLongName.toString()
                        }
                        val result = if (checkerFactory == null) {
                            checker.typecheck(if (definition == null || module == null) null else listOf(FullName(module, definition)), NotificationErrorReporter(project), CoroutineCancellationIndicator(this), indicator)
                        } else {
                            checker.typecheck(FullName(module, definition!!), checkerFactory, renamed, DummyErrorReporter.INSTANCE, CoroutineCancellationIndicator(this), indicator)
                        }
                        if (bgAction != null) bgAction()
                        result
                    } > 0
                }

                if (updated && checkerFactory == null) {
                    if (!ApplicationManager.getApplication().isUnitTestMode) {
                        DaemonCodeAnalyzer.getInstance(project).restart()
                    }
                    withContext(Dispatchers.EDT) {
                        project.service<ArendMessagesService>().update()
                        if (edtAction != null) edtAction()
                    }
                } else if (edtAction != null) {
                    withContext(Dispatchers.EDT) {
                        edtAction()
                    }
                }
            } }
        }

    private fun runCheckerWithFile(library: String?, isTest: Boolean, module: ModuleLocation?, definition: LongName?, onlyResolve: Boolean, checkerFactory: ArendCheckerFactory?, renamed: Map<TCDefReferable, TCDefReferable>?, bgAction: (() -> Unit)?, edtAction: (() -> Unit)?)
    = coroutineScope.launch {
    println("RunCheckerWithFile")
    val message = module?.toString() ?: (library ?: "project")
    val server = project.service<ArendServerService>().server
    server.addErrorReporter(myFileListErrorReporter)
    withBackgroundProgress(project, "Checking $message") { reportSequentialProgress { reporter ->
      val checker = reporter.nextStep(if (onlyResolve) 100 else 5, "Resolving $message") { reportRawProgress { reporter ->
        if (module == null) {
          ArendServerRequesterImpl(project).requestUpdate(server, library, isTest)
        }
        val checker = server.getCheckerFor(if (module == null) server.modules.filter { (library == null || it.libraryName == library) && (it.locationKind == ModuleLocation.LocationKind.SOURCE || isTest && it.locationKind == ModuleLocation.LocationKind.TEST) } else listOf(module))
        checker.resolveAll(CoroutineCancellationIndicator(this), IntellijProgressReporter(reporter) { it.toString() })
        checker
      } }

      if (checkerFactory == null) withContext(Dispatchers.EDT) {
        project.service<ArendMessagesService>().update()
      }

      val updated = if (onlyResolve) false else reporter.nextStep(100, "Typechecking $message") {
        reportRawProgress { reporter ->
          val indicator = IntellijProgressReporter<List<Concrete.ResolvableDefinition>>(reporter) {
            val ref = it.firstOrNull()?.data ?: return@IntellijProgressReporter null
            val location = if (module == null) ref.location else null
            (if (location == null) "" else "$location ") + ref.refLongName.toString()
          }
          val result = if (checkerFactory == null) {
            checker.typecheck(if (definition == null || module == null) null else listOf(FullName(module, definition)), NotificationErrorReporter(project), CoroutineCancellationIndicator(this), indicator)
          } else {
            checker.typecheck(FullName(module, definition!!), checkerFactory, renamed, DummyErrorReporter.INSTANCE, CoroutineCancellationIndicator(this), indicator)
          }
          if (bgAction != null) bgAction()
          result
        } > 0
      }

      if (updated && checkerFactory == null) {
        if (!ApplicationManager.getApplication().isUnitTestMode) {
          DaemonCodeAnalyzer.getInstance(project).restart()
        }
        withContext(Dispatchers.EDT) {
          project.service<ArendMessagesService>().update()
          if (edtAction != null) edtAction()
        }
      } else if (edtAction != null) {
        withContext(Dispatchers.EDT) {
          edtAction()
        }
      }
    } }
  }

  private fun runCheckerForList(library: String?, isTest: Boolean, module: ModuleLocation?, definition: LongName?, onlyResolve: Boolean, checkerFactory: ArendCheckerFactory?, renamed: Map<TCDefReferable, TCDefReferable>?, bgAction: (() -> Unit)?, edtAction: (() -> Unit)?) : List<GeneralError> {
    val newErrorList = mutableListOf<GeneralError>()
    val myNewListErrorReporter = ListErrorReporter(newErrorList)
    val server = project.service<ArendServerService>().server
    // Use runBlocking to wait for the coroutine to complete before returning the error list
    runBlocking<Unit> {
      val message = module?.toString() ?: (library ?: "project")
      server.addErrorReporter(myNewListErrorReporter)
      withBackgroundProgress(project, "Checking $message") {
        reportSequentialProgress { reporter ->
          val checker = reporter.nextStep(if (onlyResolve) 100 else 5, "Resolving $message") {
            reportRawProgress { reporter ->
              if (module == null) {
                ArendServerRequesterImpl(project).requestUpdate(server, library, isTest)
              }
              val modulesToCheck = if (module == null) server.modules.filter { (library == null || it.libraryName == library) && (it.locationKind == ModuleLocation.LocationKind.SOURCE || isTest && it.locationKind == ModuleLocation.LocationKind.TEST) } else listOf(module)
              val checker = server.getCheckerFor(modulesToCheck)
              checker.resolveAll(CoroutineCancellationIndicator(this), IntellijProgressReporter(reporter) { it.toString() })
              checker
            }
          }

          if (checkerFactory == null) withContext(Dispatchers.EDT) {
            project.service<ArendMessagesService>().update()
          }
          val updated = if (onlyResolve) false else reporter.nextStep(100, "Typechecking $message") {
            reportRawProgress { reporter ->
              val indicator = IntellijProgressReporter<List<Concrete.ResolvableDefinition>>(reporter) {
                val ref = it.firstOrNull()?.data ?: return@IntellijProgressReporter null
                val location = if (module == null) ref.location else null
                (if (location == null) "" else "$location ") + ref.refLongName.toString()
              }
              println("[DEBUG_LOG] About to call checker.typecheck")
              val result = if (checkerFactory == null) {
                checker.typecheck(if (definition == null || module == null) null else listOf(FullName(module, definition)), NotificationErrorReporter(project), CoroutineCancellationIndicator(this), indicator)
              } else {
                checker.typecheck(FullName(module, definition!!), checkerFactory, renamed, DummyErrorReporter.INSTANCE, CoroutineCancellationIndicator(this), indicator)
              }
              println("[DEBUG_LOG] checker.typecheck returned: $result")
              println("[DEBUG_LOG] Errors after typecheck: ${newErrorList.size}")
              for ((index, error) in newErrorList.withIndex()) {
                println("[DEBUG_LOG] Error in newErrorList[$index]: $error")
              }
              if (bgAction != null) bgAction()
              result
            } > 0
          }
          println("[DEBUG_LOG] Typechecking step completed, updated: $updated")

          if (updated && checkerFactory == null) {
            if (!ApplicationManager.getApplication().isUnitTestMode) {
              DaemonCodeAnalyzer.getInstance(project).restart()
            }
            withContext(Dispatchers.EDT) {
              project.service<ArendMessagesService>().update()
              if (edtAction != null) edtAction()
            }
          } else if (edtAction != null) {
            withContext(Dispatchers.EDT) {
              edtAction()
            }
          }
        }
      }
    }
    println("[DEBUG_LOG] runBlocking completed, returning ${myNewListErrorReporter.errorList.size} errors")
    return myNewListErrorReporter.errorList
  }

    fun runChecker(library: String?, isTest: Boolean, module: ModuleLocation?, definition: LongName?, onlyResolve: Boolean = false) =
        runChecker(library, isTest, module, definition, onlyResolve, null, null, null, null)

    fun runChecker(module: ModuleLocation, definition: LongName?) =
        runChecker(module.libraryName, module.locationKind == ModuleLocation.LocationKind.TEST, module, definition)

    fun runChecker(module: ModuleLocation, definition: LongName, checkerFactory: ArendCheckerFactory, renamed: Map<TCDefReferable, TCDefReferable>?, bgAction: (() -> Unit)?, edtAction: (() -> Unit)?) =
        runChecker(module.libraryName, module.locationKind == ModuleLocation.LocationKind.TEST, module, definition, false, checkerFactory, renamed, bgAction, edtAction)

    fun runChecker(module: ModuleLocation, onlyResolve: Boolean = false) =
        runChecker(module.libraryName, module.locationKind == ModuleLocation.LocationKind.TEST, module, null, onlyResolve)

  fun runCheckerWithFile(module : ModuleLocation) =
    runCheckerWithFile(module.libraryName, module.locationKind == ModuleLocation.LocationKind.TEST, module, null, false, null, null, null, null)

  fun runCheckerForList(module : ModuleLocation) =
    runCheckerForList(module.libraryName, module.locationKind == ModuleLocation.LocationKind.TEST, module, null, false, null, null, null, null)

}