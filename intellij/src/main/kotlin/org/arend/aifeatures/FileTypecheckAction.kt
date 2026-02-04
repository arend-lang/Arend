package org.arend.aifeatures
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.module.ModuleLocation
import org.arend.typechecking.runner.RunnerService

class FileTypecheckAction(val project : Project) {
  fun typeCheckModules(modules : List<ModuleLocation>){
    println("clearing file error")
    clearFileErrorList()
    for (module in modules) {
      println("Typechecking $module from FileTypecheckAction")
      project.service<RunnerService>().runCheckerWithFile(module)
    }
  }

  fun clearFileErrorList(){
    val basePath = project.basePath ?: return
    val errorFilePath = basePath + "/.junieCommunication/errorFile.txt"
    java.io.File(errorFilePath).delete()
  }

}

//override fun actionPerformed(e: AnActionEvent) {
//            val def = selectedDefinition()
//            if (def != null) {
//                val fullName = def.refFullName
//                project.service<RunnerService>().runChecker(fullName.module ?: return, fullName.longName)
//            } else {
//                val modules = selectedModuleLocations()
//                val libraries = selectedLibraryNames()
//                if (modules.isNotEmpty()) {
//                    for (module in modules) {
//                        println("Typechecking module $module")
//                        project.service<RunnerService>().runCheckerWithFile(module)
//                    }
//                } else {
//                    // Typecheck whole libraries (both sources and tests)
//                    for (lib in libraries) {
//                        project.service<RunnerService>().runChecker(lib, true, null, null, false)
//                    }
//                }
//            }
//        }