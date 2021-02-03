package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
import org.arend.core.context.param.DependentLink.Helper.toList
import org.arend.core.context.param.UntypedDependentLink
import org.arend.core.definition.DataDefinition
import org.arend.core.expr.DataCallExpression
import org.arend.core.expr.ReferenceExpression
import org.arend.core.pattern.BindingPattern
import org.arend.core.subst.ExprSubstitution
import org.arend.core.subst.LevelSubstitution
import org.arend.core.subst.SubstVisitor
import org.arend.ext.variable.Variable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.quickfix.ExpectedConstructorQuickFix.Companion.EXPECTED_CONSTRUCTOR_QUICKFIX_TEXT
import org.arend.quickfix.ExpectedConstructorQuickFix.Companion.doInitOccupiedLocalNames
import org.arend.quickfix.ExpectedConstructorQuickFix.Companion.doInsertCaseArgs
import org.arend.quickfix.ExpectedConstructorQuickFix.Companion.doInsertPatternPrimers
import org.arend.quickfix.ExpectedConstructorQuickFix.Companion.doWriteTypeQualification
import org.arend.quickfix.removers.RemoveClauseQuickFix.Companion.doRemoveClause
import org.arend.resolving.DataLocatedReferable
import org.arend.typechecking.error.local.ImpossibleEliminationError

class ImpossibleEliminationQuickFix(val error: ImpossibleEliminationError, val cause: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun getText(): String = EXPECTED_CONSTRUCTOR_QUICKFIX_TEXT

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
        error.myCaseExpressions != null || error.myElimParams != null // this prevents quickfix from showing in the "no matching constructor" case

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        val dataDefinition = error.dataCall.definition
        val definition = error.definition as DataLocatedReferable
        val definitionPsi = definition.data?.element
        val constructorPsi = cause.element?.ancestor<ArendConstructor>()
        val typecheckedParameters = when {
            constructorPsi != null -> {
                (definition.typechecked as DataDefinition).constructors.firstOrNull { (it.referable as DataLocatedReferable).data?.element == constructorPsi }?.parameters
                    ?: throw java.lang.IllegalStateException()
            }
            else -> definition.typechecked.parameters
        }
        val bodyPsi = (definitionPsi as? ArendFunctionalDefinition)?.body
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
        val elimParams = error.myElimParams

        if (error.myCaseExpressions != null) { // case
            val stuckParameter = error.myParameters
            val stuckParameterType = stuckParameter.type

            val caseExprPsi = cause.element?.ancestor<ArendCaseExpr>()
            val clausesListPsi = caseExprPsi?.withBody?.clauseList
            if (caseExprPsi != null && stuckParameterType is DataCallExpression && clausesListPsi != null) {
                val exprsToEliminate = stuckParameterType.defCallArguments.zip(toList(dataDefinition.parameters)).filter { ddEliminatedParameters.contains(it.second) }.toList()
                val sampleDataCall = DataCallExpression(dataDefinition, error.dataCall.sortArgument, toList(dataDefinition.parameters).map { it.makeReference() })
                val toActualParametersSubstitution = ExprSubstitution(); for (entry in stuckParameterType.defCallArguments.zip(toList(dataDefinition.parameters))) toActualParametersSubstitution.add(entry.second, entry.first)
                val oldCaseArgs = caseExprPsi.caseArgList
                val parameterToCaseArgMap = HashMap<DependentLink, ArendCaseArg>()
                val parameterToCaseExprMap = ExprSubstitution()
                val caseOccupiedLocalNames = HashSet<String>(); doInitOccupiedLocalNames(caseExprPsi, caseOccupiedLocalNames)
                val bindingToCaseArgMap = HashMap<Binding, ArendCaseArg>()

                if (error.myCaseExpressions != null) for (triple in toList(error.clauseParameters).zip(error.myCaseExpressions.zip(caseExprPsi.caseArgList))) {
                    parameterToCaseArgMap[triple.first] = triple.second.second
                    parameterToCaseExprMap.add(triple.first, triple.second.first)
                }

                val dependentCaseArg =
                    parameterToCaseArgMap[stuckParameter]!! //Safe as stuckParameter is one of error.clauseParameters
                for (expression in exprsToEliminate) {
                    val exprSubst =
                        expression.first.accept(SubstVisitor(parameterToCaseExprMap, LevelSubstitution.EMPTY), null)
                    doInsertCaseArgs(psiFactory, caseExprPsi, oldCaseArgs, expression.second, exprSubst, dependentCaseArg, error.myCaseExpressions, caseOccupiedLocalNames, bindingToCaseArgMap,null)
                }

                val toInsertedBindingsSubstitution = ExprSubstitution(); for (e in bindingToCaseArgMap) toInsertedBindingsSubstitution.add(e.key, ReferenceExpression(UntypedDependentLink(e.value.caseArgExprAs.defIdentifier!!.name)))

                val typeQualification = sampleDataCall.accept(SubstVisitor(toInsertedBindingsSubstitution, LevelSubstitution.EMPTY), null).accept(SubstVisitor(toActualParametersSubstitution, LevelSubstitution.EMPTY), null)
                doWriteTypeQualification(psiFactory, typeQualification, dependentCaseArg)

                doInsertPatternPrimers(psiFactory, clausesListPsi, caseExprPsi, oldCaseArgs, bindingToCaseArgMap, null)
            }
        } else
            if (elimParams != null && elimPsi != null) { // elim
                val clausesListPsi = when {
                    bodyPsi is ArendFunctionBody -> bodyPsi.functionClauses?.clauseList
                    bodyPsi is ArendInstanceBody -> bodyPsi.functionClauses?.clauseList
                    constructorPsi != null -> constructorPsi.clauseList
                    dataBodyPsi != null -> dataBodyPsi.constructorClauseList
                    else -> null
                }
                val clauseToDefinitionMap = HashMap<Variable, Variable>()

                if (error.substitution != null) for (variable in error.substitution.keys) {
                    val binding = (error.substitution.get(variable) as? ReferenceExpression)?.binding
                    if (binding != null && variable is Binding) clauseToDefinitionMap[binding] = variable
                }

                val definitionParametersToEliminate = HashSet<Variable>()
                val exprsToEliminate = toList(dataDefinition.parameters).zip(error.dataCall.defCallArguments)
                    .filter { ddEliminatedParameters.contains(it.first) }.map { it.second }.toList()
                for (expr in exprsToEliminate) if (expr is ReferenceExpression) (clauseToDefinitionMap[expr.binding]
                    ?: expr.binding).let { definitionParametersToEliminate.add(it) }

                definitionParametersToEliminate.removeAll(elimParams)

                val paramsMap = HashMap<DependentLink, ArendRefIdentifier>()
                for (e in elimParams.zip(elimPsi.refIdentifierList)) paramsMap[e.first] = e.second

                ExpectedConstructorQuickFix.doInsertElimVars(
                    psiFactory,
                    definitionParameters,
                    definitionParametersToEliminate,
                    elimPsi,
                    paramsMap
                )

                if (clausesListPsi != null) for (transformedClause in clausesListPsi)
                    ExpectedConstructorQuickFix.doInsertPrimers(
                        psiFactory,
                        transformedClause,
                        definitionParameters,
                        elimParams,
                        definitionParametersToEliminate,
                        null
                    ) { p -> p.name }
            }

        val clause = (error.cause.data as? PsiElement)?.ancestors?.firstOrNull{ it is ArendClause || it is ArendConstructorClause }
        if (clause != null) doRemoveClause(clause)
    }
}