package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Definition
import org.arend.core.expr.DefCallExpression
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorExpressionPattern
import org.arend.core.pattern.EmptyPattern
import org.arend.core.pattern.ExpressionPattern
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.renamer.StringRenamer
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.*
import org.arend.settings.ArendSettings
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.local.MissingClausesError
import kotlin.math.abs

class ImplementMissingClausesQuickFix(private val missingClausesError: MissingClausesError,
                                      private val causeRef: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    private val clauses = ArrayList<ArendClause>()

    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = causeRef.element != null

    override fun getText(): String = "Implement missing clauses"

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val psiFactory = ArendPsiFactory(project)
        val element = causeRef.element ?: return
        clauses.clear()
        val definedVariables: List<Variable> = collectDefinedVariables(element)

        missingClausesError.setMaxListSize(service<ArendSettings>().clauseActualLimit)
        for (clause in missingClausesError.limitedMissingClauses.reversed()) if (clause != null) {
            val filters = HashMap<ConstructorExpressionPattern, List<Boolean>>()
            val previewResults = ArrayList<PatternKind>()
            val recursiveTypeUsagesInBindings = ArrayList<Int>()
            val elimMode = missingClausesError.isElim

            run {
                var parameter: DependentLink? = if (!elimMode) missingClausesError.parameters else null
                val iterator = clause.iterator()
                var i = 0
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val recTypeUsagesInPattern = HashSet<BindingPattern>()
                    val sampleParameter = if (elimMode) missingClausesError.eliminatedParameters[i] else parameter
                    previewResults.add(previewPattern(pattern, filters, if (parameter == null || parameter.isExplicit) Companion.Braces.NONE else Companion.Braces.BRACES, recTypeUsagesInPattern, (sampleParameter?.type as? DefCallExpression)?.definition))
                    recursiveTypeUsagesInBindings.add(recTypeUsagesInPattern.size)
                    parameter = if (parameter != null && parameter.hasNext()) parameter.next else null
                    i++
                }
            }

            val topLevelFilter = computeFilter(previewResults)

            val patternStrings = ArrayList<String>()
            var containsEmptyPattern = false
            run {
                val iterator = clause.iterator()
                val recursiveTypeUsagesInBindingsIterator = recursiveTypeUsagesInBindings.iterator()
                var parameter2: DependentLink? = if (!elimMode) missingClausesError.parameters else null
                val clauseBindings: MutableList<Variable> = definedVariables.toMutableList()
                val eliminatedBindings = HashSet<DependentLink>()
                var i = 0
                while (iterator.hasNext()) {
                    val pattern = iterator.next()
                    val nRecursiveBindings = recursiveTypeUsagesInBindingsIterator.next()
                    val braces = if (parameter2 == null || parameter2.isExplicit) Companion.Braces.NONE else Companion.Braces.BRACES
                    val sampleParameter = if (elimMode) missingClausesError.eliminatedParameters[i] else parameter2!!
                    val patternData = doTransformPattern(pattern, element, project, filters, braces, clauseBindings, sampleParameter, nRecursiveBindings, eliminatedBindings, missingClausesError)
                    patternStrings.add(patternData.first)
                    containsEmptyPattern = containsEmptyPattern || patternData.second
                    parameter2 = if (parameter2 != null && parameter2.hasNext()) parameter2.next else null
                    i++
                }
            }

            clauses.add(psiFactory.createClause(concat(patternStrings, topLevelFilter), containsEmptyPattern))
        }

        insertClauses(psiFactory, element, clauses)
    }

    companion object {
        enum class Braces { NONE, PARENTHESES, BRACES }
        enum class PatternKind { IMPLICIT_ARG, IMPLICIT_EXPR, EXPLICIT }

        private fun insertPairOfBraces(psiFactory: ArendPsiFactory, anchor: PsiElement) {
            val braces = psiFactory.createPairOfBraces()
            anchor.parent.addAfter(braces.second, anchor)
            anchor.parent.addAfter(braces.first, anchor)
            anchor.parent.addAfter(psiFactory.createWhitespace(" "), anchor)
        }

        fun insertClauses(psiFactory: ArendPsiFactory, cause: ArendCompositeElement, clauses: List<ArendClause>) {
            var primerClause: ArendClause? = null // a "primer" clause which is needed only to simplify insertion of proper clauses
            val anchor = when (cause) {
                is ArendDefFunction -> {
                    val fBody = cause.functionBody
                    if (fBody != null) {
                        val fClauses = fBody.functionClauses
                        val lastChild = fBody.lastChild
                        if (fClauses != null) {
                            fClauses.clauseList.lastOrNull() ?: fClauses.lbrace
                            ?: fClauses.lastChild /* the last branch is meaningless */
                        } else {
                            val newClauses = psiFactory.createFunctionClauses()
                            val insertedClauses = lastChild.parent?.addAfter(newClauses, lastChild) as ArendFunctionClauses
                            lastChild.parent?.addAfter(psiFactory.createWhitespace(" "), lastChild)
                            primerClause = insertedClauses.lastChild as? ArendClause
                            primerClause
                        }
                    } else {
                        val newBody = psiFactory.createFunctionClauses().parent as ArendFunctionBody
                        val lastChild = cause.lastChild
                        cause.addAfter(newBody, lastChild)
                        cause.addAfter(psiFactory.createWhitespace(" "), lastChild)
                        primerClause = cause.functionBody!!.functionClauses!!.lastChild as ArendClause
                        primerClause
                    }
                }
                is ArendCaseExpr -> {
                    cause.clauseList.lastOrNull() ?: (cause.lbrace ?: (cause.withKw ?: (cause.returnExpr
                            ?: cause.caseArgList.lastOrNull())?.let { withAnchor ->
                        cause.addAfterWithNotification((psiFactory.createExpression("\\case 0 \\with") as ArendCaseExpr).withKw!!,
                                cause.addAfter(psiFactory.createWhitespace(" "), withAnchor))
                    })?.let {
                        insertPairOfBraces(psiFactory, it)
                        cause.lbrace
                    })
                }
                is ArendCoClauseDef -> {
                    val coClauseBody = cause.coClauseBody ?: (cause.addAfterWithNotification(psiFactory.createCoClauseBody(), cause.lastChild) as ArendCoClauseBody)
                    val elim = coClauseBody.elim ?:
                        coClauseBody.addWithNotification(psiFactory.createCoClauseBody().childOfType<ArendElim>()!!)
                    if (coClauseBody.lbrace == null)
                        insertPairOfBraces(psiFactory, elim)

                    coClauseBody.clauseList.lastOrNull() ?: coClauseBody.lbrace ?: cause.lastChild
                }
                else -> null
            }
            val anchorParent = anchor?.parent
            for (clause in clauses) if (anchorParent != null) {
                val pipe = clause.findPrevSibling()
                var currAnchor: PsiElement? = null
                if (pipe != null) currAnchor = anchorParent.addAfter(pipe, anchor)
                anchorParent.addBefore(psiFactory.createWhitespace("\n"), currAnchor)
                currAnchor = anchorParent.addAfterWithNotification(clause, currAnchor)
                anchorParent.addBefore(psiFactory.createWhitespace(" "), currAnchor)
            }

            if (primerClause != null) {
                primerClause.findPrevSibling()?.delete()
                primerClause.delete()
            }

        }

        private fun computeFilter(input: List<PatternKind>): List<Boolean> {
            val result = ArrayList<Boolean>()
            var doNotSkipPatterns = false
            for (previewResult in input.reversed()) {
                when (previewResult) {
                    Companion.PatternKind.IMPLICIT_ARG -> {
                        result.add(0, doNotSkipPatterns)
                    }
                    Companion.PatternKind.IMPLICIT_EXPR -> {
                        result.add(0, true); doNotSkipPatterns = true
                    }
                    Companion.PatternKind.EXPLICIT -> {
                        result.add(0, true); doNotSkipPatterns = false
                    }
                }
            }
            return result
        }

        fun concat(input: List<String>, filter: List<Boolean>?, separator: String = ", "): String = buildString {
            val filteredInput = if (filter == null) input else input.filterIndexed { index, _ -> filter[index] }
            val iterator = filteredInput.iterator()
            while (iterator.hasNext()) {
                append(iterator.next())
                if (iterator.hasNext()) append(separator)
            }
        }

        private fun previewPattern(pattern: ExpressionPattern,
                                   filters: MutableMap<ConstructorExpressionPattern, List<Boolean>>,
                                   paren: Braces,
                                   recursiveTypeUsages: MutableSet<BindingPattern>,
                                   recursiveTypeDefinition: Definition?): PatternKind {
            when (pattern) {
                is ConstructorExpressionPattern -> {
                    val definition: Definition? = pattern.definition
                    val previewResults = ArrayList<PatternKind>()

                    val patternIterator = pattern.subPatterns.iterator()
                    var constructorArgument: DependentLink? = definition?.parameters

                    while (patternIterator.hasNext()) {
                        val argumentPattern = patternIterator.next()
                        previewResults.add(previewPattern(argumentPattern, filters,
                                if (constructorArgument == null || constructorArgument.isExplicit) Companion.Braces.PARENTHESES else Companion.Braces.BRACES, recursiveTypeUsages, recursiveTypeDefinition))
                        constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                    }

                    filters[pattern] = computeFilter(previewResults)
                }
                is BindingPattern -> {
                    val bindingType = pattern.binding.type
                    if (recursiveTypeDefinition != null && bindingType is DefCallExpression && bindingType.definition == recursiveTypeDefinition && pattern.binding.name == null) {
                        recursiveTypeUsages.add(pattern)
                    }
                    return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_ARG else Companion.PatternKind.EXPLICIT
                }
                is EmptyPattern -> {
                }
                else -> throw IllegalStateException()
            }

            return if (paren == Companion.Braces.BRACES) Companion.PatternKind.IMPLICIT_EXPR else Companion.PatternKind.EXPLICIT
        }

        private fun getIntegralNumber(pattern: ConstructorExpressionPattern): Int? {
            val isSuc = pattern.definition == Prelude.SUC
            val isPos = pattern.definition == Prelude.POS
            val isNeg = pattern.definition == Prelude.NEG
            if (isSuc || isPos || isNeg) {
                val argumentList = pattern.subPatterns
                if (argumentList.size != 1) return null
                val firstArgument = argumentList.first() as? ConstructorExpressionPattern
                        ?: return null
                val number = getIntegralNumber(firstArgument)
                if (isSuc && number != null) return number + 1
                if (isPos && number != null) return number
                if (isNeg && number != null && number != 0) return -number
                return null
            } else if (pattern.definition == Prelude.ZERO) return 0
            return null
        }

        fun doTransformPattern(pattern: ExpressionPattern, cause: ArendCompositeElement, project: Project,
                               filters: Map<ConstructorExpressionPattern, List<Boolean>>, paren: Braces,
                               occupiedNames: MutableList<Variable>,
                               sampleParameter: DependentLink,
                               nRecursiveBindings: Int,
                               eliminatedBindings: MutableSet<DependentLink>,
                               missingClausesError: MissingClausesError): Pair<String, Boolean> {
            var containsEmptyPattern = false

            val parameterName: String? = sampleParameter.name
            val recursiveTypeDefinition: Definition? = (sampleParameter.type as? DefCallExpression)?.definition

            fun getFreshName(binding: DependentLink): String {
                val renamer = StringRenamer()
                if (recursiveTypeDefinition != null) renamer.setParameterName(recursiveTypeDefinition, parameterName)
                return renamer.generateFreshName(binding, calculateOccupiedNames(occupiedNames, parameterName, nRecursiveBindings))
            }

            val result = when (pattern) {
                is ConstructorExpressionPattern -> {
                    eliminatedBindings.add(sampleParameter)
                    val definition: Definition? = pattern.definition
                    val referable = if (definition != null) PsiLocatedReferable.fromReferable(definition.referable) else null
                    val integralNumber = getIntegralNumber(pattern)
                    val patternMatchingOnIdp = admitsPatternMatchingOnIdp(sampleParameter.type.expr, if (cause is ArendCaseExpr) missingClausesError.parameters else null, eliminatedBindings)
                    if (patternMatchingOnIdp != PatternMatchingOnIdpResult.INAPPLICABLE) {
                        if (patternMatchingOnIdp == PatternMatchingOnIdpResult.IDP)
                            getCorrectPreludeItemStringReference(project, cause, Prelude.IDP)
                        else getFreshName(sampleParameter)
                    } else if (integralNumber != null && abs(integralNumber) < Concrete.NumberPattern.MAX_VALUE) {
                        integralNumber.toString()
                    } else {
                        val tupleMode = definition == null || definition is ClassDefinition && definition.isRecord
                        val argumentPatterns = ArrayList<String>()
                        run {
                            val patternIterator = pattern.subPatterns.iterator()
                            var constructorArgument: DependentLink? = definition?.parameters

                            while (patternIterator.hasNext()) {
                                val argumentPattern = patternIterator.next()
                                val argumentParen = when {
                                    tupleMode -> Companion.Braces.NONE
                                    constructorArgument == null || constructorArgument.isExplicit -> Companion.Braces.PARENTHESES
                                    else -> Companion.Braces.BRACES
                                }
                                val argPattern = doTransformPattern(argumentPattern, cause, project, filters, argumentParen, occupiedNames, sampleParameter, nRecursiveBindings, eliminatedBindings, missingClausesError)
                                argumentPatterns.add(argPattern.first)
                                containsEmptyPattern = containsEmptyPattern || argPattern.second
                                constructorArgument = if (constructorArgument != null && constructorArgument.hasNext()) constructorArgument.next else null
                            }
                        }

                        val filter = filters[pattern]
                        val arguments = concat(argumentPatterns, filter, if (tupleMode) "," else " ")
                        val result = buildString {
                            val defCall = if (referable != null) getTargetName(referable, cause)
                                    ?: referable.name else definition?.name
                            if (tupleMode) append("(") else {
                                append(defCall)
                                if (arguments.isNotEmpty()) append(" ")
                            }
                            append(arguments)
                            if (tupleMode) append(")")
                        }

                        if (paren == Companion.Braces.PARENTHESES && arguments.isNotEmpty()) "($result)" else result
                    }
                }

                is BindingPattern -> {
                    val result = getFreshName(pattern.binding)
                    occupiedNames.add(VariableImpl(result))
                    result
                }

                is EmptyPattern -> {
                    containsEmptyPattern = true
                    "()"
                }
                else -> throw IllegalStateException()
            }

            return Pair(if (paren == Companion.Braces.BRACES) "{$result}" else result, containsEmptyPattern)
        }


    }

}