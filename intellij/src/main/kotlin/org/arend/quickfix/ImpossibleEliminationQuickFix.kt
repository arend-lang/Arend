package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.nextLeaf
import org.arend.core.context.binding.Binding
import org.arend.core.context.binding.TypedBinding
import org.arend.core.context.param.DependentLink
import org.arend.core.context.param.DependentLink.Helper.toList
import org.arend.core.context.param.UntypedDependentLink
import org.arend.core.definition.DataDefinition
import org.arend.core.expr.DataCallExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.ReferenceExpression
import org.arend.core.pattern.BindingPattern
import org.arend.core.subst.ExprSubstitution
import org.arend.core.subst.SubstVisitor
import org.arend.ext.core.level.LevelSubstitution
import org.arend.ext.prettyprinting.DefinitionRenamer
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.renamer.StringRenamer
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ancestor
import org.arend.psi.ancestors
import org.arend.psi.descendantOfType
import org.arend.psi.ext.*
import org.arend.psi.findNextSibling
import org.arend.quickfix.removers.RemoveClauseQuickFix.Companion.doRemoveClause
import org.arend.refactoring.PsiLocatedRenamer
import org.arend.refactoring.findConcreteByPsi
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.LocalVariablesCollector
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.error.local.ImpossibleEliminationError
import org.arend.util.ArendBundle

class ImpossibleEliminationQuickFix(val error: ImpossibleEliminationError, val cause: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun getText(): String = ArendBundle.message("arend.pattern.doMatching")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        error.caseExpressions != null || error.elimParams != null // this prevents quickfix from showing in the "no matching constructor" case

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val server = project.service<ArendServerService>().server
        val psiFactory = ArendPsiFactory(project)
        val dataDefinition = error.defCall.definition as? DataDefinition ?: return
        val definition = error.definition as? TCDefReferable ?: return
        val definitionPsi = definition.data
        val concreteDefinition = server.getResolvedDefinition(definition)?.definition as Concrete.GeneralDefinition

        val constructorPsi = cause.element?.ancestor<ArendConstructor>()
        val typecheckedParameters = when {
            constructorPsi != null -> {
                (definition.typechecked as DataDefinition).constructors.firstOrNull { it.referable.data == constructorPsi }?.parameters
            }
            else -> definition.typechecked.parameters
        } ?: return

        val bodyPsi = (definitionPsi as? ArendFunctionDefinition<*>)?.body
        val dataBodyPsi = (definitionPsi as? ArendDefData)?.dataBody
        val elimPsi = when {
            bodyPsi != null -> bodyPsi.elim
            constructorPsi != null -> constructorPsi.elim
            dataBodyPsi != null -> dataBodyPsi.elim
            else -> null
        }

        val definitionParameters = toList(typecheckedParameters)

        val ddEliminatedParameters = HashSet<DependentLink>()
        for (constructor in dataDefinition.constructors) constructor.patterns?.let {
            for (p in it.zip(toList(dataDefinition.parameters))) if (p.first !is BindingPattern) ddEliminatedParameters.add(p.second)
        }

        // determine matching expressions
        val elimParams = error.elimParams

        if (error.caseExpressions != null) { // case
            val stuckParameter = error.myParameters
            val stuckParameterType = stuckParameter.type

            val caseExprPsi = cause.element?.ancestor<ArendCaseExpr>()
            val clausesListPsi = caseExprPsi?.withBody?.clauseList
            if (caseExprPsi != null && stuckParameterType is DataCallExpression && clausesListPsi != null) {
                val concreteCaseExpr = (concreteDefinition as? Concrete.Definition)?.let{ findConcreteByPsi(it, Concrete.CaseExpression::class.java, caseExprPsi) }
                val exprsToEliminate = stuckParameterType.defCallArguments.zip(toList(dataDefinition.parameters)).filter { ddEliminatedParameters.contains(it.second) }.toList()
                val sampleDataCall = DataCallExpression.make(dataDefinition, error.defCall.levels, toList(dataDefinition.parameters).map { it.makeReference() })
                val toActualParametersSubstitution = ExprSubstitution(); for (entry in stuckParameterType.defCallArguments.zip(toList(dataDefinition.parameters))) toActualParametersSubstitution.add(entry.second, entry.first)
                val oldCaseArgs = caseExprPsi.caseArguments
                val parameterToCaseArgMap = HashMap<DependentLink, ArendCaseArg>()
                val parameterToCaseExprMap = ExprSubstitution()
                val caseOccupiedLocalNames = HashSet<String>()
                if (concreteDefinition is Concrete.Definition && concreteCaseExpr != null)
                    doInitOccupiedLocalNames(concreteCaseExpr, concreteDefinition, caseOccupiedLocalNames)

                val bindingToCaseArgMap = HashMap<Binding, ArendCaseArg>()

                for (triple in toList(error.clauseParameters).zip(error.caseExpressions.zip(caseExprPsi.caseArguments))) {
                    parameterToCaseArgMap[triple.first] = triple.second.second
                    parameterToCaseExprMap.add(triple.first, triple.second.first)
                }

                val dependentCaseArg =
                    parameterToCaseArgMap[stuckParameter]!! //Safe as stuckParameter is one of error.clauseParameters
                for (expression in exprsToEliminate) {
                    val exprSubst =
                        expression.first.accept(SubstVisitor(parameterToCaseExprMap, LevelSubstitution.EMPTY), null)
                    doInsertCaseArgs(psiFactory, caseExprPsi, expression.second, exprSubst, dependentCaseArg, error.caseExpressions, caseOccupiedLocalNames, bindingToCaseArgMap)
                }

                val toInsertedBindingsSubstitution = ExprSubstitution(); for (e in bindingToCaseArgMap) toInsertedBindingsSubstitution.add(e.key, ReferenceExpression(UntypedDependentLink(e.value.referable!!.name)))

                val typeQualification = sampleDataCall.accept(SubstVisitor(toInsertedBindingsSubstitution, LevelSubstitution.EMPTY), null).accept(SubstVisitor(toActualParametersSubstitution, LevelSubstitution.EMPTY), null)
                doWriteTypeQualification(psiFactory, typeQualification, dependentCaseArg)

                doInsertPatternPrimers(psiFactory, clausesListPsi, caseExprPsi, oldCaseArgs, bindingToCaseArgMap)
            }
        } else
            if (elimParams != null && elimPsi != null) { // elim
                val clausesListPsi = when {
                    bodyPsi is ArendFunctionBody -> bodyPsi.functionClauses?.clauseList
                    constructorPsi != null -> constructorPsi.clauses
                    dataBodyPsi != null -> dataBodyPsi.constructorClauseList
                    else -> null
                }
                val clauseToDefinitionMap = HashMap<Variable, Variable>()

                if (error.substitution != null) for (variable in error.substitution.keys) {
                    val binding = (error.substitution.get(variable) as? ReferenceExpression)?.binding
                    if (binding != null && variable is Binding) clauseToDefinitionMap[binding] = variable
                }

                val definitionParametersToEliminate = HashSet<Variable>()
                val exprsToEliminate = toList(dataDefinition.parameters).zip(error.defCall.defCallArguments)
                    .filter { ddEliminatedParameters.contains(it.first) }.map { it.second }.toList()
                for (expr in exprsToEliminate) if (expr is ReferenceExpression) (clauseToDefinitionMap[expr.binding]
                    ?: expr.binding).let { definitionParametersToEliminate.add(it) }

                definitionParametersToEliminate.removeAll(elimParams.toSet())

                val paramsMap = HashMap<DependentLink, ArendRefIdentifier>()
                for (e in elimParams.zip(elimPsi.refIdentifierList)) paramsMap[e.first] = e.second

                doInsertElimVars(
                    psiFactory,
                    definitionParameters,
                    definitionParametersToEliminate,
                    elimPsi,
                    paramsMap
                )

                if (clausesListPsi != null) for (transformedClause in clausesListPsi)
                    doInsertPrimers(
                        psiFactory,
                        transformedClause,
                        definitionParameters,
                        elimParams,
                        definitionParametersToEliminate
                    ) { p -> p.name }
            }

        val clause = (error.cause.data as? PsiElement)?.ancestors?.firstOrNull{ it is ArendClause || it is ArendConstructorClause }
        if (clause != null) doRemoveClause(clause)
    }

    companion object {
        private fun doInsertPattern(psiFactory: ArendPsiFactory, anchor: PsiElement?, param: Binding,
                                    clause: PsiElement, nameCalculator: (Binding) -> String): PsiElement {
            val template = psiFactory.createClause("${nameCalculator.invoke(param)}, dummy").descendantOfType<ArendPattern>()!!
            val comma = template.nextSibling
            var commaInserted = false
            var insertedPsi : PsiElement?
            if (anchor != null) {
                insertedPsi = clause.addAfter(comma, anchor)
                commaInserted = true
                insertedPsi = clause.addAfter(template, insertedPsi ?: clause.firstChild)
                clause.addBefore(psiFactory.createWhitespace(" "), insertedPsi)
            } else {
                insertedPsi = clause.addBefore(template, clause.firstChild)
            }
            if (!commaInserted) clause.addAfter(comma, insertedPsi)
            return insertedPsi!!
        }


        fun doInsertPrimers(psiFactory: ArendPsiFactory,
                            clause: Abstract.Clause,
                            typecheckedParams: List<Binding>,
                            eliminatedParams: List<Variable>,
                            eliminatedVars: HashSet<Variable>,
                            nameCalculator: (Binding) -> String) {
            if (clause !is PsiElement) return
            val patternsMap = HashMap<Variable, ArendPattern>()
            for (e in eliminatedParams.zip(clause.patterns)) (e.second as? ArendPattern)?.let { patternsMap[e.first] = it }

            var anchor: PsiElement? = null

            for (param in typecheckedParams) anchor = if (eliminatedVars.contains(param))
                doInsertPattern(psiFactory, anchor, param, clause, nameCalculator)
            else patternsMap[param]
        }

        fun doInsertPatternPrimers(psiFactory: ArendPsiFactory, clausesListPsi: List<Abstract.Clause>, caseExprPsi: ArendCaseExpr, oldCaseArgs: List<ArendCaseArg>, bindingToCaseArgMap: Map<Binding, ArendCaseArg>) {
            for (clause in clausesListPsi) {
                val anchorMap = HashMap<ArendCaseArg, Abstract.Pattern>()
                val caseArgToBindingMap = HashMap<ArendCaseArg, Binding>()
                for (p in oldCaseArgs.zip(clause.patterns)) anchorMap[p.first] = p.second
                for (e in bindingToCaseArgMap) caseArgToBindingMap[e.value] = e.key

                var anchor: PsiElement? = null
                for (actualCaseArg in caseExprPsi.caseArguments) {
                    anchor = if (oldCaseArgs.contains(actualCaseArg)) {
                        anchorMap[actualCaseArg] as? PsiElement
                    } else {
                        doInsertPattern(psiFactory, anchor, caseArgToBindingMap[actualCaseArg]!!, clause as PsiElement) { binding ->
                            val matchingCaseArg = bindingToCaseArgMap[binding]
                            matchingCaseArg?.referable?.name ?: binding.name
                        }
                    }
                }
            }
        }

        fun doInsertCaseArgs(psiFactory: ArendPsiFactory,
                             caseExprPsi: ArendCaseExpr,
                             binding: Binding,
                             expression: Expression,
                             dependentCaseArg: ArendCaseArg,
                             caseExpressions: List<Expression>,
                             caseOccupiedLocalNames: HashSet<String>,
                             bindingToCaseArgMap: HashMap<Binding, ArendCaseArg>) {
            val renamer = StringRenamer()
            val ppConfig = object : PrettyPrinterConfig { override fun getDefinitionRenamer(): DefinitionRenamer = PsiLocatedRenamer(caseExprPsi) }
            val freshName = if (expression is ReferenceExpression) expression.binding.name else renamer.generateFreshName(TypedBinding(null, binding.typeExpr), caseOccupiedLocalNames.map{ VariableImpl(it) })
            caseOccupiedLocalNames.add(freshName)

            for (ce in caseExpressions.zip(caseExprPsi.caseArguments))
                if (expression == ce.first) {
                    bindingToCaseArgMap[binding] = ce.second
                    if (ce.second.referable == null) {
                        val newCaseArgExprAs = psiFactory.createCaseArg("0 \\as $freshName")
                        if (newCaseArgExprAs != null) ce.second.addRangeAfter(newCaseArgExprAs.firstChild.nextSibling, newCaseArgExprAs.lastChild, ce.second.expression)
                    }

                    return
                }

            val abstractExpr = ToAbstractVisitor.convert(expression, ppConfig)
            val newCaseArg = psiFactory.createCaseArg("$abstractExpr \\as $freshName")!!
            val comma = newCaseArg.findNextSibling()!!
            bindingToCaseArgMap[binding] = dependentCaseArg.parent.addBefore(newCaseArg, dependentCaseArg) as ArendCaseArg
            dependentCaseArg.parent.addBefore(comma, dependentCaseArg)
        }

        fun doWriteTypeQualification(psiFactory: ArendPsiFactory, expression: Expression, caseArg: ArendCaseArg) {
            val ppConfig = object : PrettyPrinterConfig { override fun getDefinitionRenamer(): DefinitionRenamer = PsiLocatedRenamer(caseArg) }
            val typeExpr = psiFactory.createExpression(ToAbstractVisitor.convert(expression, ppConfig).toString())
            val caseArgExpr = caseArg.type
            if (caseArgExpr != null) {
                caseArgExpr.replace(typeExpr)
            } else {
                caseArg.add(psiFactory.createWhitespace(" "))
                caseArg.add(psiFactory.createColon())
                caseArg.add(typeExpr)
            }
        }

        fun doInitOccupiedLocalNames(caseExpr: Concrete.CaseExpression, enclosingDefinition: Concrete.Definition, sink: MutableSet<String>) {
            for (clause in caseExpr.clauses) {
                val expression = clause.expression
                if (expression != null && expression.data != null) {
                    val collector = LocalVariablesCollector(expression.data)
                    enclosingDefinition.accept(collector, null)
                    sink.addAll(collector.names)
                }
            }
        }

        fun doInsertElimVars(psiFactory: ArendPsiFactory, definitionParameters: List<DependentLink>, definitionParametersToEliminate: HashSet<Variable>, elimPsi: ArendElim, paramsMap: HashMap<DependentLink, ArendRefIdentifier>) {
            var anchor: PsiElement? = null
            for (param in definitionParameters) if (definitionParametersToEliminate.contains(param)) {
                val template = psiFactory.createRefIdentifier("${param.name}, dummy")
                val comma = template.nextLeaf() as PsiElement
                var commaInserted = false
                if (anchor != null) {
                    anchor = elimPsi.addAfter(comma, anchor)
                    commaInserted = true
                }
                anchor = elimPsi.addAfter(template, anchor ?: elimPsi.elimKw)
                elimPsi.addBefore(psiFactory.createWhitespace(" "), anchor)
                if (!commaInserted) {
                    elimPsi.addAfter(comma, anchor)
                }
            } else {
                anchor = paramsMap[param]
            }
        }
    }
}