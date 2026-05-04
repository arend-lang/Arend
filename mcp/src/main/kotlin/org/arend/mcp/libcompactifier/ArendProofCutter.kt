package org.arend.mcp.libcompactifier

import org.arend.core.definition.ClassField
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.ClassCallExpression
import org.arend.error.DummyErrorReporter
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.module.ModuleLocation
import org.arend.frontend.library.CliServerRequester
import org.arend.frontend.library.FileSourceLibrary
import org.arend.frontend.library.LibraryManager
import org.arend.frontend.library.SourceLibrary
import org.arend.frontend.source.PreludeResourceSource
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.UnresolvedReference
import org.arend.prelude.Prelude
import org.arend.server.ArendChecker
import org.arend.server.ArendServer
import org.arend.server.impl.ArendServerImpl
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.ConcreteExpressionFactory
import org.arend.term.group.ConcreteGroup
import org.arend.term.group.ConcreteStatement
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.server.ProgressReporter
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.util.FileUtils
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Objects
import java.util.function.Supplier
import kotlin.system.exitProcess

object ArendProofCutter {

  /**
   * Returns true if the given ClassFieldImpl's implemented field has a proposition type
   * (i.e., its typechecked result type level is -1).
   */
  private fun isPropositionClassField(field: ClassField): Boolean {
    if (field.resultTypeLevel == -1) return true
    if (field.isProperty) return true
    // Check the sort of the field's type in the parent class
    // The parent class sort tracks which fields are Prop
    val parentClass = field.parentClass
    val fieldType = parentClass.getFieldType(field)
    if (fieldType != null) {
      val codomain = fieldType.codomain
      try {
        val sort = codomain?.getSortOfType()
        if (sort != null && sort.isProp) return true
      } catch (_: Exception) {}
    }
    return false
  }

  private fun isPropositionField(fieldImpl: Concrete.ClassFieldImpl): Boolean {
    var ref = fieldImpl.implementedField
    if (ref is UnresolvedReference && ref.isResolved) {
      ref = ref.resolve(null, null, null, null)
    }
    if (ref is TCDefReferable) {
      val typechecked = ref.typechecked
      if (typechecked is ClassField) {
        return isPropositionClassField(typechecked)
      }
    }
    return false
  }

  /**
   * Takes a parsed ConcreteGroup and returns a new ConcreteGroup with all proof bodies removed.
   * For each BaseFunctionDefinition, the body is replaced with a goal.
   * For each ClassDefinition, ClassFieldImpl implementations whose field type is a proposition
   * are replaced with a goal.
   */
  fun cutProofs(group: ConcreteGroup, checker: ArendChecker): ConcreteGroup {
    val newStatements = group.statements().map { stmt ->
      if (stmt.group() != null) {
        ConcreteStatement(cutProofs(stmt.group()!!, checker), stmt.command(), stmt.pLevelsDefinition(), stmt.hLevelsDefinition())
      } else {
        stmt
      }
    }
    val newDynamicGroups = group.dynamicGroups().map { cutProofs(it, checker) }

    val def = group.definition()
    val newDef: Concrete.ResolvableDefinition? = when {
      def is Concrete.BaseFunctionDefinition && def.kind == FunctionKind.LEMMA -> {
        val emptyBody = Concrete.TermFunctionBody(null, ConcreteExpressionFactory.cGoal("hidden_proof", null))
        def.copy(def.parameters, emptyBody)
      }
      def is Concrete.CoClauseFunctionDefinition && def.kind == FunctionKind.FUNC_COCLAUSE -> {
        val implFieldRef = def.implementedField
        val classField = when {
          implFieldRef is TCDefReferable -> implFieldRef.typechecked as? ClassField
          else -> {
            val typechecked = def.data.typechecked
            if (typechecked is FunctionDefinition) typechecked.implementedField?.typechecked as? ClassField else null
          }
        }
        if (classField != null && isPropositionClassField(classField)) {
          val emptyBody = Concrete.TermFunctionBody(null, ConcreteExpressionFactory.cGoal("hidden_proof", null))
          def.copy(def.parameters, emptyBody)
        } else def
      }
      def is Concrete.BaseFunctionDefinition && def.body is Concrete.CoelimFunctionBody -> {
        val coelimBody = def.body as Concrete.CoelimFunctionBody
        val typecheckedDef = def.data?.typechecked
        val targetClass = if (typecheckedDef is FunctionDefinition) {
          (typecheckedDef.resultType as? ClassCallExpression)?.definition
        } else null
        for (element in coelimBody.coClauseElements) {
          if (element is Concrete.ClassFieldImpl && element !is Concrete.CoClauseFunctionReference) {
            val fieldName = element.implementedField.textRepresentation()
            val classField = targetClass?.findField { it.name == fieldName }
            if (classField != null && isPropositionClassField(classField)) {
              element.implementation = ConcreteExpressionFactory.cGoal("hidden_proof", null)
            }
          }
        }
        def
      }
      def is Concrete.ClassDefinition -> {
        for (element in def.elements) {
          if (element is Concrete.ClassFieldImpl && element !is Concrete.CoClauseFunctionReference && isPropositionField(element)) {
            element.implementation = ConcreteExpressionFactory.cGoal("hidden_proof", null)
          }
        }
        def
      }
      else -> def
    }

    return ConcreteGroup(group.description(), group.referable(), newDef, newStatements, newDynamicGroups, group.externalParameters())
  }

  /**
   * Takes a ConcreteGroup, cuts all proofs, and returns the pretty-printed result.
   */
  fun cutProofsAndPrint(group: ConcreteGroup, checker: ArendChecker): String {
    val cut = cutProofs(group, checker)
    val builder = StringBuilder()
    val visitor = PrettyPrintVisitor(builder, 0)
    visitor.printStatements(cut.statements())
    return builder.toString()
  }

  @JvmStatic
  fun main(args: Array<String>) {
    try {
      val libDir: Path = args.firstOrNull()?.let { Paths.get(it) } ?: return
      val libraryManager = LibraryManager(ListErrorReporter())
      val server: ArendServer = ArendServerImpl(CliServerRequester(libraryManager), false, false, true)
      server.addReadOnlyModule(
        Prelude.MODULE_LOCATION,
        Supplier { Objects.requireNonNull<ConcreteGroup?>(PreludeResourceSource().loadGroup(DummyErrorReporter.INSTANCE)) })
      server.addErrorReporter(ListErrorReporter())

      val library: SourceLibrary =
        FileSourceLibrary.fromConfigFile(libDir.resolve(FileUtils.LIBRARY_CONFIG_FILE), false, ListErrorReporter())

      libraryManager.updateLibrary(library, server)
      for (modulePath in library.findModules(false)) {
        val module = ModuleLocation(
          library.libraryName,
          ModuleLocation.LocationKind.SOURCE,
          modulePath
        )
        library.getSource(modulePath, false)?.load(server, ListErrorReporter())
        val group: ConcreteGroup = server.getRawGroup(module) ?: exitProcess(1)
        val checker = server.getCheckerFor(listOf(module))
        checker.resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
        checker.typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())

        val result = cutProofsAndPrint(group, checker)
        val outPath = libDir.resolve(".compactifiedLib").let { base ->
          FileUtils.sourceFile(base, modulePath)
        }
        Files.createDirectories(outPath.parent)
        Files.writeString(outPath, result)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }
}
