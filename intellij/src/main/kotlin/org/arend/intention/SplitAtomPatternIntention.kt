package org.arend.intention

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.arend.codeInsight.completion.withAncestors
import org.arend.core.context.param.DependentLink
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.elimtree.ElimBody
import org.arend.core.elimtree.IntervalElim
import org.arend.core.expr.*
import org.arend.core.pattern.BindingPattern
import org.arend.core.pattern.ConstructorExpressionPattern
import org.arend.core.pattern.ExpressionPattern
import org.arend.core.pattern.Pattern.toExpressionPatterns
import org.arend.error.DummyErrorReporter
import org.arend.ext.core.ops.NormalizationMode
import org.arend.ext.module.LongName
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.variable.Variable
import org.arend.ext.variable.VariableImpl
import org.arend.naming.reference.LocalReferable
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.naming.renamer.ReferableRenamer
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.*
import org.arend.refactoring.utils.ServerBasedDefinitionRenamer
import org.arend.refactoring.utils.NumberSimplifyingConcreteVisitor
import org.arend.server.ArendServer
import org.arend.server.ArendServerService
import org.arend.server.RawAnchor
import org.arend.term.abs.Abstract
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.LocalVariablesCollector
import org.arend.term.concrete.SubstConcreteVisitor
import org.arend.term.prettyprint.DefinitionRenamerConcreteVisitor
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.util.ArendBundle
import java.util.*
import java.util.Collections.singletonList

class SplitAtomPatternIntention : SelfTargetingIntention<PsiElement>(PsiElement::class.java, ArendBundle.message("arend.pattern.split")) {
    private var splitPatternEntries: List<SplitPatternEntry>? = null
    private var caseClauseParameters: DependentLink? = null

    override fun isApplicableTo(element: PsiElement, caretOffset: Int, editor: Editor): Boolean {
        val server = element.project.service<ArendServerService>().server
        val recursiveParameterName = when (element) {
            is ArendPattern -> element.singleReferable?.refName
            else -> null
        }
        val project = editor.project
        this.splitPatternEntries = null

        if (!(element is ArendPattern && element.sequence.isEmpty())) return this.splitPatternEntries != null
        val type = getElementType(element, editor)?.let{ TypeConstructorExpression.unfoldType(it) }
        this.splitPatternEntries = when (type) {
            is DataCallExpression -> {
                val canDoPatternMatchingOnIdp = admitsPatternMatchingOnIdp(type, caseClauseParameters)
                if (project != null && canDoPatternMatchingOnIdp == PatternMatchingOnIdpResult.IDP) {
                    singletonList(IdpPatternEntry(project, element.isExplicit))
                } else if (canDoPatternMatchingOnIdp != PatternMatchingOnIdpResult.DO_NOT_ELIMINATE) {
                    val constructors = type.matchedConstructors ?: return false
                    constructors.map { ConstructorSplitPatternEntry( element, it.definition, element.isExplicit, type.definition, recursiveParameterName) }
                } else null
            }
            is SigmaExpression -> singletonList(TupleSplitPatternEntry(type, element.isExplicit))
            is ClassCallExpression -> {
                if (type.definition == Prelude.DEP_ARRAY) {
                    val isEmpty = ConstructorExpressionPattern.isArrayEmpty(type)
                    val implementedHere = type.definition.notImplementedFields.filter { type.isImplementedHere(it) }
                    val lenImplemented = implementedHere.contains(Prelude.ARRAY_LENGTH)

                    val result = ArrayList<SplitPatternEntry>()
                    for (p in arrayOf(Pair(true, Prelude.EMPTY_ARRAY), Pair(false, Prelude.ARRAY_CONS)))
                        if (isEmpty == null || isEmpty == p.first) {
                            val allParameters = DependentLink.Helper.toList(p.second.parameters)
                            val parametersToHideInPattern = if (p.first) allParameters else {
                                if (lenImplemented) allParameters.subList(0, 2) else singletonList(allParameters[1])
                            }

                            result.add(
                                ConstructorSplitPatternEntry(
                                    element,
                                    p.second,
                                    element.isExplicit,
                                    type.definition,
                                    recursiveParameterName,
                                    parametersToHideInPattern
                                )
                            )
                        }
                    result
                } else {
                    singletonList(ClassSplitPatternEntry(type, element.isExplicit))
                }
            }
            else -> null
        }
        return this.splitPatternEntries != null
    }

    override fun applyTo(element: PsiElement, project: Project, editor: Editor) {
        splitPatternEntries?.let { doSplitPattern(element, project, it) }
    }

    private fun getElementType(element: PsiElement, editor: Editor): Expression? {
        val project = editor.project
        caseClauseParameters = null
        if (project == null) return null
        val server = project.service<ArendServerService>().server

        var definition: PsiLocatedReferable? = null
        val (patternOwner, indexList) = locatePattern(element) ?: return null
        val ownerParent = (patternOwner as PsiElement).parent
        var coClauseName: String? = null

        var clauseIndex = -1
        if (patternOwner is ArendClause) {
            val body = ownerParent.ancestor<ArendFunctionBody>()?.let {
                if (it.kind == ArendFunctionBody.Kind.COCLAUSE) it.parent.ancestor() else it
            }
            val func = body?.parent

            if (ownerParent is ArendFunctionClauses)
                clauseIndex = ownerParent.clauseList.indexOf(patternOwner)
            else if (ownerParent is ArendFunctionBody && ownerParent.kind == ArendFunctionBody.Kind.COCLAUSE) {
                coClauseName = ownerParent.ancestor<ArendCoClause>()?.longName?.referenceName
                clauseIndex = ownerParent.clauseList.indexOf(patternOwner)
            }

            if (body is ArendFunctionBody && func is ArendFunctionDefinition<*>) {
                definition = func
            }
        }
        if (patternOwner is ArendConstructorClause && ownerParent is ArendDataBody) {
            /* val data = ownerParent.parent
            abstractPatterns = patternOwner.patterns
            if (data is ArendDefData) definition = data */
            return null // TODO: Implement some behavior for constructor clauses as well
        }

        if (definition != null && clauseIndex != -1) {
            val tcReferable = (definition as? ReferableBase<*>)?.tcReferable
            var typeCheckedDefinition: Definition = tcReferable?.typechecked ?: return null
            var concreteClauseOwner = server.getResolvedDefinition(tcReferable)?.definition as? Concrete.GeneralDefinition ?: return null

            if (typeCheckedDefinition is FunctionDefinition && concreteClauseOwner is Concrete.FunctionDefinition && definition is Abstract.ParametersHolder && definition is Abstract.EliminatedExpressionsHolder) {
                if (coClauseName != null) {
                  val classCallExpression = typeCheckedDefinition.resultType as? ClassCallExpression
                  val expr = classCallExpression?.implementations?.firstOrNull { it.key.name == coClauseName }?.value
                  typeCheckedDefinition = ((expr as? LamExpression)?.body as? FunCallExpression)?.definition ?: typeCheckedDefinition
                  concreteClauseOwner = server.getResolvedDefinition(typeCheckedDefinition.referable)?.definition as? Concrete.FunctionDefinition ?: concreteClauseOwner
                }

                val elimBody = (((typeCheckedDefinition as? FunctionDefinition)?.actualBody as? IntervalElim)?.otherwise
                        ?: ((typeCheckedDefinition as? FunctionDefinition)?.actualBody as? ElimBody) ?: return null)

                val corePatterns = elimBody.clauses.getOrNull(clauseIndex)?.patterns?.let { toExpressionPatterns(it, typeCheckedDefinition.parameters) }
                        ?: return null

                val parameters = ArrayList<Abstract.AbstractReferable>(); for (pp in definition.parameters) parameters.addAll(pp.referableList)
                val elimVars = definition.eliminatedExpressions ?: emptyList()
                val isElim = elimVars.isNotEmpty()
                val elimVarPatterns: List<ExpressionPattern> = if (isElim) elimVars.map { reference ->
                    if (reference is ArendRefIdentifier) {
                        val parameterIndex = (reference.reference?.resolve() as? Abstract.AbstractReferable)?.let { parameters.indexOf(it) }
                                ?: -1
                        if (parameterIndex < corePatterns.size && parameterIndex != -1) corePatterns[parameterIndex] else throw IllegalStateException()
                    } else throw IllegalStateException()
                } else corePatterns

                if (indexList.isNotEmpty()) {
                    val concreteClause = (concreteClauseOwner as Concrete.FunctionDefinition).body.clauses[clauseIndex]
                    val index = patternOwner
                            .patterns
                            .filterIsInstance<ArendPattern>()
                            .indexOfFirst {
                                it.skipSingleTuples() == indexList[0]
                            }
                    val (typecheckedPattern, concrete) = (if (isElim) elimVarPatterns.getOrNull(index)?.let { it to  concreteClause.patterns.find { it.data == indexList[0] } }
                    else findMatchingPattern(concreteClause.patterns, typeCheckedDefinition.parameters, corePatterns, indexList[0])) ?: return null
                    if (concrete == null) return null
                    val patternPart = findPattern(indexList.drop(1), typecheckedPattern, concrete) as? BindingPattern
                            ?: return null
                    return patternPart.binding.typeExpr
                }
            }
        }

        if (ownerParent is ArendWithBody && patternOwner is ArendClause) {
            val clauseIndex2 = ownerParent.clauseList.indexOf(patternOwner)
            val caseExprData = tryCorrespondedSubExpr(ownerParent.textRange, patternOwner.containingFile, project, editor, false)
            val coreCaseExpr = caseExprData?.subCore
            if (coreCaseExpr is CaseExpression) {
                val coreClause = coreCaseExpr.elimBody.clauses.getOrNull(clauseIndex2)
                caseClauseParameters = coreClause?.parameters
                val bindingData = caseExprData.findBinding(element.textRange)
                return bindingData?.second
            }
        }

        return null
    }

    companion object {
        abstract class SplitPatternEntry(recursiveDefinition: Definition?,
                                         recursiveParameterName: String?,
                                         val isExplicit: Boolean) {
            protected val referableRenamer = ReferableRenamer()

            init {
                referableRenamer.setParameterName(recursiveDefinition, recursiveParameterName)
            }

            abstract fun patternConcrete(freeVars: Collection<Variable>): Pair<Concrete.Pattern, Concrete.Expression>

            protected fun generateConcreteParameters(parameters: List<Variable>, freeVars: Collection<Variable>): List<Referable> {
                val concreteParameters = ArrayList<LocalReferable>()
                val freeVarsSet = HashSet(freeVars)
                for (parameter in parameters) {
                    concreteParameters.add (referableRenamer.generateFreshReferable(parameter, freeVarsSet))
                    freeVarsSet.add(parameter)
                }
                return concreteParameters
            }

        }

        class ConstructorSplitPatternEntry(val anchor: PsiElement,
                                           val constructor: Definition,
                                           isExplicit: Boolean,
                                           recursiveDefinition: Definition?,
                                           recursiveParameterName: String?,
                                           val parametersToRemove: Collection<DependentLink> = emptyList()) : SplitPatternEntry(recursiveDefinition, recursiveParameterName, isExplicit) {
            override fun patternConcrete(freeVars: Collection<Variable>): Pair<Concrete.Pattern, Concrete.Expression> {
                val coreParameters = DependentLink.Helper.toList(constructor.parameters).minus(parametersToRemove.toSet())
                val concreteParameters = generateConcreteParameters(coreParameters, freeVars)
                val concreteParametersWithExplicitness = coreParameters.map { it.isExplicit }.toList().zip(concreteParameters)
                val psiReferable = constructor.referable.data as? PsiLocatedReferable
                val referable = if (psiReferable != null && anchor is ArendCompositeElement) {
                   val p = ResolveReferenceAction.getTargetName(psiReferable, anchor)
                   p?.second?.execute()
                   val l = LongName.fromString(p?.first ?: "").toList()
                   if (p?.first != null && l.size > 1)
                     LongUnresolvedReference(psiReferable, null, l)
                   else
                     constructor.referable
                } else constructor.referable

                val resultPattern = Concrete.ConstructorPattern(null, isExplicit, null, referable, concreteParametersWithExplicitness.map { Concrete.NamePattern(null, it.first, it.second, null) }, null)
                val resultExpression = Concrete.AppExpression.make(null, Concrete.ReferenceExpression(null, constructor.referable), concreteParametersWithExplicitness.map {
                    Concrete.Argument(Concrete.ReferenceExpression(null, it.second), it.first)
                })
                return Pair(resultPattern, resultExpression)
            }

        }

        class TupleSplitPatternEntry(val type: SigmaExpression,
                                     isExplicit: Boolean) : SplitPatternEntry(null, null, isExplicit) {
            override fun patternConcrete(freeVars: Collection<Variable>): Pair<Concrete.Pattern, Concrete.Expression> {
                val concreteParameters = generateConcreteParameters(DependentLink.Helper.toList(type.parameters), freeVars)
                val resultPattern = Concrete.TuplePattern(null, isExplicit, concreteParameters.map { Concrete.NamePattern(null, true, it, null) }, null)
                val resultExpression = Concrete.TupleExpression(null, concreteParameters.map { Concrete.ReferenceExpression(null, it) })
                return Pair(resultPattern, resultExpression)
            }
        }

        class ClassSplitPatternEntry(val classCall: ClassCallExpression,
                                     isExplicit: Boolean) : SplitPatternEntry(null, null, isExplicit) {
            val notImplementedFields = classCall.definition.notImplementedFields.filter { !classCall.isImplementedHere(it) }

            init {
                referableRenamer.setForceTypeSCName(true)
            }

            override fun patternConcrete(freeVars: Collection<Variable>): Pair<Concrete.Pattern, Concrete.Expression> {
                val concreteParameters = generateConcreteParameters(notImplementedFields, freeVars)
                val expr = ToAbstractVisitor.convert(classCall, object : PrettyPrinterConfig { override fun getNormalizationMode(): NormalizationMode? = null })
                val additionalArguments = concreteParameters.map { Concrete.Argument(Concrete.ReferenceExpression(null, it), true) }
                val appExpr = if (expr is Concrete.AppExpression) { expr.arguments.addAll(additionalArguments); expr } else
                    Concrete.AppExpression.make(null, expr, additionalArguments)

                val resultPattern = Concrete.TuplePattern(null, isExplicit, concreteParameters.map { Concrete.NamePattern(null, true, it, null) }, null)
                val resultExpression = Concrete.NewExpression(null, appExpr)
                return Pair(resultPattern, resultExpression)
            }
        }

        class IdpPatternEntry(val project: Project, isExplicit: Boolean): SplitPatternEntry(null, null, isExplicit) {
            override fun patternConcrete(freeVars: Collection<Variable>): Pair<Concrete.Pattern, Concrete.Expression> {
                val resultPattern = Concrete.ConstructorPattern(null, isExplicit, null, Prelude.IDP.ref, emptyList(), null)
                val resultExpression = Concrete.AppExpression.make(null, Concrete.ReferenceExpression(null, Prelude.IDP.ref), emptyList())
                return Pair(resultPattern, resultExpression)
            }
        }

        fun doSplitPattern(patternPsiToSplit: PsiElement,
                           project: Project,
                           splitPatternEntries: Collection<SplitPatternEntry>) {
            if (patternPsiToSplit !is ArendPattern || !patternPsiToSplit.isValid) return

            val server = project.service<ArendServerService>().server
            val docManager = PsiDocumentManager.getInstance(project)
            val arendFile = patternPsiToSplit.containingFile as? ArendFile ?: return
            val document = docManager.getDocument(arendFile) ?: return

            val tcReferable = (patternPsiToSplit.ancestor<ReferableBase<*>>())?.tcReferable ?: return
            val concreteClauseOwner = tcReferable.let { server.getResolvedDefinition(it) }?.definition as? Concrete.Definition ?: return
            val concreteClause = findConcreteByPsi(concreteClauseOwner, Concrete.FunctionClause::class.java, patternPsiToSplit.ancestor<ArendClause>()!!)
                ?: return
            val clausePsi = (concreteClause.data as? PsiElement) ?: return
            val pipe = clausePsi.findPrevSibling() ?: return // Leading PIPE
            val textRangeToReplace = TextRange(pipe.textRange.startOffset, clausePsi.textRange.endOffset)
            val patternToSplit = findConcreteByPsi(concreteClauseOwner, Concrete.Pattern::class.java, patternPsiToSplit)
                ?: return

            val referableToReplace = (patternToSplit as? Concrete.NamePattern)?.referable
            val newClauses = ArrayList<Concrete.Clause>()
            val serverBasedDefinitionRenamer = ServerBasedDefinitionRenamer(server, DummyErrorReporter.INSTANCE, tcReferable, arendFile)
            for (entry in splitPatternEntries) {
                val freeVars = HashSet<Variable>()

                val collector = LocalVariablesCollector(concreteClause.expression?.data ?: concreteClause.data)
                concreteClauseOwner.accept(collector, null)
                val names = collector.names
                if (referableToReplace != null) names.remove(referableToReplace.refName)
                freeVars.addAll(names.filterNotNull().map{ VariableImpl(it) })

                val p = entry.patternConcrete(freeVars)

                val substitution = HashMap<Referable, Concrete.Expression>()
                if (referableToReplace != null) substitution[referableToReplace] = p.second

                val numberSimplifyingVisitor = NumberSimplifyingConcreteVisitor()
                val renamerConcreteVisitor = DefinitionRenamerConcreteVisitor(serverBasedDefinitionRenamer)
                val substVisitor = object: SubstConcreteVisitor(substitution, null) {
                    override fun visitPattern(pattern: Concrete.Pattern?): Concrete.Pattern? {
                        if (pattern == patternToSplit) {
                            return p.first
                        }
                        return super.visitPattern(pattern)
                    }
                }

                val x1 = substVisitor.visitClause(concreteClause)
                renamerConcreteVisitor.visitClauses(singletonList(x1), null)
                val x3 = numberSimplifyingVisitor.visitClause(x1)
                if (x3.expression == null) x3.expression = Concrete.GoalExpression(null, "", null)

                newClauses.add(x3)
            }

            val builder = StringBuilder()
            var isFirst = true
            for (newClause in newClauses) {
                if (!isFirst) builder.append("\n")
                builder.append(newClause.prettyPrint(PrettyPrinterConfig.DEFAULT))
                isFirst = false
            }

            if (newClauses.isEmpty() && clausePsi is ArendClause) {
                clausePsi.fatArrow?.let {document.replaceString(it.startOffset, clausePsi.textRange.endOffset, "")}
                document.replaceString(patternPsiToSplit.startOffset, patternPsiToSplit.endOffset, "()")
            } else {
                document.replaceString(textRangeToReplace.startOffset, textRangeToReplace.endOffset, builder.toString())
                CodeStyleManager.getInstance(project).reformatText(arendFile, textRangeToReplace.startOffset, textRangeToReplace.startOffset + builder.length)
            }

            docManager.commitDocument(document)
            serverBasedDefinitionRenamer.getAction()?.execute()
        }

        private fun doFindVariablePatterns(variables: MutableSet<String>, pattern: Concrete.Pattern, excludedPsi: PsiElement?) {
            if (pattern is Concrete.NamePattern) {
                pattern.referable?.refName?.let { variables.add(it) }
            } else {
                for (subPattern in pattern.patterns)
                    doFindVariablePatterns(variables, subPattern, excludedPsi)
            }
        }

        /**
         * @return the owner of pattern alongside with a top-down path to this pattern
         */
        fun locatePattern(element: PsiElement): Pair<Abstract.Clause, List<ArendPattern>>? {
            var pattern: PsiElement? = element
            val indexList = ArrayList<ArendPattern>()

            while (pattern is ArendPattern) {
                if (pattern.skipSingleTuples() == pattern) {
                    indexList.add(pattern)
                }
                pattern = pattern.parent
            }

            if (pattern == null) return null
            val clause: Abstract.Clause = pattern as? Abstract.Clause ?: return null
            return Pair(clause, indexList.reversed())
        }

        private fun findPattern(indexList: List<ArendPattern>, typecheckedPattern: ExpressionPattern, concretePattern: Concrete.Pattern): ExpressionPattern? {
            if (indexList.isEmpty()) return typecheckedPattern
            if (typecheckedPattern is ConstructorExpressionPattern) {
                val (typecheckedPatternChild, concretePatternChild) = findMatchingPattern(concretePattern.patterns, typecheckedPattern.parameters, typecheckedPattern.subPatterns, indexList[0]) ?: return null
                var i = 0
                while (indexList[i] != indexList[0].skipSingleTuples()) i++
                return findPattern(indexList.drop(i + 1), typecheckedPatternChild, concretePatternChild)
            }
            return null
        }

        private fun findReplaceablePsiElement(indexList: List<ArendPattern>, concretePattern: Concrete.Pattern?): Concrete.Pattern? {
            if (indexList.isEmpty()) return concretePattern
            val concretePatternChild = concretePattern?.patterns?.firstNotNullOfOrNull { findDeepInArguments(it, indexList[0]) }
            var i = 0
            while (indexList[i] != indexList[0].skipSingleTuples()) i++
            if (concretePatternChild != null) return findReplaceablePsiElement(indexList.drop(i + 1), concretePatternChild)
            return null
        }

        private fun findMatchingPattern(concretePatterns: List<Concrete.Pattern>,
                                        parameters: DependentLink,
                                        typecheckedPatterns: List<ExpressionPattern>,
                                        requiredPsi: ArendPattern): Pair<ExpressionPattern, Concrete.Pattern>? {
            var link = parameters
            var i = 0
            var j = 0

            while (link.hasNext() && i < concretePatterns.size) {
                val isEqual = link.isExplicit == concretePatterns[i].isExplicit
                if (isEqual) {
                    when (matches(concretePatterns[i], requiredPsi)) {
                        MatchData.NO -> Unit
                        MatchData.DIRECT -> return typecheckedPatterns.getOrNull(j)?.let { it to concretePatterns[i] }
                        MatchData.DEEP -> {
                            val typechecked = typecheckedPatterns[j] as ConstructorExpressionPattern
                            return findMatchingPattern(concretePatterns[i].patterns, typechecked.parameters, typechecked.subPatterns, requiredPsi)
                        }
                    }
                }
                if (isEqual || link.isExplicit) i++
                if (isEqual || !link.isExplicit) {
                    link = link.next
                    j++
                }
            }

            return null
        }

        fun doSubstituteUsages(project: Project, elementToReplace: ArendReferenceElement?, element: PsiElement, expressionLine: String, resolver: (ArendReferenceElement) -> PsiElement? = { it.reference?.resolve() }) {
            if (elementToReplace == null || element is ArendWhere) return
            val resolved = (element as? ArendReferenceElement)?.let { resolver.invoke(it) }

            if ((element is ArendRefIdentifier || element is ArendIPName) &&
                resolved == elementToReplace &&
                withAncestors(ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java).accepts(element)) {
                val literal = element.parent as ArendLiteral
                val atom = literal.parent as ArendAtom
                val factory = ArendPsiFactory(project)

                val atomFieldsAcc = atom.parent as? ArendAtomFieldsAcc
                val argumentAppExpr = atomFieldsAcc?.parent as? ArendArgumentAppExpr
                val arendNewExpr = argumentAppExpr?.parent as? ArendNewExpr

                val substitutedExpression = factory.createExpression(expressionLine) as ArendNewExpr
                val substitutedAtom = if (needParentheses(element, element.textRange, substitutedExpression, null))
                    factory.createExpression("($expressionLine)").descendantOfType() else
                        substitutedExpression.descendantOfType<ArendAtom>()

                if (arendNewExpr != null && atomFieldsAcc.fieldAccList.isEmpty() && argumentAppExpr.argumentList.isEmpty() &&
                    arendNewExpr.let { it.lbrace == null && it.rbrace == null }) {
                    arendNewExpr.replace(substitutedExpression)
                } else if (substitutedAtom != null) {
                    atom.replace(substitutedAtom)
                }
            } else for (child in element.children)
                doSubstituteUsages(project, elementToReplace, child, expressionLine)
        }
    }
}

private enum class MatchData {
    NO,
    DIRECT,
    DEEP
}

private fun matches(concreteNode: Concrete.Pattern, element: ArendPattern) : MatchData {
    return if (concreteNode.data == element.skipSingleTuples()) {
        MatchData.DIRECT
    } else if (findDeepInArguments(concreteNode, element) != null) {
        MatchData.DEEP
    } else {
        MatchData.NO
    }
}

private fun findDeepInArguments(node: Concrete.Pattern, element: ArendPattern) : Concrete.Pattern? {
    if (node.data == element.skipSingleTuples()) {
        return node
    }
    if (node !is Concrete.ConstructorPattern) {
        return node.takeIf { it.data == element.skipSingleTuples() }
    }
    return node.patterns.firstNotNullOfOrNull { findDeepInArguments(it, element) }
}

private fun findParentConcrete(node: Concrete.Pattern, element: ArendPattern) : Concrete.Pattern? {
    if (node.data == element.skipSingleTuples()) {
        error("Descended too deep")
    }
    if (node !is Concrete.ConstructorPattern) {
        return null
    }
    return if (node.patterns.any { it.data == element.skipSingleTuples() }) {
        node
    } else {
        node.patterns.firstNotNullOfOrNull { findParentConcrete(it, element) }
    }
}

private tailrec fun ArendPattern.skipSingleTuples() : ArendPattern {
    return if (this.type != null && this.sequence.size == 1) {
        this.sequence[0].skipSingleTuples()
    } else if (this.isTuplePattern && this.sequence.size == 1) {
        this.sequence[0].skipSingleTuples()
    } else {
        this
    }
}