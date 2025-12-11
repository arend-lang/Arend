package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.impl.BetterPrefixMatcher
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.util.descendantsOfType
import org.arend.ArendIcons.AREND
import org.arend.ArendIcons.DIRECTORY
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.ArendRefIdentifier
import org.arend.psi.ext.ArendReplLine
import org.arend.psi.ext.ArendStatCmd
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.repl.Repl.getInScopeElements
import org.arend.repl.Repl.replModuleLocation
import org.arend.repl.action.CdCommand.PARENT_DIR
import org.arend.repl.action.ListModulesCommand.ALL_MODULES
import org.arend.repl.action.LoadLibraryCommand.CUR_DIR
import org.arend.resolving.ArendReferenceBase.Companion.createArendLookUpElement
import org.arend.term.abs.ConcreteBuilder
import org.arend.toolWindow.repl.ArendReplService
import org.arend.util.FileUtils
import kotlin.collections.contains

class ArendReplCompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val file = parameters.originalFile as? ArendFile ?: return
    if (!file.isRepl) {
      return super.fillCompletionVariants(parameters, result)
    }
    val project = parameters.editor.project ?: return
    val repl = project.service<ArendReplService>().getRepl()
    val server = repl?.getServer()

    if (isLoadUnloadOrImportReplCommand(file)) {
      val modulePath = (file.childOfType<ArendReplLine>()?.expr?.text ?: getModulePathFromImport(file))
        ?.run {
          if (endsWith('.')) this + "Foo" else this
        } ?: "Foo"
      val replLineFile = ArendPsiFactory(project, replModuleLocation.libraryName).createFromText("\\import $modulePath")
      val replLineGroup = replLineFile?.let { ConcreteBuilder.convertGroup(it, it.moduleLocation, DummyErrorReporter.INSTANCE) }
      val ref = replLineFile?.descendantsOfType<ArendRefIdentifier>()?.lastOrNull()?.apply {
        (containingFile as ArendFile?)?.generatedModuleLocation = replModuleLocation
      }
      val modulePaths = repl?.loadedModules ?: emptyList()
      val modules = mutableSetOf<ModulePath>()
      for (modulePath in modulePaths) {
        if (modulePath == Prelude.MODULE_PATH || modulePath == replModuleLocation.modulePath) {
          continue
        }
        var rootPath = ModulePath()
        for (path in modulePath.toList()) {
          rootPath = ModulePath(rootPath.toList() + path)
          modules.add(rootPath)
        }
      }

      if ((isLoadReplCommand(file) || isUnloadReplCommand(file)) && !modulePath.contains('.')) {
        val lookupElement = LookupElementBuilder.create(ALL_MODULES)
        result.addElement(lookupElement)
      }

      val isImportOrUnload = isImportOrUnloadReplCommand(file)
      ref?.let { server?.getCompletionVariants(replLineGroup, it) }?.forEach {
          origElement ->
        run {
          if (origElement.modulePath == Prelude.MODULE_PATH || origElement.modulePath == replModuleLocation.modulePath) {
            return@run
          }
          if (isImportOrUnload && !modules.contains(origElement.modulePath)) {
            return@run
          }
          createArendLookUpElement(origElement, origElement.abstractReferable, file, false, null, false)?.let {
            result.addElement(it)
          }
        }
      }
    } else if (isModuleCommand(file)) {
      val lookupElement = LookupElementBuilder.create(ALL_MODULES)
      result.addElement(lookupElement)
    } else if (isCdOrLibCommand(file)) {
      val currentDir = repl?.pwd?.toFile()
      val files = currentDir?.listFiles()?.toList() ?: emptyList()
      result.addElement(
        if (currentDir?.resolve(FileUtils.LIBRARY_CONFIG_FILE)?.exists() == true) {
          LookupElementBuilder.create(CUR_DIR).withIcon(AREND)
        } else {
          LookupElementBuilder.create(CUR_DIR).withIcon(DIRECTORY)
        }
      )
      result.addElement(LookupElementBuilder.create(PARENT_DIR).withIcon(DIRECTORY))
      for (curFile in files) {
        if (curFile.isDirectory && !curFile.isHidden) {
          val lookupElement = if (curFile.resolve(FileUtils.LIBRARY_CONFIG_FILE).exists()) {
            LookupElementBuilder.create(curFile.name).withIcon(AREND)
          } else {
            LookupElementBuilder.create(curFile.name).withIcon(DIRECTORY)
          }
          result.addElement(lookupElement)
        }
      }
    } else if (isUnlibCommand(file)) {
      val libraries = repl?.libraries ?: mutableListOf()
      for (library in libraries) {
        if (library == Prelude.LIBRARY_NAME || library == replModuleLocation.libraryName) continue
        val lookupElement = LookupElementBuilder.create(library).withIcon(AREND)
        result.addElement(lookupElement)
      }
    } else {
      val bpm = object: BetterPrefixMatcher(result.prefixMatcher, Int.MIN_VALUE) {
        override fun prefixMatchesEx(name: String?): MatchingOutcome {
          if (name?.startsWith(myPrefix) == true) return MatchingOutcome.BETTER_MATCH
          return super.prefixMatchesEx(name)
        }
      }
      server?.let { getInScopeElements(server, server.getRawGroup(replModuleLocation)?.statements ?: emptyList())
        .mapNotNull { (it as? LocatedReferableImpl)?.data as? PsiLocatedReferable } }
        ?.filter { it.isValid }
        ?.forEach {
          val name = it.name ?: return@forEach
          if (bpm.prefixMatches(name)) {
            createArendLookUpElement(null, it, parameters.originalFile, true, null, it !is ArendDefClass || !it.isRecord)
              ?.let { lookupElement -> result.addElement(lookupElement) }
          }
        }
    }
  }

  companion object {
    private fun getModulePathFromImport(file: ArendFile): String? {
      val statCmd = file.childOfType<ArendStatCmd>() ?: return null
      return statCmd.longName?.text?.let { it + if (statCmd.text?.endsWith(".") == true) "." else "" }
    }

    fun getReplCommand(replFile: ArendFile): @NlsSafe String? {
      return replFile.childOfType<ArendReplLine>()?.replCommand?.text?.drop(1) ?: if (replFile.text.trim().startsWith("\\import")) "import" else null
    }

    private fun isUnloadReplCommand(replFile: ArendFile): Boolean {
      val replCommand = getReplCommand(replFile)
      return replCommand?.equals("unload") == true
    }

    private fun isImportOrUnloadReplCommand(replFile: ArendFile): Boolean {
      val replCommand = getReplCommand(replFile)
      return replCommand?.equals("import") == true || isUnloadReplCommand(replFile)
    }

    private fun isLoadReplCommand(replFile: ArendFile): Boolean {
      val replCommand = getReplCommand(replFile)
      return replCommand?.equals("load") == true
    }

    private fun isLoadUnloadOrImportReplCommand(replFile: ArendFile): Boolean {
      return isLoadReplCommand(replFile) || isImportOrUnloadReplCommand(replFile)
    }

    private fun isModuleCommand(replFile: ArendFile): Boolean {
      val replCommand = getReplCommand(replFile)
      return replCommand?.equals("modules") == true
    }

    private fun isCdOrLibCommand(replFile: ArendFile): Boolean {
      val replCommand = getReplCommand(replFile)
      return replCommand?.equals("cd") == true || replCommand?.equals("lib") == true
    }

    private fun isUnlibCommand(replFile: ArendFile): Boolean {
      val replCommand = getReplCommand(replFile)
      return replCommand?.equals("unlib") == true
    }
  }
}
