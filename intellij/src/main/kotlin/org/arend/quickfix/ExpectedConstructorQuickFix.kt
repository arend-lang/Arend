package org.arend.quickfix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.context.binding.Binding
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.Constructor
import org.arend.core.expr.DataCallExpression
import org.arend.core.expr.ReferenceExpression
import org.arend.core.pattern.*
import org.arend.core.subst.ExprSubstitution
import org.arend.error.CountingErrorReporter
import org.arend.error.DummyErrorReporter
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.DataLocatedReferable
import org.arend.term.abs.ConcreteBuilder.convertClause
import org.arend.term.abs.ConcreteBuilder.convertPattern
import org.arend.term.concrete.Concrete
import org.arend.typechecking.error.local.ExpectedConstructorError
import org.arend.typechecking.patternmatching.ExpressionMatcher
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashSet

class ExpectedConstructorQuickFix(val error: ExpectedConstructorError, val cause: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = "arend.patternmatching"

    override fun getText(): String = "Do patternmatching on the 'stuck' variable"

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val parameterType = error.parameter?.type
        val definition = error.definition as? DataLocatedReferable
        val constructor = error.referable as? DataLocatedReferable
        val constructorTypechecked = constructor?.typechecked as? Constructor

        if (error.parameter == null || error.substitution == null || definition == null ||
                constructorTypechecked == null || parameterType !is DataCallExpression) return // We could show a notification?

        val definitionPsi = definition.data?.element
        val bodyPsi = (definitionPsi as? ArendFunctionalDefinition)?.body
        val functionClausesPsi = when (bodyPsi) {
            is ArendFunctionBody -> bodyPsi.functionClauses
            is ArendInstanceBody -> bodyPsi.functionClauses
            else -> null
        }

        val clausesListPsi = functionClausesPsi?.clauseList
        val currentClause = cause.element?.ancestor<ArendClause>() ?: return
        val concreteCurrentClause = convertClause(currentClause, ArendReferableConverter, null, null)
        run {
            val cer = CountingErrorReporter(DummyErrorReporter.INSTANCE)
            (ExpressionResolveNameVisitor(ArendReferableConverter, currentClause.scope, ArrayList<Referable>(), cer, null)).visitClause(concreteCurrentClause, null)
            if (cer.errorsNumber > 0) return@invoke
        }

        // STEP 1: Compute matching patterns
        val rawSubsts = HashMap<Binding, ExpressionPattern>()
        val canMatch: Boolean = ExpressionMatcher.computeMatchingPatterns(parameterType, constructorTypechecked, ExprSubstitution(), rawSubsts) != null

        if (!canMatch) {
            println("Pattern match failed")
            return
        } // We could show a notification?

        val definitionParameters = DependentLink.Helper.toList(definition.typechecked.parameters)
        val clauseParameters = DependentLink.Helper.toList(error.patternParameters)

        val definitionToClauseMap = HashMap<Variable, Variable>()
        val clauseToDefinitionMap = HashMap<Variable, Variable>()
        val definitionParametersToEliminate = HashSet<Variable>() // Subset of definitionParameters
        val clauseParametersToSpecialize = HashSet<Variable>() //Subset of clauseParameters

        //STEP 2: Calculate lists of variables which need to be eliminated or specialized
        for (variable in error.substitution.keys) {
            val binding = (error.substitution.get(variable) as? ReferenceExpression)?.binding
            if (binding != null && variable is Binding && definitionParameters.contains(variable) && clauseParameters.contains(binding)) {
                definitionToClauseMap[variable] = binding
                clauseToDefinitionMap[binding] = variable
            }
        }

        val correctedSubsts = HashMap<Variable, ExpressionPattern>() //This piece of code filters out trivial substitutions and also ensures that the key of each substitution is either an element of definitionParametersToEliminate or clauseParametersToSpecialize
        for (subst in rawSubsts) if (subst.value !is BindingPattern) {
            if (definitionParameters.contains(subst.key)) {
                definitionParametersToEliminate.add(subst.key)
                correctedSubsts[subst.key] = subst.value
            } else {
                val localClauseBinding = if (clauseParameters.contains(subst.key)) subst.key else (error.substitution[subst.key] as? ReferenceExpression)?.binding
                if (localClauseBinding != null) {
                    val definitionBinding = clauseToDefinitionMap[localClauseBinding]
                    if (definitionBinding != null && definitionParameters.contains(definitionBinding)) {
                        correctedSubsts[definitionBinding] = subst.value
                        definitionParametersToEliminate.add(definitionBinding)
                    } else if (clauseParameters.contains(localClauseBinding)) {
                        correctedSubsts[localClauseBinding] = subst.value
                        clauseParametersToSpecialize.add(localClauseBinding)
                    }
                }
            }
        }

        //STEP 3: Match clauseParameters with currentClause PSI
        val matchData = HashMap<DependentLink, VariableLocationDescriptor>()
        if (!matchConcreteWithWellTyped(currentClause, concreteCurrentClause.patterns, prepareExplicitnessMask(definitionParameters, error.elimParams), clauseParameters.iterator(), matchData)) return
        if (clauseParametersToSpecialize.any { matchData[it] == null }) return

        fun dump() {
            println("DefinitionPsi:\n${definitionPsi?.text}")
            println("Constructor: ${constructorTypechecked.name}; ")
            println("Clause: ${currentClause.text}")

            print("\nParameters of the typechecked definition header: {")
            for (p in definitionParameters) print("${p.toString1()}; Explicit:${p.isExplicit}; ")
            println("}")

            print("Free variables of the local clause patterns': {")
            for (p in clauseParameters) print("${p.toString1()} Explicit:${p.isExplicit}; ")
            println("}")

            println("\nerror.substitution contents:")
            for (variable in error.substitution.keys) {
                val binding = (error.substitution.get(variable) as? ReferenceExpression)?.binding
                println("${variable.toString1()} -> ${binding?.toString1() ?: error.substitution[variable]}")
            }

            println()

            println("Calculated substitutions (corrected nontrivial): {")
            for (s in correctedSubsts.entries) {
                print("  ${s.key.toString1()}->${s.value.toExpression()}")
                if (definitionParameters.contains(s.key)) print("[definition]") else if (clauseParameters.contains(s.key)) print("[clause]") else print("[neither]")
                println(";")
            }

            println("}\nDefinition parameters => clause parameters {")
            for (r in definitionToClauseMap) print("  ${r.key.toString1()} -> ${r.value.toString1()};\n")
            println("}")

            if (definitionParametersToEliminate.isNotEmpty()) {
                print("Definition parameters which need to be eliminated: {")
                for (v in definitionParametersToEliminate) print("${v.toString1()}; ")
                println("}")
            } else {
                println("No definition parameters to eliminate")
            }

            if (clauseParametersToSpecialize.isNotEmpty()) {
                print("Local clause variables which need to be specialized: {")
                for (v in clauseParametersToSpecialize) print("${v.toString1()}; ")
                println("}\n")
            } else {
                println("No local clause variables to specialize\n")
            }

            println("Result of matching local clause parameters with abstract PSI:")
            for (mdEntry in matchData) when (val mdValue = mdEntry.value) {
                is ExplicitNamePattern ->
                    println("${mdEntry.key.toString1()} -> existing parameter ${mdValue.bindingPsi.javaClass.simpleName}/${mdValue.bindingPsi.text}")
                is ImplicitConstructorPattern ->
                    println("${mdEntry.key.toString1()} -> implicit parameter of ${mdValue.enclosingNode.javaClass.simpleName}/\"${mdValue.enclosingNode.text}\" before \"${mdValue.followingParameter?.text}\" after ${mdValue.implicitArgCount} implicit args")
            }


            println()
        }

        //dump()

        // At this point we have collected enough information to actually attempt modifying PSI
        val psiFactory = ArendPsiFactory(project)

        if (error.caseExpressions == null) {
            val patternPrimers = HashMap<Variable, ArendPattern>()

            // STEP 4: Eliminate global variables and insert primers corresponding to definitionVariablesToEliminate
            // We may need to modify all clauses not just the one on which refactoring was invoked.
            if (error.elimParams.isNotEmpty()) { //elim
                val elimPsi = bodyPsi?.elim!! //safe since elimParams is nonempty
                val paramsMap = HashMap<DependentLink, ArendRefIdentifier>()
                for (e in error.elimParams.zip(elimPsi.refIdentifierList)) paramsMap[e.first] = e.second
                for (e in error.elimParams.zip(currentClause.patternList)) patternPrimers[e.first] = e.second

                definitionParametersToEliminate.removeAll(error.elimParams)

                var anchor: PsiElement? = null
                for (param in definitionParameters) if (definitionParametersToEliminate.contains(param)) {
                    val template = psiFactory.createRefIdentifier("${param.name}, dummy")
                    val comma = template.nextSibling
                    var commaInserted = false
                    if (anchor != null) {
                        anchor = elimPsi.addAfter(comma, anchor)
                        commaInserted = true
                    }
                    anchor = elimPsi.addAfterWithNotification(template, anchor ?: elimPsi.elimKw)
                    elimPsi.addBefore(psiFactory.createWhitespace(" "), anchor)
                    if (!commaInserted) {
                        anchor = elimPsi.addAfter(comma, anchor)
                    }
                } else {
                    anchor = paramsMap[param]
                }


                if (clausesListPsi != null) for (transformedClause in clausesListPsi)
                    doInsertPrimers(psiFactory, transformedClause, definitionParameters, error.elimParams, definitionParametersToEliminate,
                            if (transformedClause == currentClause) patternPrimers else null) { p -> p.name }

            } else if (clausesListPsi != null) for (transformedClause in clausesListPsi) {
                val parameterIterator = definitionParameters.iterator()
                val patternIterator = transformedClause.patternList.iterator()
                val patternMatchedParameters = LinkedHashSet<Variable>()
                while (parameterIterator.hasNext() && patternIterator.hasNext()) {
                    val pattern = patternIterator.next()
                    var parameter: DependentLink? = parameterIterator.next()
                    if (pattern.isExplicit) while (parameter != null && !parameter.isExplicit)
                        parameter = if (parameterIterator.hasNext()) parameterIterator.next() else null
                    if (parameter != null && pattern.isExplicit == parameter.isExplicit) {
                        patternMatchedParameters.add(parameter)
                        if (transformedClause == currentClause) patternPrimers[parameter] = pattern
                    }
                }

                val newVars = definitionParametersToEliminate.minus(patternMatchedParameters)
                val newVarsPlusPrecedingImplicitVars = HashSet<Variable>(newVars)
                var implicitFlag = false
                for (param in definitionParameters.reversed()) {
                    if (implicitFlag && !param.isExplicit) newVarsPlusPrecedingImplicitVars.add(param)
                    if (newVars.contains(param) && !param.isExplicit) implicitFlag = true
                    if (param.isExplicit) implicitFlag = false
                }

                doInsertPrimers(psiFactory, transformedClause, definitionParameters, ArrayList(patternMatchedParameters), newVarsPlusPrecedingImplicitVars,
                        if (transformedClause == currentClause) patternPrimers else null) { p ->
                    val builder = StringBuilder()
                    if (!p.isExplicit) builder.append("{")
                    builder.append(if (newVars.contains(p)) p.name else "_")
                    if (!p.isExplicit) builder.append("}")
                    builder.toString()
                }
            }

            // STEP 5: Analyze matchData and insert primers corresponding to localClauseVariablesToSpecialize
            val insertData = HashMap<Pair<PsiElement, PsiElement?>, MutableList<Pair<Int, Variable>>>()
            for (p in clauseParametersToSpecialize) {
                when (val descriptor = matchData[p]) {
                    is ExplicitNamePattern -> patternPrimers[p] = descriptor.bindingPsi as ArendPattern
                    is ImplicitConstructorPattern -> {
                        val positionKey = Pair(descriptor.enclosingNode, descriptor.followingParameter)
                        var positionList = insertData[positionKey]
                        if (positionList == null) {
                            positionList = ArrayList()
                            insertData[positionKey] = positionList
                        }
                        positionList.add(Pair(descriptor.implicitArgCount, p))
                    }
                }
            }

            insertPrimers(psiFactory, insertData) {a, b -> patternPrimers[a] = b }

            if (clausesListPsi != null) {
                val processedSubstitutions = HashMap<PsiElement, ExpressionPattern>()
                val newPrimers = HashMap<ImplicitConstructorPattern, ExpressionPattern>()
                val mismatchedPatterns = HashMap<PsiElement, Pair<ExpressionPattern, List<PsiElement>>>()

                // STEP 6: Unify matched patterns with existing patterns and calculate elementary substitutions (of the form "variable" -> "expression")
                for (cS in correctedSubsts) {
                    val patternPrimer = patternPrimers[cS.key]
                    if (patternPrimer != null) {
                        var concretePrimer = convertPattern(patternPrimer, ArendReferableConverter, null, null)
                        val primerOk: Boolean
                        run {
                            val cer = CountingErrorReporter(DummyErrorReporter.INSTANCE)
                            val resolver = ExpressionResolveNameVisitor(ArendReferableConverter, patternPrimer.scope, ArrayList<Referable>(), cer, null)
                            val primerList = ArrayList<Concrete.Pattern>()
                            primerList.add(concretePrimer)
                            resolver.visitPatterns(primerList, null, true)
                            concretePrimer = primerList[0]
                            resolver.resolvePattern(concretePrimer)
                            primerOk = cer.errorsNumber == 0
                        }

                        if (primerOk) unify(cS.value, concretePrimer, processedSubstitutions, newPrimers, mismatchedPatterns)
                    }
                }

                // STEP 7: Insert new primers
                val newPrimerInsertData = HashMap<Pair<PsiElement, PsiElement?>, MutableList<Pair<Int, Variable>>>()
                val tempVarMatchInfo = HashMap<Variable, ExpressionPattern>()
                for (p in newPrimers) {
                    val descriptor = p.key
                    val positionKey = Pair(descriptor.enclosingNode, descriptor.followingParameter)
                    var positionList = newPrimerInsertData[positionKey]
                    if (positionList == null) {
                        positionList = ArrayList()
                        newPrimerInsertData[positionKey] = positionList
                    }
                    val temp = VariableImpl("temp${descriptor.hashCode().rem(1000)}") // This variable name does not appear in the resulting code and is needed only for debugging
                    positionList.add(Pair(descriptor.implicitArgCount, temp))
                    tempVarMatchInfo[temp] = p.value
                }

                insertPrimers(psiFactory, newPrimerInsertData) {tempVar, createdPrimer -> tempVarMatchInfo[tempVar]?.let{ processedSubstitutions[createdPrimer] = it}}

                // STEP 8: Perform actual substitutions (at this point all keys of substEntry should be simple NamePatterns...)
                for (removeEntry in mismatchedPatterns) {
                    println("mismatchedEntry: ${removeEntry.key.text} |-> ${removeEntry.value.first.toExpression()} variables:${removeEntry.value.second.map { it.text }.toList()}")
                }
                for (substEntry in processedSubstitutions) {
                    println("splitEntry: ${substEntry.key.text} |-> ${substEntry.value.toExpression()}")
                }
            }
        } else { // case
            println("caseExpressions: ${error.caseExpressions}")
        }
    }

    companion object {
        fun Variable.toString1(): String = "${this}@${this.hashCode().rem(100000)}"

        fun stripOfImplicitBraces(patternPsi: PsiElement): PsiElement {
            if (patternPsi is ArendPattern && patternPsi.let{ it.defIdentifier == null && it.atomPatternOrPrefixList.size == 0 && it.atomPattern != null }) {
                val atom = patternPsi.atomPattern!!
                if (atom.lbrace != null && atom.rbrace != null && atom.patternList.size == 1)
                    return atom.patternList[0]
            }
            return patternPsi
        }

        fun collectBindings(pattern: Concrete.Pattern, collectedVariables: MutableList<PsiElement>) {
            val data = pattern.data as PsiElement
            when (pattern) {
                is Concrete.NamePattern ->  collectedVariables.add(if (pattern.isExplicit) data else stripOfImplicitBraces(data))
                is Concrete.ConstructorPattern -> for (p in pattern.patterns) collectBindings(p, collectedVariables)
                is Concrete.TuplePattern -> for (p in pattern.patterns) collectBindings(p, collectedVariables)
                else -> {}
            }
        }

        fun unify(pattern: ExpressionPattern, concretePattern: Concrete.Pattern,
                  processedSubstitutions: MutableMap<PsiElement, ExpressionPattern>,
                  newPrimers: MutableMap<ImplicitConstructorPattern, ExpressionPattern>,
                  mismatchedPatterns: MutableMap<PsiElement, Pair<ExpressionPattern, List<PsiElement>>>) {
            val data = concretePattern.data as PsiElement

            fun yield() {
                val key = if (concretePattern.isExplicit) data else stripOfImplicitBraces(data)
                if (concretePattern is Concrete.NamePattern) processedSubstitutions[key] = pattern else {
                    val bindingsToRemove = ArrayList<PsiElement>()
                    collectBindings(concretePattern, bindingsToRemove)
                    mismatchedPatterns[key] = Pair(pattern, bindingsToRemove)
                }
            }

            when (concretePattern) {
                is Concrete.NamePattern -> if (pattern !is BindingPattern) yield()
                is Concrete.ConstructorPattern -> { //TODO: Code too similar to "matchConcreteWithWellTyped". Could we somehow isolate common pieces of code for these functions?
                    val existingConstructor = concretePattern.constructor
                    val substitutedConstructor = (pattern.constructor)?.referable
                    if (pattern is ConstructorExpressionPattern && substitutedConstructor == existingConstructor) {
                        val bindingsIterator = DependentLink.Helper.toList(substitutedConstructor.typechecked.parameters).iterator()
                        val constructorExpressionParameters = pattern.subPatterns.iterator()
                        val concreteSubPatternsIterator = concretePattern.patterns.iterator()

                        var concreteSubPattern: Concrete.Pattern? = null
                        var skippedParameters = 0
                        fun nextConcretePattern() {
                            concreteSubPattern = if (concreteSubPatternsIterator.hasNext()) concreteSubPatternsIterator.next() else null
                            skippedParameters = 0
                        }
                        nextConcretePattern()

                        while (bindingsIterator.hasNext() && constructorExpressionParameters.hasNext()) {
                            val currentBinding = bindingsIterator.next()
                            val currentSubpattern = constructorExpressionParameters.next()
                            val concreteSubPatternVal = concreteSubPattern

                            if (concreteSubPatternVal != null && concreteSubPatternVal.isExplicit == currentBinding.isExplicit) {
                                if (currentSubpattern !is BindingPattern) unify(currentSubpattern, concreteSubPatternVal, processedSubstitutions, newPrimers, mismatchedPatterns)
                                nextConcretePattern()
                            } else if (concreteSubPatternVal == null || concreteSubPatternVal.isExplicit && !currentBinding.isExplicit) {
                                val enclosingNode = (concretePattern.data as PsiElement).let{ if (concretePattern.isExplicit) it else stripOfImplicitBraces(it) }

                                if (currentSubpattern !is BindingPattern) newPrimers[ImplicitConstructorPattern(enclosingNode, concreteSubPatternVal?.data as? PsiElement, skippedParameters)] = currentSubpattern
                                skippedParameters++
                            }
                        }
                    } else yield()

                }
                is Concrete.NumberPattern -> yield()
                is Concrete.TuplePattern -> if (pattern is ConstructorExpressionPattern && pattern.constructor == null) for (pair in concretePattern.patterns.zip(pattern.subPatterns)) {
                    unify(pair.second, pair.first, processedSubstitutions, newPrimers, mismatchedPatterns)
                } else yield()
            }
        }

        fun prepareExplicitnessMask(sampleParameters: List<DependentLink>, elimParams: List<DependentLink>?): List<Boolean> =
                if (elimParams != null && elimParams.isNotEmpty()) sampleParameters.map { elimParams.contains(it) } else sampleParameters.map { it.isExplicit }

        fun insertPrimers(psiFactory: ArendPsiFactory, insertData: HashMap<Pair<PsiElement, PsiElement?>, MutableList<Pair<Int, Variable>>>, callback: (Variable, ArendPattern) -> Unit) {
            for (insertDataEntry in insertData) {
                val toInsertList = insertDataEntry.value
                val enclosingNode = insertDataEntry.key.first
                val position = insertDataEntry.key.second

                var skippedParams = 0
                toInsertList.sortBy { it.first }

                var nullAnchor: PsiElement? = null
                for (entry in toInsertList) {
                    var patternLine = ""
                    for (i in 0 until entry.first - skippedParams) patternLine += " {_}"
                    patternLine +=  /*" {_}" */ " {${entry.second.name}}"
                    val templateList = (psiFactory.createAtomPattern(patternLine).parent as ArendPattern).atomPatternOrPrefixList
                    if (position == null) {
                        if (enclosingNode is ArendPattern) {
                            if (nullAnchor == null) nullAnchor = enclosingNode.defIdentifier
                            enclosingNode.addRangeAfterWithNotification(templateList.first(), templateList.last(), nullAnchor!!)
                            nullAnchor = enclosingNode.atomPatternOrPrefixList.last()
                            nullAnchor.childOfType<ArendPattern>()?.let { callback.invoke(entry.second, it)  }
                        }
                    } else {
                        enclosingNode.addRangeBefore(templateList.first(), templateList.last(), position)
                        position.prevSibling.childOfType<ArendPattern>()?.let { callback.invoke(entry.second, it) }
                    }
                    skippedParams = entry.first + 1
                }
            }
        }

        fun doInsertPrimers(psiFactory: ArendPsiFactory,
                            clause: ArendClause,
                            typecheckedParams: List<DependentLink>,
                            eliminatedParams: List<Variable>,
                            eliminatedVars: HashSet<Variable>,
                            patternPrimers: HashMap<Variable, ArendPattern>?,
                            nameCalculator: (DependentLink) -> String) {
            val patternsMap = HashMap<Variable, ArendPattern>()
            for (e in eliminatedParams.zip(clause.patternList)) patternsMap[e.first] = e.second

            var anchor: PsiElement? = null

            for (param in typecheckedParams) {
                if (eliminatedVars.contains(param)) {
                    val template = psiFactory.createClause("${nameCalculator.invoke(param)}, dummy").childOfType<ArendPattern>()!!
                    val comma = template.nextSibling
                    var commaInserted = false
                    if (anchor != null) {
                        anchor = clause.addAfter(comma, anchor)
                        commaInserted = true
                        anchor = clause.addAfterWithNotification(template, anchor ?: clause.firstChild)
                        clause.addBefore(psiFactory.createWhitespace(" "), anchor)
                    } else {
                        anchor = clause.addBeforeWithNotification(template, clause.firstChild)
                    }
                    if (patternPrimers != null) patternPrimers[param] = anchor as ArendPattern
                    if (!commaInserted) clause.addAfter(comma, anchor)
                } else {
                    anchor = patternsMap[param]
                }
            }
        }

        interface VariableLocationDescriptor
        data class ExplicitNamePattern(val bindingPsi: PsiElement) : VariableLocationDescriptor
        data class ImplicitConstructorPattern(val enclosingNode: PsiElement, val followingParameter: PsiElement?, val implicitArgCount: Int) : VariableLocationDescriptor

        fun matchConcreteWithWellTyped(enclosingNode: PsiElement, patterns: List<Concrete.Pattern>, explicitnessMask: List<Boolean>,
                                       patternParameterIterator: MutableIterator<DependentLink>,
                                       result: MutableMap<DependentLink, VariableLocationDescriptor>): Boolean {
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
                            matchConcreteWithWellTyped(data, concretePattern.patterns, List(concretePattern.patterns.size) { true }, patternParameterIterator, result)
                        }
                        is Concrete.NamePattern -> {
                            val patternParameter = patternParameterIterator.next()
                            result[patternParameter] = ExplicitNamePattern(data)
                        }
                        is Concrete.ConstructorPattern -> {
                            val typechecked = (concretePattern.constructor as? DataLocatedReferable)?.typechecked as? Constructor
                                    ?: return false
                            if (!matchConcreteWithWellTyped(data, concretePattern.patterns, prepareExplicitnessMask(DependentLink.Helper.toList(typechecked.parameters), null), patternParameterIterator, result)) return false
                        }
                    }
                    continue
                }
                if (!spExplicit) {
                    val patternParameter = patternParameterIterator.next()
                    result[patternParameter] = ImplicitConstructorPattern(enclosingNode, data, skippedParameters)
                    skippedParameters += 1
                }
            }
            return true
        }
    }
}

