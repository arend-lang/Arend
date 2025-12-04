package org.arend.quickfix

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.TypedBinding
import org.arend.core.context.param.DependentLink
import org.arend.core.context.param.EmptyDependentLink
import org.arend.core.definition.Constructor
import org.arend.core.definition.DataDefinition
import org.arend.core.definition.Definition
import org.arend.core.expr.*
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorExpressionPattern
import org.arend.core.pattern.ExpressionPattern
import org.arend.core.subst.ExprSubstitution
import org.arend.core.subst.SubstVisitor
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.level.LevelSubstitution
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.reference.DataContainer
import org.arend.ext.reference.Precedence
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.*
import org.arend.naming.renamer.ReferableRenamer
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.findConcreteByPsi
import org.arend.refactoring.utils.ArendRefactoringToAbstractVisitor
import org.arend.refactoring.utils.ServerBasedDefinitionRenamer
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.LocalVariablesCollector
import org.arend.term.concrete.SearchConcreteVisitor
import org.arend.term.concrete.SubstConcreteVisitor
import org.arend.term.prettyprint.DefinitionRenamerConcreteVisitor
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.error.local.ExpectedConstructorError
import org.arend.typechecking.patternmatching.ElimTypechecking
import org.arend.typechecking.patternmatching.ExpressionMatcher
import org.arend.typechecking.patternmatching.PatternTypechecking
import org.arend.typechecking.visitor.CheckTypeVisitor
import org.arend.util.ArendBundle
import org.arend.util.Decision
import java.util.Collections.singletonList

class ExpectedConstructorQuickFix(val error: ExpectedConstructorError, val cause: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun getText(): String = ArendBundle.message("arend.pattern.doMatching")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val server = project.service<ArendServerService>().server
        val caseExprPsi = cause.element?.ancestor<ArendCaseExpr>()
        val constructorPsi = cause.element?.ancestor<ArendConstructor>()

        val definition = error.definition as? TCDefReferable ?: return
        var concreteDefinition = server.getResolvedDefinition(definition)?.definition as Concrete.GeneralDefinition
        var typecheckedDefinition = (error.definition as? LocatedReferableImpl)?.typechecked

        if (constructorPsi != null) {
            var constructorFound = false
            var index = 0
            ccLoop@for (constructorClause in (concreteDefinition as Concrete.DataDefinition).constructorClauses)
                for (concreteConstructor in constructorClause.constructors) {
                    if (concreteConstructor.data.data == constructorPsi) {
                        typecheckedDefinition = (typecheckedDefinition as? DataDefinition)?.constructors?.get(index)
                        concreteDefinition = concreteConstructor
                        constructorFound = true
                        break@ccLoop
                    }
                    index++
                }
            if (!constructorFound)
                throw java.lang.IllegalStateException()
        }

        val quickFix = if (caseExprPsi == null)
            typecheckedDefinition?.let { typecheckedDefinition ->
                ElimWithExpectedConstructorErrorQuickFix(error, project, concreteDefinition, constructorPsi, typecheckedDefinition)
            }
        else {
            val concreteCaseExpr = (concreteDefinition as? Concrete.Definition)?.let{ findConcreteByPsi(it, Concrete.CaseExpression::class.java, caseExprPsi) } ?: return

            if (typecheckedDefinition != null)
                CaseExpectedConstructorErrorQuickFix(error, project, caseExprPsi, concreteCaseExpr, concreteDefinition, typecheckedDefinition)
            else null
        }
        quickFix?.runQuickFix(editor)
    }

    companion object {
        private abstract class ClauseEntry(
            val error: ExpectedConstructorError,
            val clause: Concrete.Clause,
            val substitution: ExprSubstitution,
            val constructorTypechecked: Constructor) {
            val correctedSubsts = HashMap<Variable, ExpressionPattern>()
        }

        private class CaseClauseEntry(error: ExpectedConstructorError, clause: Concrete.Clause, substitution: ExprSubstitution, constructorTypechecked: Constructor) :
            ClauseEntry(error, clause, substitution, constructorTypechecked) {
            var matchedDataCall : DataCallExpression? = null
            val matchedSubstitutions = ArrayList<ExpressionMatcher.MatchResult>()
        }

        private class ElimClauseEntry(error: ExpectedConstructorError, clause: Concrete.Clause, substitution: ExprSubstitution, constructorTypechecked: Constructor) :
            ClauseEntry(error, clause, substitution, constructorTypechecked) {
            val matchData = HashMap<DependentLink, VariableLocationDescriptor>()
            val clauseParametersToSpecialize = HashSet<Variable>()
            val clauseDefinitionParametersToEliminate = HashSet<DependentLink>()

            val patterns = ArrayList<Concrete.Pattern>()
            val matchDataConcrete = HashMap<DependentLink, ConcreteVariableLocationDescriptor>()
            val processedConcreteSubstitutions = HashMap<Referable, Concrete.Expression>()
        }

        private sealed class ExpectedConstructorQuickFixRunner<K : ClauseEntry>(
            val thisError: ExpectedConstructorError,
            val project: Project,
            val concreteDefinition: Concrete.GeneralDefinition,
            val typecheckedDefinition: Definition) {

            val errorHintBuffer = StringBuffer()
            val server = project.service<ArendServerService>().server
            val definition = thisError.definition as TCDefReferable
            val definitionPsi = definition.data
            val clauseToEntryMap = HashMap<Abstract.Clause, ClauseEntry>()
            private val entriesToRemove = ArrayList<ClauseEntry>()

            val arendFile = (definitionPsi as? PsiElement)?.containingFile as? ArendFile
            val serverRenamer = arendFile?.let { ServerBasedDefinitionRenamer(server, DummyErrorReporter.INSTANCE,
            typecheckedDefinition.referable ?: FullModuleReferable(arendFile.moduleLocation), arendFile) }
            val renamingVisitor = serverRenamer?.let { DefinitionRenamerConcreteVisitor(serverRenamer) }

            fun reportError(ecEntry: ClauseEntry, currentClause: Abstract.Clause) {
                entriesToRemove.add(ecEntry)
                errorHintBuffer.append("Constructor: ${ecEntry.constructorTypechecked.name}\n")
                errorHintBuffer.append("Patterns of the constructor: ${ecEntry.constructorTypechecked.patterns.map{ it.toExpression() }.toList()}\n")
                errorHintBuffer.append("Containing clause: ${(currentClause as PsiElement).text}\n")
            }

            fun runQuickFix(editor: Editor?) {
                var typecheckedParameters: DependentLink? = null

                if (definitionPsi is Abstract.Definition) {
                    var elimParams: List<DependentLink> = emptyList()
                    val expectedConstructorErrorEntries = ArrayList<K>()

                    run {
                        val errorReporter = ListErrorReporter()
                        val data = preparePatternTypechecking(concreteDefinition)
                        typecheckedParameters = data.typecheckedParameters

                        val context = HashMap<Referable, Binding>()
                        if (data.parameters != null) for (pair in data.parameters.map { it.referableList }.flatten().zip(DependentLink.Helper.toList(typecheckedParameters))) context[pair.first] = pair.second
                        elimParams = if (data.eliminatedReferences != null)
                            ElimTypechecking.getEliminatedParameters(data.eliminatedReferences, data.clauses, typecheckedParameters, errorReporter, context) else emptyList()

                        val typechecker = CheckTypeVisitor(errorReporter, null, null)

                        if (data.clauses != null && data.mode != null) for (clause in data.clauses) {
                            val substitution = ExprSubstitution()
                            errorReporter.errorList.clear()

                            PatternTypechecking(data.mode, typechecker, false, thisError.caseExpressions, elimParams)
                                .typecheckPatterns(clause.patterns, data.parameters, typecheckedParameters, substitution, ExprSubstitution(), clause)

                            val relevantErrors = errorReporter.errorList.filterIsInstance<ExpectedConstructorError>()
                            if (relevantErrors.size == 1) {
                                val relevantError = relevantErrors[0]
                                val constructorTypechecked = (relevantError.referable as InternalReferableImpl).typechecked
                                if (constructorTypechecked is Constructor)
                                    expectedConstructorErrorEntries.add(createClauseEntry(relevantError, clause, substitution, constructorTypechecked))
                            }
                        }
                    }

                    val typecheckedDefinitionParameters = DependentLink.Helper.toList(typecheckedParameters)

                    for (ecEntry in expectedConstructorErrorEntries)
                        computeMatchingPatterns(ecEntry, typecheckedDefinitionParameters, elimParams)

                    for (ecEntry in expectedConstructorErrorEntries)
                        calculateEntriesToEliminate(ecEntry)

                    expectedConstructorErrorEntries.removeAll(entriesToRemove.toSet())

                    for (entry in expectedConstructorErrorEntries)
                        clauseToEntryMap[entry.clause.data as Abstract.Clause] = entry

                    val scope = server.getReferableScope(definition) ?: return

                    val newConcrete = rebuildDefinition(scope, expectedConstructorErrorEntries, elimParams)

                    val builder = StringBuilder()
                    val visitor = PrettyPrintVisitor(builder, 0)
                    when (newConcrete) {
                        is Concrete.Constructor -> visitor.prettyPrintConstructor(newConcrete)
                        is Concrete.DataDefinition -> visitor.visitData(newConcrete, null)
                        is Concrete.FunctionDefinition -> visitor.visitFunction(newConcrete, null)
                        is Concrete.CaseExpression -> newConcrete.accept(visitor, Precedence(Precedence.Associativity.NON_ASSOC, Concrete.CaseExpression.PREC, false))
                    }

                    // Modification of the file text starts here
                    val textFile = (definitionPsi as PsiElement).containingFile.fileDocument
                    val textRange = getTextRange()
                    textFile.replaceString(textRange.startOffset, textRange.endOffset, builder.toString())
                    PsiDocumentManager.getInstance(project).commitDocument(textFile)
                    serverRenamer?.getAction()?.execute()
                }

                if (editor != null && errorHintBuffer.isNotEmpty()) ApplicationManager.getApplication().invokeLater {
                    val text = errorHintBuffer.toString().trim()
                    HintManager.getInstance().showErrorHint(editor, text)
                }
            }

            abstract fun computeMatchingPatterns(ecEntry: K, definitionParameters: List<DependentLink>, elimParams: List<DependentLink>)
            abstract fun preparePatternTypechecking(concreteDefinition: Concrete.GeneralDefinition): PatternTypecheckingData
            abstract fun createClauseEntry(error: ExpectedConstructorError, clause: Concrete.Clause, substitution: ExprSubstitution, constructorTypechecked: Constructor): K
            abstract fun calculateEntriesToEliminate(ecEntry: K)

            abstract fun rebuildDefinition(scope: Scope, expectedConstructorErrorEntries: List<K>, elimParams: List<DependentLink>): Concrete.SourceNode
            abstract fun getTextRange(): TextRange

            companion object {
                fun reconstructList(scope: Scope,
                                    occupiedLocalNames: HashSet<Variable>,
                                    parameters: List<DependentLink>,
                                    concreteParameters: List<Referable>?,
                                    eliminatedParameters: Set<DependentLink>?,
                                    concretePatterns: List<Concrete.Pattern>,
                                    substitutions: Map<Variable, ExpressionPattern>?,
                                    sink: MutableMap<Referable, Concrete.Expression>,
                                    clauseLinkListIterator: Iterator<DependentLink>,
                                    sampleData: DataDefinition? = null,
                                    sampleName: String? = null): List<Concrete.Pattern> {
                    val result = ArrayList<Concrete.Pattern>()
                    val elimMode = eliminatedParameters != null
                    val sampleIterator = parameters.iterator()
                    val concreteParameterIterator = concreteParameters?.iterator()
                    val concreteIterator = concretePatterns.iterator()
                    var skippedPatterns = 0
                    var currentConcretePattern: Concrete.Pattern? = if (concreteIterator.hasNext()) concreteIterator.next() else null

                    while (sampleIterator.hasNext()) {
                        val sample = sampleIterator.next()
                        val concreteParameter = concreteParameterIterator?.next()

                        val sampleIsExplicit = if (elimMode) eliminatedParameters.contains(sample) else sample.isExplicit
                        val substitutedPattern = substitutions?.let{ it[sample] }
                        if (sampleIsExplicit == currentConcretePattern?.isExplicit) {
                            val (data, name) = if (sample.name != null && sample.typeExpr is DataCallExpression) Pair((sample.typeExpr as? DataCallExpression)?.definition, sample.name) else Pair(sampleData, sampleName)
                            val reconstructedPattern = reconstructPattern(scope, occupiedLocalNames, substitutedPattern, currentConcretePattern, substitutions, sink, clauseLinkListIterator, data, name)
                            result.add(reconstructedPattern)
                            currentConcretePattern = if (concreteIterator.hasNext()) concreteIterator.next() else null
                            skippedPatterns = 0
                            continue
                        } else if (!sampleIsExplicit) {
                            val pattern = reconstructPattern(scope, occupiedLocalNames, substitutedPattern,
                                Concrete.NamePattern(null, elimMode, concreteParameter, null), substitutions, sink, clauseLinkListIterator,
                                (sample.typeExpr as? DataCallExpression)?.definition, sample.name)
                            if (pattern !is Concrete.NamePattern) {
                                while (skippedPatterns > 0) {
                                    result.add(Concrete.NamePattern(null, false, null, null))
                                    skippedPatterns--
                                }
                                result.add(pattern)
                            } else if (!elimMode) {
                                skippedPatterns++
                            }
                        }
                    }

                    return result
                }

                fun findNamePatterns(concretePattern: Concrete.Pattern): List<Referable> {
                    return when (concretePattern) {
                        is Concrete.NamePattern -> concretePattern.referable?.let{ singletonList(it) } ?: emptyList()
                        is Concrete.ConstructorPattern -> concretePattern.patterns.map { findNamePatterns(it) }.fold(ArrayList()) { acc, el -> acc.addAll(el); acc}
                        is Concrete.TuplePattern -> concretePattern.patterns.map { findNamePatterns(it) }.fold(ArrayList()) { acc, el -> acc.addAll(el); acc}
                        else -> emptyList()
                    }
                }

                fun reconstructPattern(scope: Scope,
                                       occupiedLocalNames: HashSet<Variable>,
                                       substitutedCorePattern: ExpressionPattern?,
                                       concretePattern: Concrete.Pattern,
                                       substitutions: Map<Variable, ExpressionPattern>?,
                                       sink: MutableMap<Referable, Concrete.Expression>,
                                       clauseLinkListIterator: Iterator<DependentLink>,
                                       sampleData: DataDefinition? = null,
                                       sampleName: String? = null): Concrete.Pattern {
                    var mismatched = false
                    when (concretePattern) {
                        is Concrete.NamePattern -> {
                            val correspondingClauseParameter = if (clauseLinkListIterator.hasNext()) clauseLinkListIterator.next() else null
                            val corePattern = substitutedCorePattern ?: substitutions?.let{ if (correspondingClauseParameter != null) it[correspondingClauseParameter] else null }
                            if (corePattern != null && corePattern !is BindingPattern) {
                                val toAbstractVisitor = ArendRefactoringToAbstractVisitor(scope, occupiedLocalNames, sampleData, sampleName)
                                val pair = toAbstractVisitor.convertPattern(corePattern, concretePattern.isExplicit)
                                concretePattern.referable?.let { sink[it] = pair.proj2 }
                                pair.proj1.asReferable = concretePattern.asReferable
                                return pair.proj1
                            }
                        }
                        is Concrete.ConstructorPattern -> {
                            val existingConstructor = concretePattern.constructor as TCDefReferable
                            val substitutedConstructor = (substitutedCorePattern?.constructor)?.referable

                            val constructorParameters = DependentLink.Helper.toList(existingConstructor.typechecked.parameters)
                            val newSubstitutions = HashMap<Variable, ExpressionPattern>()
                            if (substitutions != null) newSubstitutions.putAll(substitutions)
                            if (substitutedCorePattern is ConstructorExpressionPattern) for ((constructorParameter, matchedPattern) in constructorParameters.zip(substitutedCorePattern.subPatterns))
                                newSubstitutions[constructorParameter] = matchedPattern

                            val reconstructedPatterns = reconstructList(scope, occupiedLocalNames, constructorParameters, null, null, concretePattern.patterns, newSubstitutions, sink, clauseLinkListIterator, sampleData, sampleName)
                            mismatched = substitutedCorePattern != null && (substitutedCorePattern !is ConstructorExpressionPattern || substitutedConstructor != existingConstructor)
                            if (!mismatched)
                                return Concrete.ConstructorPattern(null, concretePattern.isExplicit, concretePattern.constructorData, existingConstructor, reconstructedPatterns, concretePattern.asReferable)
                        }
                        is Concrete.NumberPattern -> if (substitutedCorePattern != null && substitutedCorePattern.match(SmallIntegerExpression(concretePattern.number), ArrayList()) != Decision.YES) {
                            mismatched = true
                        }
                        is Concrete.TuplePattern -> {
                            if (substitutedCorePattern is ConstructorExpressionPattern && substitutedCorePattern.constructor == null && substitutedCorePattern.subPatterns.size == concretePattern.patterns.size) {
                                val tuplePatterns = concretePattern.patterns.zip(substitutedCorePattern.subPatterns).map {
                                        (concretePattern, substitutedCorePattern) ->
                                    reconstructPattern(scope, occupiedLocalNames, substitutedCorePattern, concretePattern, substitutions, sink, clauseLinkListIterator)
                                }
                                return Concrete.TuplePattern(null, concretePattern.isExplicit, tuplePatterns, concretePattern.asReferable)
                            } else if (substitutedCorePattern == null) {
                                val tuplePatterns = concretePattern.patterns.map { reconstructPattern(scope,occupiedLocalNames, null, it, substitutions, sink, clauseLinkListIterator) }.toList()
                                return Concrete.TuplePattern(null, concretePattern.isExplicit, tuplePatterns, concretePattern.asReferable)
                            } else {
                                mismatched = true
                            }
                        }
                    }
                    if (mismatched) {
                        val mismatchedReferables = findNamePatterns(concretePattern)
                        for (ref in mismatchedReferables) sink[ref] = Concrete.GoalExpression(null, ref.refName, null)
                        val toAbstractVisitor = ArendRefactoringToAbstractVisitor(scope, occupiedLocalNames, sampleData, sampleName)
                        val concreteSubstitutedPattern = toAbstractVisitor.convertPattern(substitutedCorePattern, concretePattern.isExplicit)
                        return concreteSubstitutedPattern.proj1
                    }

                    val collector = object: SearchConcreteVisitor<Void, Void>() {
                        override fun visitReferable(referable: Referable?, params: Void?) {
                            referable?.refName?.let { occupiedLocalNames.add(VariableImpl(it)) }
                        }
                    }

                    collector.visitPattern(concretePattern, null)

                    return concretePattern
                }
            }
        }

        data class PatternTypecheckingData(val mode: PatternTypechecking.Mode?,
                                           val typecheckedParameters: DependentLink?,
                                           val clauses: List<Concrete.Clause>?,
                                           val eliminatedReferences: List<Concrete.ReferenceExpression>?,
                                           val parameters: List<Concrete.Parameter>?)

        private class CaseExpectedConstructorErrorQuickFix(thisError: ExpectedConstructorError,
                                                           project: Project,
                                                           val caseExprPsi: ArendCaseExpr,
                                                           val concreteCaseExpression: Concrete.CaseExpression,
                                                           concreteDefinition: Concrete.GeneralDefinition,
                                                           typecheckedDefinition: Definition):
            ExpectedConstructorQuickFixRunner<CaseClauseEntry>(thisError, project, concreteDefinition, typecheckedDefinition) {
            private val concreteCaseBindings = LinkedHashMap<Binding, Pair<Concrete.CaseArgument /* case arg that depends on this binding */ , Expression /* corresponding expression */>>()
            private val concreteCaseTypeQualifications = HashMap<Concrete.CaseArgument, DataCallExpression>()
            private val concreteParameterToCaseArgMap = HashMap<DependentLink, Concrete.CaseArgument>()
            private val parameterToCaseExprMap = ExprSubstitution()

            override fun getTextRange(): TextRange = caseExprPsi.textRange

            override fun computeMatchingPatterns(ecEntry: CaseClauseEntry, definitionParameters: List<DependentLink>, elimParams: List<DependentLink>) {
                val parameterType = ecEntry.error.parameter?.typeExpr
                val currentClause = ecEntry.clause.data as Abstract.Clause

                // STEP 1C: Compute matching expressions
                ecEntry.matchedDataCall = if (parameterType is DataCallExpression) ExpressionMatcher.computeMatchingExpressions(parameterType, ecEntry.constructorTypechecked, true, ecEntry.matchedSubstitutions) else null
                if (ecEntry.matchedDataCall == null) {
                    errorHintBuffer.append("ExpectedConstructorError quickfix was unable to compute matching expressions for the case parameter ${ecEntry.error.parameter}\n")
                    reportError(ecEntry, currentClause)
                    return
                }

                if (ecEntry.error.caseExpressions != null) {
                    for (triple in DependentLink.Helper.toList(thisError.clauseParameters)
                        .zip(ecEntry.error.caseExpressions.zip(concreteCaseExpression.arguments))) {
                        parameterToCaseExprMap.add(triple.first, triple.second.first)
                        concreteParameterToCaseArgMap[triple.first] = triple.second.second
                    }
                }
            }

            override fun preparePatternTypechecking(concreteDefinition: Concrete.GeneralDefinition): PatternTypecheckingData {
                val eliminatedReferences = when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> (concreteDefinition.body as? Concrete.ElimFunctionBody)?.eliminatedReferences
                    is Concrete.DataDefinition -> concreteDefinition.eliminatedReferences
                    is Concrete.Constructor -> concreteDefinition.eliminatedReferences
                    else -> null
                }

                return PatternTypecheckingData(PatternTypechecking.Mode.CASE, thisError.clauseParameters, concreteCaseExpression.clauses, eliminatedReferences, null)
            }

            override fun createClauseEntry(error: ExpectedConstructorError, clause: Concrete.Clause, substitution: ExprSubstitution, constructorTypechecked: Constructor) =
                CaseClauseEntry(error, clause, substitution, constructorTypechecked)

            override fun calculateEntriesToEliminate(ecEntry: CaseClauseEntry) {
                // STEP 2C: Calculate the list of expressions which need to be eliminated or specialized
                val matchedDataCall = ecEntry.matchedDataCall
                if (matchedDataCall != null) {
                    val stuckParameter = ecEntry.error.parameter
                    val correspondingConcreteCaseArg = concreteParameterToCaseArgMap[stuckParameter]!!

                    val concreteSampleQualification = concreteCaseTypeQualifications[correspondingConcreteCaseArg]
                    if (concreteSampleQualification == null) {
                        concreteCaseTypeQualifications[correspondingConcreteCaseArg] = matchedDataCall

                        for (mr in ecEntry.matchedSubstitutions) {
                            concreteCaseBindings[mr.binding] = Pair(correspondingConcreteCaseArg, mr.expression)
                            val mrPattern = mr.pattern
                            if (mrPattern !is BindingPattern && mrPattern is ExpressionPattern)
                                ecEntry.correctedSubsts[mr.binding] = mrPattern
                        }
                    } else {
                        val substitution = ExprSubstitution()
                        sBELoop@for (sampleBindingEntry in concreteCaseBindings) for (matchResult in ecEntry.matchedSubstitutions) {
                            val expr1 = matchResult.expression
                            val expr2 = sampleBindingEntry.value.second
                            if (expr1 == expr2) {
                                substitution.add(matchResult.binding, ReferenceExpression(sampleBindingEntry.key))
                                continue@sBELoop
                            }
                        }
                        val cmdType = matchedDataCall.accept(SubstVisitor(substitution, LevelSubstitution.EMPTY), null)
                        if (cmdType != concreteSampleQualification) {
                            errorHintBuffer.append("Calculated type expressions for the case argument do not match between the clauses")
                            reportError(ecEntry, ecEntry.clause.data as Abstract.Clause)
                        } else for (matchResult in ecEntry.matchedSubstitutions) {
                            val matchedCaseBinding = (substitution.get(matchResult.binding) as? ReferenceExpression)?.binding
                            val mrPattern = matchResult.pattern
                            if (matchedCaseBinding != null && mrPattern !is BindingPattern && mrPattern is ExpressionPattern)
                                ecEntry.correctedSubsts[matchedCaseBinding] = mrPattern
                        }
                    }
                }
            }

            override fun rebuildDefinition(
                scope: Scope,
                expectedConstructorErrorEntries: List<CaseClauseEntry>,
                elimParams: List<DependentLink>
            ): Concrete.CaseExpression {
                val bindingToCaseArgMap = HashMap<Binding, Concrete.CaseArgument>()
                val newCaseArgs = ArrayList<Pair<Concrete.CaseArgument, Concrete.CaseArgument?>>(concreteCaseExpression.arguments.map { Pair(it, it) })

                val ppConfig = if (serverRenamer != null) object : PrettyPrinterConfig { override fun getDefinitionRenamer(): DefinitionRenamer = serverRenamer } else PrettyPrinterConfig.DEFAULT
                val occupiedNames = HashSet<String>()
                val localReferables = HashMap<Any, Referable>()

                for (clause in concreteCaseExpression.clauses) clause.expression?.data?.let {
                    val collector = LocalVariablesCollector(it)
                    (concreteDefinition as? Concrete.BaseFunctionDefinition)?.accept(collector, null)
                    occupiedNames.addAll(collector.names)
                    collector.result?.forEach { container -> if (container is DataContainer) container.data?.let { d -> localReferables[d] = container } }
                }

                cBLoop@for ((binding, pair) in concreteCaseBindings) {
                    val (dependentCaseArg, expression) = pair

                    val caseBindingUsedInSubsts = expectedConstructorErrorEntries.any { it.correctedSubsts.keys.contains(binding) }
                    if (!caseBindingUsedInSubsts) continue@cBLoop
                    val expressionAfterSubstitution = expression.accept(SubstVisitor(parameterToCaseExprMap, LevelSubstitution.EMPTY), null)

                    val compositeMap = HashMap<Binding, LocalReferable>()
                    for (binding in thisError.myDataContext) (localReferables[binding.value] as? LocalReferable)?.let { compositeMap[binding.key] = it }
                    doInsertCaseArgsConcrete(ppConfig, concreteCaseExpression.arguments, newCaseArgs, binding,
                        expressionAfterSubstitution, dependentCaseArg, thisError.caseExpressions,
                        occupiedNames, bindingToCaseArgMap, concreteCaseTypeQualifications, compositeMap)
                }

                val referableRenamer = object: ReferableRenamer() {
                    override fun generateFreshNames(variables: Collection<Variable?>?) {
                        super.generateFreshNames(variables?.filter { !bindingToCaseArgMap.keys.contains(it) })
                    }
                }
                for ((binding, caseArg) in bindingToCaseArgMap.entries) {
                    val caseReferable = caseArg.referable
                    if (caseReferable is LocalReferable)
                        referableRenamer.addNewName(binding, caseReferable)
                }

                for (caseArg in newCaseArgs) {
                    val qualification = concreteCaseTypeQualifications[caseArg.second]
                    if (qualification != null) caseArg.first.type = ToAbstractVisitor.convert(qualification, ppConfig, referableRenamer)
                    caseArg.first.referable?.refName?.let { occupiedNames.remove(it) }
                }


                val newClauses = ArrayList<Concrete.FunctionClause>()
                for (clause in concreteCaseExpression.clauses) {
                    val patterns = ArrayList<Concrete.Pattern>()
                    val entry = clauseToEntryMap[clause.data]
                    val correctedSubstsNew = HashMap<Variable, ExpressionPattern>()
                    entry?.correctedSubsts?.let { correctedSubstsNew.putAll(it) }
                    val sink = HashMap<Referable, Concrete.Expression>()
                    val newPatterns = HashMap<Concrete.CaseArgument, Concrete.Pattern>()
                    val occupiedLocalNames = HashSet<Variable>()
                    occupiedNames.forEach { occupiedLocalNames.add(VariableImpl(it)) }

                    if (entry != null) for (substEntry in entry.correctedSubsts) {
                        val newCaseArg = bindingToCaseArgMap[substEntry.key]!!
                        val oldCaseArg = newCaseArgs.first { it.first == newCaseArg }.second
                        val index = concreteCaseExpression.arguments.indexOf(oldCaseArg)

                        if (index != -1) {
                            newPatterns[newCaseArg] = reconstructPattern(scope, occupiedLocalNames, substEntry.value, clause.patterns[index], correctedSubstsNew, sink, emptyList<DependentLink>().iterator())
                        } else {
                            val localReferable = (newCaseArg.expression as? Concrete.ReferenceExpression)?.referent as? LocalReferable
                            newPatterns[newCaseArg] = reconstructPattern(scope, occupiedLocalNames, substEntry.value, Concrete.NamePattern(null, true, localReferable ?: newCaseArg.referable, null),
                                correctedSubstsNew, sink, emptyList<DependentLink>().iterator(), ((substEntry.key as? TypedBinding)?.typeExpr as? DataCallExpression)?.definition, newCaseArg.referable?.refName)
                        }
                    }

                    for (caseArg in newCaseArgs) {
                        val index = concreteCaseExpression.arguments.indexOf(caseArg.second)
                        patterns.add(newPatterns[caseArg.first] ?: if (index == -1) {
                            Concrete.NamePattern(null, true, LocalReferable(caseArg.first.referable!!.refName), null)
                        } else {
                            clause.patterns[index]
                        })
                    }

                    val substVisitor = SubstConcreteVisitor(sink, null)
                    val substResult = clause.expression.accept(substVisitor, null)
                    val renamedExpression = if (renamingVisitor != null) substResult.accept(renamingVisitor, null) else substResult

                    newClauses.add(Concrete.FunctionClause(null, patterns, renamedExpression))
                }

                return Concrete.CaseExpression(null, concreteCaseExpression.isSCase, newCaseArgs.map {
                    Concrete.CaseArgument(it.first.expression.accept(renamingVisitor, null),
                        it.first.referable,
                        it.first.type?.accept(renamingVisitor, null), it.first.isElim) }, concreteCaseExpression.resultType,
                    concreteCaseExpression.resultTypeLevel, newClauses)
            }

            private fun doInsertCaseArgsConcrete(ppConfig: PrettyPrinterConfig,
                                                 oldCaseArgs: List<Concrete.CaseArgument>,
                                                 newCaseArgs: MutableList<Pair<Concrete.CaseArgument, Concrete.CaseArgument?>>,
                                                 binding: Binding,
                                                 expression: Expression,
                                                 dependentCaseArg: Concrete.CaseArgument,
                                                 caseExpressions: List<Expression>,
                                                 caseOccupiedLocalNames: HashSet<String>,
                                                 bindingToCaseArgMap: HashMap<Binding, Concrete.CaseArgument>,
                                                 typeQualifications: Map<Concrete.CaseArgument, DataCallExpression>,
                                                 localReferables: Map<Binding, LocalReferable>) {
                val renamer = StringRenamer()
                val occupiedVars = caseOccupiedLocalNames.map { VariableImpl(it) }
                val freshName = if (expression is ReferenceExpression) expression.binding.name else
                    renamer.generateFreshName(TypedBinding(null, binding.typeExpr), occupiedVars)
                var replaceKey: Concrete.CaseArgument? = null
                var replaceValue: Concrete.CaseArgument? = null
                var foundExpression = false

                for (ce in caseExpressions.zip(oldCaseArgs))
                    if (expression == ce.first) {
                        foundExpression = true
                        bindingToCaseArgMap[binding] = if (ce.second.referable == null) {
                            val type = typeQualifications[ce.second]?.let { ToAbstractVisitor.convert(it, ppConfig) } ?: ce.second.type
                            val newBinding = Concrete.CaseArgument(ce.second.expression, LocalReferable(freshName), type)
                            replaceKey = ce.second
                            replaceValue = newBinding
                            newBinding
                        } else
                            ce.second

                        break
                    }

                if (replaceKey != null && replaceValue != null) {
                    val index = newCaseArgs.indexOfFirst {it.second == replaceKey}
                    if (index != -1) {
                        newCaseArgs.removeAt(index)
                        newCaseArgs.add(index, Pair(replaceValue, replaceKey))
                    }
                }

                if (foundExpression) return

                val referableRenamer = object : ReferableRenamer() {
                    override fun generateFreshNames(variables: Collection<Variable?>?) {
                        super.generateFreshNames(variables?.filter { !localReferables.keys.contains(it) })
                    }
                }
                for (entry in localReferables) referableRenamer.addNewName(entry.key, entry.value)
                val abstractExpr = ToAbstractVisitor.convert(expression, ppConfig, referableRenamer)
                val newCaseArg = Concrete.CaseArgument(abstractExpr, LocalReferable(freshName), null)
                caseOccupiedLocalNames.add(freshName)
                val index = newCaseArgs.indexOfFirst {it.second == dependentCaseArg}
                newCaseArgs.add(index, Pair(newCaseArg, null))
                bindingToCaseArgMap[binding] = newCaseArg
            }
        }

        private class ElimWithExpectedConstructorErrorQuickFix(
            thisError: ExpectedConstructorError,
            project: Project,
            concreteDefinition: Concrete.GeneralDefinition,
            val constructorPsi: PsiElement?,
            typecheckedDefinition: Definition):
            ExpectedConstructorQuickFixRunner<ElimClauseEntry>(thisError, project, concreteDefinition, typecheckedDefinition) {
            private val definitionParametersToEliminate = HashSet<DependentLink>() // Subset of definitionParameters (global)

            override fun getTextRange(): TextRange =
                (concreteDefinition.data.data as PsiElement).textRange

            override fun computeMatchingPatterns(ecEntry: ElimClauseEntry, definitionParameters: List<DependentLink>, elimParams: List<DependentLink>) {
                val parameterType = ecEntry.error.parameter?.typeExpr
                val currentClause = ecEntry.clause.data as Abstract.Clause
                val concreteCurrentClause = ecEntry.clause

                // STEP 1: Compute matching patterns
                val rawSubsts = HashMap<Binding, ExpressionPattern>()
                if (parameterType !is DataCallExpression || ExpressionMatcher.computeMatchingPatterns(parameterType, ecEntry.constructorTypechecked, ExprSubstitution(), rawSubsts) == null) {
                    errorHintBuffer.append("ExpectedConstructorError quickfix was unable to compute matching patterns for the parameter ${ecEntry.error.parameter}\n")
                    reportError(ecEntry, currentClause)
                    return
                }

                // STEP 2: Calculate lists of variables which need to be eliminated or specialized
                val clauseParameters = DependentLink.Helper.toList(ecEntry.error.patternParameters)
                val definitionToClauseMap = HashMap<Variable, Variable>()
                val clauseToDefinitionMap = HashMap<Variable, Variable>()

                for (variable in ecEntry.substitution.keys) {
                    val binding = (ecEntry.substitution.get(variable) as? ReferenceExpression)?.binding
                    if (binding != null && variable is Binding && definitionParameters.contains(variable) && clauseParameters.contains(binding)) {
                        definitionToClauseMap[variable] = binding
                        clauseToDefinitionMap[binding] = variable
                    }
                }

                //This piece of code filters out trivial substitutions and also ensures that the key of each substitution is either an element of definitionParametersToEliminate or clauseParametersToSpecialize
                for (subst in rawSubsts) if (subst.value !is BindingPattern) {
                    if (definitionParameters.contains(subst.key)) {
                        ecEntry.clauseDefinitionParametersToEliminate.add(subst.key as DependentLink)
                        ecEntry.correctedSubsts[subst.key] = subst.value
                    } else {
                        val localClauseBinding =
                            if (clauseParameters.contains(subst.key)) subst.key else (ecEntry.substitution[subst.key] as? ReferenceExpression)?.binding
                        if (localClauseBinding != null) {
                            val definitionBinding = clauseToDefinitionMap[localClauseBinding]
                            if (definitionBinding != null && definitionParameters.contains(definitionBinding)) {
                                ecEntry.correctedSubsts[definitionBinding] = subst.value
                                ecEntry.clauseDefinitionParametersToEliminate.add(definitionBinding as DependentLink)
                            } else if (clauseParameters.contains(localClauseBinding)) {
                                ecEntry.correctedSubsts[localClauseBinding] = subst.value
                                ecEntry.clauseParametersToSpecialize.add(localClauseBinding)
                            }
                        }
                    }
                }

                //STEP 3: Match clauseParameters with currentClause PSI
                if (!matchConcreteWithWellTyped(currentClause as PsiElement, concreteCurrentClause,
                        concreteCurrentClause.patterns, prepareExplicitnessMask(definitionParameters, elimParams),
                        clauseParameters.iterator(), ecEntry.matchData, ecEntry.matchDataConcrete) ||
                    ecEntry.clauseParametersToSpecialize.any { ecEntry.matchData[it] == null })
                    throw IllegalStateException("ExpectedConstructorError quickfix failed to calculate the correspondence between psi and concrete name patterns")
            }

            override fun preparePatternTypechecking(concreteDefinition: Concrete.GeneralDefinition): PatternTypecheckingData {
                val eliminatedReferences = when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> (concreteDefinition.body as? Concrete.ElimFunctionBody)?.eliminatedReferences
                    is Concrete.DataDefinition -> concreteDefinition.eliminatedReferences
                    is Concrete.Constructor -> concreteDefinition.eliminatedReferences
                    else -> null
                }

                val parameters = when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> concreteDefinition.parameters
                    is Concrete.DataDefinition -> concreteDefinition.parameters
                    is Concrete.Constructor -> concreteDefinition.parameters
                    else -> null
                }

                val typecheckedParameters = when {
                    constructorPsi != null -> (definition.typechecked as? DataDefinition)?.constructors?.firstOrNull { it.referable.data == constructorPsi }?.parameters
                    else -> definition.typechecked?.parameters
                } ?: EmptyDependentLink.getInstance()

                val clauses = when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> (concreteDefinition.body as? Concrete.ElimFunctionBody)?.clauses
                    is Concrete.DataDefinition -> concreteDefinition.constructorClauses
                    is Concrete.Constructor -> concreteDefinition.clauses
                    else -> null
                }

                val mode = when (concreteDefinition) {
                    is Concrete.BaseFunctionDefinition -> PatternTypechecking.Mode.FUNCTION
                    is Concrete.DataDefinition -> PatternTypechecking.Mode.DATA
                    is Concrete.Constructor -> PatternTypechecking.Mode.CONSTRUCTOR
                    else -> null
                }

                return PatternTypecheckingData(mode, typecheckedParameters, clauses, eliminatedReferences, parameters)
            }

            override fun createClauseEntry(error: ExpectedConstructorError, clause: Concrete.Clause, substitution: ExprSubstitution, constructorTypechecked: Constructor) =
                ElimClauseEntry(error, clause, substitution, constructorTypechecked)

            override fun calculateEntriesToEliminate(ecEntry: ElimClauseEntry) {
                definitionParametersToEliminate.addAll(ecEntry.clauseDefinitionParametersToEliminate)
            }

            override fun rebuildDefinition(scope: Scope, expectedConstructorErrorEntries: List<ElimClauseEntry>, elimParams: List<DependentLink>): Concrete.GeneralDefinition {
                val oldEliminatedReferences = ArrayList<Concrete.ReferenceExpression>()
                val newEliminatedReferences = ArrayList<Concrete.ReferenceExpression>()

                when (concreteDefinition) {
                    is Concrete.FunctionDefinition -> oldEliminatedReferences.addAll((concreteDefinition.body as Concrete.ElimFunctionBody).eliminatedReferences)
                    is Concrete.DataDefinition -> oldEliminatedReferences.addAll(
                        concreteDefinition.eliminatedReferences ?: emptyList()
                    )

                    is Concrete.Constructor -> oldEliminatedReferences.addAll(concreteDefinition.eliminatedReferences)
                    else -> throw IllegalStateException()
                }

                val typecheckedParameters =
                    DependentLink.Helper.toList(typecheckedDefinition.parameters).filter { !it.isHidden }
                val concreteParameters = concreteDefinition.parameters.map { p -> p.referableList }.flatten().toList()

                val isWithMode = oldEliminatedReferences.isEmpty()

                for ((concreteParameter, typecheckedParameter) in concreteParameters.zip(typecheckedParameters)) { //TODO: This may not correctly process 100% of cases
                    if (oldEliminatedReferences.indexOfFirst { it.referent == concreteParameter } != -1 ||
                        definitionParametersToEliminate.contains(typecheckedParameter) && !isWithMode) {
                        newEliminatedReferences.add(Concrete.ReferenceExpression(null, concreteParameter))
                    }
                }

                for (clauseEntry in expectedConstructorErrorEntries) {
                    val clauseParameters = DependentLink.Helper.toList(clauseEntry.error.patternParameters)
                    clauseEntry.patterns.addAll(reconstructList(scope, HashSet(), typecheckedParameters, concreteParameters, if (isWithMode) null else elimParams.toSet(), clauseEntry.clause.patterns, clauseEntry.correctedSubsts, clauseEntry.processedConcreteSubstitutions, clauseParameters.iterator()))
                }

                val clauses = ArrayList<Concrete.FunctionClause>()
                val constructorClauses = ArrayList<Concrete.ConstructorClause>()

                for (entry in expectedConstructorErrorEntries) {
                    val patterns = entry.patterns
                    val substVisitor = SubstConcreteVisitor(entry.processedConcreteSubstitutions, null)

                    when (entry.clause) {
                        is Concrete.ConstructorClause -> {
                            val constructors = ArrayList<Concrete.Constructor>()
                            for (constructor in entry.clause.constructors) {
                                val typeParameters = ArrayList<Concrete.TypeParameter>()
                                for (parameter in constructor.parameters) {
                                    val type = parameter.type.accept(substVisitor, null)
                                    val renamedType = if (renamingVisitor != null) type.accept(renamingVisitor, null) else type
                                    typeParameters.add(Concrete.TypeParameter(parameter.isExplicit, renamedType, parameter.isProperty ))
                                }
                                val newConstructor = Concrete.Constructor(constructor.data, typeParameters, constructor.eliminatedReferences, constructor.clauses, constructor.isCoerce)
                                constructors.add(newConstructor)
                            }
                            constructorClauses.add(Concrete.ConstructorClause(entry.clause.data, patterns, constructors))
                        }
                        is Concrete.FunctionClause -> {
                            val newExpression = entry.clause.expression.accept(substVisitor, null)
                            val renamedExpression = if (renamingVisitor != null) newExpression.accept(renamingVisitor, null) else newExpression
                            clauses.add(Concrete.FunctionClause(entry.clause.data, patterns, renamedExpression))
                        }
                    }
                }

                when (concreteDefinition) {
                    is Concrete.Constructor -> return Concrete.Constructor(concreteDefinition.data, concreteDefinition.parameters, newEliminatedReferences, clauses, concreteDefinition.isCoerce)
                    is Concrete.DataDefinition -> {
                        return Concrete.DataDefinition(concreteDefinition.data, concreteDefinition.pLevelParameters, concreteDefinition.hLevelParameters, concreteDefinition.parameters,
                            newEliminatedReferences, concreteDefinition.isTruncated, concreteDefinition.universe, constructorClauses)
                    }

                    is Concrete.FunctionDefinition -> {
                        val oldBody = concreteDefinition.body as Concrete.ElimFunctionBody
                        val newBody = Concrete.ElimFunctionBody(oldBody.data, newEliminatedReferences, clauses)
                        return Concrete.FunctionDefinition(concreteDefinition.kind, concreteDefinition.data, concreteDefinition.pLevelParameters, concreteDefinition.hLevelParameters,
                            concreteDefinition.parameters, concreteDefinition.resultType, concreteDefinition.resultTypeLevel, newBody)
                    }
                }


                return concreteDefinition
            }
        }

        fun prepareExplicitnessMask(sampleParameters: List<DependentLink>, elimParams: List<DependentLink>?): List<Boolean> =
            if (!elimParams.isNullOrEmpty()) sampleParameters.map { elimParams.contains(it) } else sampleParameters.map { it.isExplicit }

        interface VariableLocationDescriptor
        data class ExplicitNamePattern(val bindingPsi: PsiElement) : VariableLocationDescriptor
        data class ImplicitConstructorPattern(val enclosingNode: PsiElement, val followingParameter: PsiElement?, val implicitArgCount: Int) : VariableLocationDescriptor

        interface ConcreteVariableLocationDescriptor
        data class ConcreteExplicitNamePattern(val bindingPsi: Concrete.Pattern) : ConcreteVariableLocationDescriptor
        data class ConcreteImplicitConstructorPattern(val enclosingNode: Concrete.SourceNode, val followingParameter: Concrete.Pattern?, val implicitArgCount: Int) : ConcreteVariableLocationDescriptor

        fun matchConcreteWithWellTyped(enclosingNode: PsiElement,
                                       enclosingConcrete: Concrete.SourceNode,
                                       patterns: List<Concrete.Pattern>,
                                       explicitnessMask: List<Boolean>,
                                       patternParameterIterator: MutableIterator<DependentLink>,
                                       result: MutableMap<DependentLink, VariableLocationDescriptor>,
                                       sink: MutableMap<DependentLink, ConcreteVariableLocationDescriptor>): Boolean {
            var concretePatternIndex = 0
            var skippedParameters = 0
            for (spExplicit in explicitnessMask) {
                if (!patternParameterIterator.hasNext()) return true
                val concretePattern = if (concretePatternIndex < patterns.size) patterns[concretePatternIndex] else null
                val data = concretePattern?.data as? PsiElement

                if (concretePattern != null && data != null && concretePattern.isExplicit == spExplicit) {
                    skippedParameters = 0
                    concretePatternIndex++

                    when (concretePattern) {
                        is Concrete.TuplePattern -> {
                            matchConcreteWithWellTyped(data, concretePattern, concretePattern.patterns, List(concretePattern.patterns.size) { true }, patternParameterIterator, result, sink)
                        }
                        is Concrete.NamePattern -> {
                            val patternParameter = patternParameterIterator.next()
                            result[patternParameter] = ExplicitNamePattern(data)
                            sink[patternParameter] = ConcreteExplicitNamePattern(concretePattern)
                        }
                        is Concrete.ConstructorPattern -> {
                            val typechecked = (concretePattern.constructor as? TCDefReferable)?.typechecked as? Constructor
                                ?: return false
                            if (!matchConcreteWithWellTyped(data, concretePattern, concretePattern.patterns, prepareExplicitnessMask(DependentLink.Helper.toList(typechecked.parameters), null), patternParameterIterator, result, sink)) return false
                        }
                    }
                    continue
                }
                if (!spExplicit) {
                    val patternParameter = patternParameterIterator.next()
                    result[patternParameter] = ImplicitConstructorPattern(enclosingNode, data, skippedParameters)
                    sink[patternParameter] = ConcreteImplicitConstructorPattern(enclosingConcrete, concretePattern, skippedParameters)
                    skippedParameters += 1
                }
            }
            return true
        }
    }
}