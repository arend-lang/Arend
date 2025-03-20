package org.arend.refactoring

import org.arend.ext.module.LongName
import org.arend.module.ModuleLocation
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ancestor
import org.arend.psi.descendantOfType
import org.arend.psi.ext.*
import org.arend.scratch.isArendScratch
import org.arend.server.modifier.RawImportAdder
import org.arend.server.modifier.RawImportRemover
import org.arend.server.modifier.RawModifier
import org.arend.server.modifier.RawSequenceModifier
import org.arend.term.group.ConcreteNamespaceCommand
import org.arend.toolWindow.repl.ArendReplService
import org.arend.util.mapFirstNotNull

fun isVisible(importFile: ArendFile, currentFile: ArendFile): Boolean {
    val modulePath = importFile.moduleLocation?.modulePath ?: return false
    val locationsOk = importFile.moduleLocation != null && importFile.moduleLocation?.modulePath != null

    if (currentFile.isRepl || currentFile.isArendScratch) return locationsOk

    val conf = currentFile.arendLibrary ?: return false
    val inTests = conf.getFileLocationKind(currentFile) == ModuleLocation.LocationKind.TEST

    return locationsOk && (importFile.generatedModuleLocation != null || conf.availableConfigs.mapFirstNotNull { it.findArendFile(modulePath, true, inTests) } == importFile) //Needed to prevent attempts of link repairing in a situation when the target directory is not marked as a content root
}

abstract class NsCmdRefactoringAction {
    abstract fun execute()
}

class NsCmdRawModifierAction(val rawModifier: RawModifier, val currentFile: ArendFile): NsCmdRefactoringAction() {
  override fun execute() {
    execute(rawModifier, currentFile)
  }

  companion object {
    private fun execute(modifier: RawModifier, currentFile: ArendFile) {
      when (modifier) {
        is RawImportAdder -> {
          val factory = ArendPsiFactory(currentFile.project)
          val statCmd = factory.createFromText(modifier.command.toString())?.descendantOfType<ArendStatCmd>()
          if (statCmd != null) {
            if (currentFile.isRepl) {
              val replService = currentFile.project.getService(ArendReplService::class.java)
              replService.getRepl()?.repl(statCmd.text) {""}
              statCmd.longName?.let { replService.getRepl()?.println("Imported ${it.text} from auto-import") }
            }
            val modulePath = LongName(modifier.command.module.path)
            val stat = statCmd.ancestor<ArendStat>()

            if (stat != null)
              addStatCmd(factory, stat, findPlaceForNsCmd(currentFile, modulePath))
          }
        }
        is RawImportRemover -> {
          if ((modifier.namespaceCommand as? ConcreteNamespaceCommand)?.data is ArendStatCmd) {
            val statCmd = (modifier.namespaceCommand as? ConcreteNamespaceCommand)?.data as ArendStatCmd
            val stat = statCmd.ancestor<ArendStat>()
            stat?.delete()
          }
        }
        is RawSequenceModifier -> {
          for (m in modifier.sequence) execute(m, currentFile)
        }
      }
    }
  }
}