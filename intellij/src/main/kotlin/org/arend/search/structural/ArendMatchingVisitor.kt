package org.arend.search.structural

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.structuralsearch.impl.matcher.GlobalMatchingVisitor
import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.naming.reference.AliasReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendArgumentAppExpr
import org.arend.psi.ArendExpr
import org.arend.psi.ArendVisitor
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.impl.CoClauseDefAdapter
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.psi.ext.impl.FunctionDefinitionAdapter
import org.arend.term.Fixity
import org.arend.term.concrete.Concrete
import org.arend.term.prettyprint.FreeVariableCollectorConcrete
import org.arend.util.appExprToConcrete

class ArendMatchingVisitor(private val matchingVisitor: GlobalMatchingVisitor) : ArendVisitor() {

    val binOpParser = BinOpParser(DummyErrorReporter.INSTANCE)

    private fun performMatch(pattern: Concrete.Expression, matched: Concrete.Expression): Boolean {
        if (pattern is Concrete.HoleExpression) {
            return true
        }
        if (pattern is Concrete.AppExpression && matched is Concrete.AppExpression) {
            val patternFunction = pattern.function
            val matchedFunction = matched.function
            if (!performMatch(patternFunction, matchedFunction)) {
                return false
            }
            val concreteArguments = matched.arguments.mapNotNull { if (it.isExplicit) it.expression else null }
            val patternArguments = pattern.arguments.map { it.expression }
            if (patternArguments.size != concreteArguments.size) {
                return false
            }
            for ((patternArg, matchedArg) in patternArguments.zip(concreteArguments)) {
                if (!performMatch(patternArg, matchedArg)) {
                    return false
                }
            }
            return true
        } else if (pattern is Concrete.ReferenceExpression) {
            return pattern.underlyingReferable?.resolveAlias() == matched.underlyingReferable?.resolveAlias()
        } else {
            return false
        }
    }

    private fun reassembleConcrete(tree: PatternTree, scope : Scope, references : Map<String, List<Referable>>): Concrete.Expression? =
        when (tree) {
            is PatternTree.BranchingNode -> {
                val binOpList = ArrayList<Concrete.BinOpSequenceElem>(tree.subNodes.size)
                for (i in tree.subNodes.indices) {
                    val expr = reassembleConcrete(tree.subNodes[i], scope, references) ?: break
                    val binOp = if (i == 0) {
                        Concrete.BinOpSequenceElem(expr, Fixity.NONFIX, true)
                    } else {
                        Concrete.BinOpSequenceElem(expr, Fixity.UNKNOWN, true)
                    }
                    binOpList.add(binOp)
                }
                if (binOpList.size != tree.subNodes.size) {
                    null
                } else {
                    binOpParser.parse(Concrete.BinOpSequenceExpression(null, binOpList, null))
                }
            }
            is PatternTree.LeafNode -> {
                val referable = Scope.resolveName(scope, tree.referenceName, false)
                    ?: references[tree.referenceName.last()]?.let { disambiguate(it, tree.referenceName) }
                if (referable != null) {
                    val refExpr = Concrete.FixityReferenceExpression.make(null, referable, Fixity.UNKNOWN, null, null)
                    refExpr ?: Concrete.HoleExpression(tree.referenceName)
                } else {
                    null
                }
            }
            PatternTree.Wildcard -> Concrete.HoleExpression(null)
        }

    override fun visitExpr(o: ArendExpr) {
        super.visitExpr(o)
        val matchedElement = this.matchingVisitor.element
        matchingVisitor.result = false
        val parentType =
            matchedElement.parentOfType<ArendFunctionalDefinition>()?.returnExpr?.exprList?.getOrNull(0) ?: return
        if (!PsiTreeUtil.isAncestor(parentType, matchedElement, false)) {
            return
        }
        if (matchedElement is ArendArgumentAppExpr) {
            val referable = matchedElement.parentOfType<DefinitionAdapter<*>>() ?: return
            val concrete = getType(referable) ?: return
            val patternTree = o.getPatternTree()
            val mscope = CachingScope.make(matchedElement.scope)
            val parsedConcrete =
                if (concrete is Concrete.BinOpSequenceExpression) binOpParser.parse(concrete) else concrete
            val qualifiedReferables by lazy(LazyThreadSafetyMode.NONE) {
                val set = mutableSetOf<Referable>()
                parsedConcrete.accept(FreeVariableCollectorConcrete(set), null)
                set.groupBy { it.refName }
            }
            val patternConcrete = reassembleConcrete(patternTree, mscope, qualifiedReferables) ?: return
            if (performMatch(patternConcrete, parsedConcrete)) {
                matchingVisitor.result = true
            }
        }
    }
}

private fun getType(def: DefinitionAdapter<*>): Concrete.Expression? {
    // todo: reuse core definitions where possible to achieve matching with explicitly untyped definitions and implicit arguments
    return when (def) {
        is CoClauseDefAdapter -> def.resultType?.let(::appExprToConcrete)
        is FunctionDefinitionAdapter -> def.resultType?.let(::appExprToConcrete)
        else -> null
    }
}

private fun disambiguate(candidates: List<Referable>, path: List<String>): Referable? {
    var result: Referable? = null
    for (candidate in candidates) {
        val longName = candidate.refLongName ?: continue
        if (longName.toList().subList(longName.size() - path.size, longName.size()) == path) {
            if (result == null) {
                result = candidate
            } else {
                // there are two referables with the same suffix, it is ambiguous
                return null
            }
        }
    }
    return result
}

private fun Referable.resolveAlias(): Referable = if (this is AliasReferable) underlyingReferable else this