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