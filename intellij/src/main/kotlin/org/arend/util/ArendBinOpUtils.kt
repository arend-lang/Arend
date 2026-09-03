package org.arend.util

import com.intellij.lang.ASTNode
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.resolving.typing.TypingInfo
import org.arend.psi.ancestor
import org.arend.psi.ext.*
import org.arend.psi.ext.ArendExpr
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.concrete.BaseConcreteExpressionVisitor
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.SearchConcreteVisitor
import org.arend.term.concrete.SubstConcreteVisitor

fun exprToConcrete1(appExpr: ArendExpr): List<Concrete.SourceNode> {
    val result = ArrayList<Concrete.SourceNode>()
    val definition = appExpr.ancestor<ArendDefinition<*>>()?.tcReferable ?: return result
    val concrete = appExpr.project.service<ArendServerService>().server.getResolvedDefinition(definition)?.definition ?: return result
    concrete.accept(object : SearchConcreteVisitor<Any?, Concrete.SourceNode?>() {
        override fun checkSourceNode(sourceNode: Concrete.SourceNode, params: Any?): Concrete.SourceNode? {
            var data: PsiElement? = sourceNode.data as? PsiElement
            val textRange = data?.textRange ?: return null
            while (data != null && data.textRange == textRange) {
                if (data == appExpr && sourceNode is Concrete.Expression) {
                    result.add(sourceNode)
                    return null
                }

                data = data.parent
            }
            return null
        }
    }, null) as? Concrete.Expression
    return result
}

/**
 * The `Concrete.Expression` that the resolved definition holds for [appExpr], or `null` when none of
 * the candidates can be trusted to correspond to it.
 *
 * [exprToConcrete1] matches by text range rather than by identity, so one PSI expression can have
 * several concrete nodes answering for it, and two kinds have to be filtered out:
 *
 * - **A wrapper colliding with its own function.** An application normally reuses its function's data
 *   as its own -- a chain of field calls `a.f.g` nests that way, and the outermost one is what
 *   consumers want. But when the function is a complete-value expression carrying its own PSI (the
 *   lambda produced for a `__` section) rather than a reference, a field call or an application, the
 *   synthetic application wrapped around it reuses that data too and so collides with it among the
 *   candidates. The wrapper holds nothing its function does not, so prefer the function.
 * - **A node claiming a range it does not cover.** A meta resolver builds its result through
 *   `ConcreteFactoryImpl(data)`, which stamps one PSI element -- usually an argument's -- on a whole
 *   synthesized subtree, so an application can report the range of a sub-expression while
 *   structurally spanning more. Every consumer assumes the opposite: the formatter maps the parts back
 *   onto AST children, and the inspections, intentions and refactorings rewrite the text the parts
 *   point at. Only data lying *outside* the range is rejected, because the function often shares the
 *   range legitimately -- [appExpr] is sometimes a bare reference (`ArendChangeSignatureProcessor`
 *   passes an `ArendAtomFieldsAcc`), and the `__` lambda above does the same. A function that spans
 *   the node without fitting into any single child is handled where that actually matters, in
 *   `ArgumentAppExprBlock.transform`.
 *
 * `null` is a supported outcome: callers fall back to a representation that makes no structural claim
 * -- the formatter to `SimpleArendBlock`, the inspections and intentions to doing nothing.
 */
fun appExprToConcrete(appExpr: ArendExpr): Concrete.Expression? {
    val matches = exprToConcrete1(appExpr)
    val matchIdentitySet = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Concrete.SourceNode, Boolean>())
    matchIdentitySet.addAll(matches)
    fun isCollisionWrapper(node: Concrete.SourceNode) =
        node is Concrete.AppExpression && matchIdentitySet.contains(node.function) &&
        node.function !is Concrete.ReferenceExpression && node.function !is Concrete.FieldCallExpression && node.function !is Concrete.AppExpression

    val textRange = appExpr.textRange
    fun escapesTextRange(node: Concrete.SourceNode): Boolean {
        if (node !is Concrete.AppExpression) return false
        val functionData = node.function.data
        if (functionData is PsiElement && !textRange.contains(functionData.textRange)) return true
        return node.arguments.any {
            val data = it.expression.data
            data is PsiElement && !textRange.contains(data.textRange)
        }
    }

    return matches.firstOrNull { !isCollisionWrapper(it) && !escapesTextRange(it) } as? Concrete.Expression
}

/**
 * Converts an `ArendExpr` expression into a `Concrete.Expression` representation.
 * The conversion replaces every non-top-level ArendArgumentAppExpr with HoleExpression.
 *
 * @param appExpr The `ArendExpr` instance to be converted.
 * @return A `Concrete.Expression` instance representing the converted expression,
 *         or `null` if the conversion fails.
 */
fun appExprToConcreteOnlyTopLevel(appExpr: ArendExpr): Concrete.Expression? {
    val concrete = appExprToConcrete(appExpr)
    val concreteCopy = concrete?.accept(SubstConcreteVisitor(HashMap(), null), null)
    concreteCopy?.accept(object : BaseConcreteExpressionVisitor<Void>() {
        override fun visitApp(
            expr: Concrete.AppExpression?,
            params: Void?
        ): Concrete.Expression? {
            return if (expr?.data is ArendArgumentAppExpr && expr.data != concreteCopy.data)
                Concrete.HoleExpression(expr.data) else
                super.visitApp(expr, params)
        }
    }, null)

    return concreteCopy
}

fun patternToConcrete(unparsedPattern: ArendPattern): Concrete.Pattern? {
    val definition = unparsedPattern.ancestor<ArendDefinition<*>>()?.tcReferable ?: return null
    val concrete = unparsedPattern.project.service<ArendServerService>().server.getResolvedDefinition(definition)?.definition ?: return null
    return concrete.accept(object : SearchConcreteVisitor<Any?, Concrete.SourceNode?>() {
        override fun checkSourceNode(sourceNode: Concrete.SourceNode, params: Any?): Concrete.SourceNode? =
            if (sourceNode.data == unparsedPattern)
                sourceNode
            else
                null
    }, null) as? Concrete.Pattern
}

fun getBounds(cExpr: Concrete.SourceNode, aaeBlocks: List<ASTNode>, rangesMap: HashMap<Concrete.SourceNode, TextRange>? = null): TextRange? {
    val cExprData = cExpr.data
    var result: TextRange? = null

    if (rangesMap != null && rangesMap[cExpr] != null) return rangesMap[cExpr]

    if (cExpr is Concrete.AppExpression || cExpr is Concrete.ConstructorPattern) {
        val elements = ArrayList<TextRange>()
        val fData = when (cExpr) {
            is Concrete.AppExpression -> cExpr.function.data
            is Concrete.ConstructorPattern -> cExpr.constructorData
            else -> throw IllegalStateException()
        }

        val args = when (cExpr) {
            is Concrete.AppExpression -> cExpr.arguments
            is Concrete.ConstructorPattern -> cExpr.patterns
            else -> throw IllegalStateException()
        }

        elements.addAll(args.asSequence().mapNotNull {
            val key = when (it) {
                is Concrete.Argument -> it.expression
                is Concrete.Pattern -> it
                else -> throw IllegalStateException()
            }
            getBounds(key, aaeBlocks, rangesMap)}
        )

        if (fData is PsiElement) {
            val f = aaeBlocks.filter { it.textRange.contains(fData.textRange) }
            if (f.size == 1) { // f may be empty if neither of aaeBlocks corresponds
                val functionRange = f.first().textRange
                elements.add(functionRange)
                if (cExpr is Concrete.AppExpression)
                    rangesMap?.put(cExpr.function, functionRange)
            }
        }

        val startOffset = elements.asSequence().map { it.startOffset }.minOrNull()
        val endOffset = elements.asSequence().map { it.endOffset }.maxOrNull()
        if (startOffset != null && endOffset != null) {
            result = TextRange.create(startOffset, endOffset)
        }
    } else if (cExpr is Concrete.LamExpression) { //cExpr.data == null, so this is most likely a postfix or apply hole expression
        result = getBounds(cExpr.body, aaeBlocks, rangesMap)
    } else if (cExpr is Concrete.ProjExpression) {
        result = getBounds(cExpr.expression, aaeBlocks, rangesMap)
    } else if (cExprData is PsiElement) {
        for (psi in aaeBlocks)
            if (psi.textRange.contains(cExprData.node.textRange)) {
            result = psi.textRange
            break
        }
    }

    if (result != null)
        rangesMap?.put(cExpr, result)
    return result
}

fun concreteDataToSourceNode(data: Any?): ArendSourceNode? =
    if (data is ArendIPName) data.parentOfType() else data as? ArendSourceNode

fun checkConcreteExprIsArendExpr(aExpr: Abstract.SourceNode, cExpr: Concrete.Expression): Boolean {
    val checkConcreteExprDataIsArendNode = ret@{ cData: ArendSourceNode?, aNode: Abstract.SourceNode ->
        // Rewrite in a less ad-hoc way
        if (cData?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode ||
                cData?.topmostEquivalentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode
                || cData?.parentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode
                || cData?.parentSourceNode?.parentSourceNode?.parentSourceNode?.topmostEquivalentSourceNode == aNode.topmostEquivalentSourceNode
        ) {
            return@ret true
        }
        return@ret false
    }
    if (cExpr is Concrete.AppExpression) {
        return false
    }
    /*if (aExpr is ArendImplicitArgument) {
        val expr = aExpr.tupleExprList.firstOrNull()?.let { it.type ?: it.expr } ?: return false
        return checkConcreteExprDataIsArendNode(concreteDataToSourceNode(cExpr.data), expr)
    }*/
    return checkConcreteExprDataIsArendNode(concreteDataToSourceNode(cExpr.data), aExpr)
}

private fun getReferenceContainer(expr: Concrete.Expression) = (expr as? Concrete.ReferenceExpression)?.data as? ArendReferenceContainer

data class DefAndArgsInParsedBinopResult(val functionReferenceContainer: ArendReferenceContainer,
                                         val operatorConcrete: Concrete.Expression,
                                         val argumentsConcrete: List<Concrete.Argument>)

fun findDefAndArgsInParsedBinop(arg: ArendExpr, parsedExpr: Concrete.Expression): DefAndArgsInParsedBinopResult? {
    if (checkConcreteExprIsArendExpr(arg, parsedExpr)) {
        getReferenceContainer(parsedExpr)?.let {
            return DefAndArgsInParsedBinopResult(it, parsedExpr, emptyList())
        }
    }

    if (parsedExpr is Concrete.AppExpression) {
        if (checkConcreteExprIsArendExpr(arg, parsedExpr.function)) {
            getReferenceContainer(parsedExpr.function)?.let {
                return DefAndArgsInParsedBinopResult(it, parsedExpr, parsedExpr.arguments)
            }
        }

        findDefAndArgsInParsedBinop(arg, parsedExpr.function)?.let { return it }

        for (argument in parsedExpr.arguments) {
            if (checkConcreteExprIsArendExpr(arg, argument.expression)) {
                getReferenceContainer(argument.expression)?.let {
                    return DefAndArgsInParsedBinopResult(it, argument.expression, emptyList())
                }
                return getReferenceContainer(parsedExpr.function)?.let { DefAndArgsInParsedBinopResult(it, parsedExpr, parsedExpr.arguments) }
            }
        }

        for (argument in parsedExpr.arguments)
            findDefAndArgsInParsedBinop(arg, argument.expression)?.let { return it }

    } else if (parsedExpr is Concrete.LamExpression)
        return findDefAndArgsInParsedBinop(arg, parsedExpr.body)

    return null
}

/**
 * Whether [referent], written at the use site as [writtenName], is an infix operator there.
 *
 * A definition with an alias exports both names, and only one of them may carry `\infix`, so the
 * answer depends on which one was written. The precedence of the name itself comes from
 * [TypingInfo], which is what resolves a coclause to the field it implements.
 */
fun TypingInfo.isInfixReference(referent: GlobalReferable, writtenName: String): Boolean =
        getRefPrecedence(referent).isInfix && (!referent.hasAlias() || referent.refName == writtenName) ||
                referent.aliasPrecedence.isInfix && referent.aliasName == writtenName

fun isBinOp(binOpReference: ArendReferenceContainer?): Boolean {
    if (binOpReference is ArendIPName) return binOpReference.infix != null
    val referable = binOpReference?.resolve as? Abstract.AbstractLocatedReferable ?: return false
    val writtenName = binOpReference.referenceName
    (referable as? ReferableBase<*>)?.tcReferable?.let {
        return binOpReference.project.service<ArendServerService>().server.typingInfo.isInfixReference(it, writtenName)
    }
    return psiIsInfix(referable, writtenName)
}

/**
 * The answer [isBinOp] falls back on when the server has not built a concrete group for the
 * definition yet, so no `TCDefReferable` is available to ask [TypingInfo] about. Mirrors
 * [isInfixReference] on the PSI, including the walk from a coclause to the field it implements.
 */
private fun psiIsInfix(referable: Abstract.AbstractLocatedReferable, writtenName: String): Boolean {
    if (referable.aliasName == writtenName) return referable.aliasPrecedence.isInfix
    if (referable.kind != GlobalReferable.Kind.COCLAUSE_FUNCTION) return referable.precedence.isInfix
    val field = (referable as? ArendCoClauseDef)?.implementedField as? ArendReferenceContainer
        ?: return referable.precedence.isInfix
    val fieldReferable = field.resolve as? Abstract.AbstractLocatedReferable ?: return referable.precedence.isInfix
    return if (fieldReferable.aliasName == field.referenceName) fieldReferable.aliasPrecedence.isInfix
           else fieldReferable.precedence.isInfix
}

/**
 * [action] returns true if the processing should be stopped.
 */
fun forEachRange(concrete: Concrete.Expression, action: (TextRange, Concrete.Expression) -> Boolean): TextRange? {
    var result: TextRange? = null

    fun doVisit(concrete: Concrete.Expression): TextRange? {
        when (concrete) {
            is Concrete.AppExpression -> {
                val childRanges = mutableListOf<TextRange>()
                for (arg in concrete.arguments) {
                    val argRange = doVisit(arg.expression) ?: return null
                    if (action(argRange, arg.expression)) {
                        result = argRange
                        return null
                    }
                    childRanges.add(argRange)
                }
                childRanges.add((concrete.function.data as PsiElement).textRange)
                val data = concrete.data as? PsiElement
                if (data != null && data is ArendArgumentAppExpr) {
                    data.ancestor<ArendAtomFieldsAcc>()?.let { childRanges.add(it.textRange) }
                }
                return TextRange(childRanges.minOf { it.startOffset }, childRanges.maxOf { it.endOffset })
            }
            else -> return (concrete.data as PsiElement).textRange
        }
    }
    val resultRange = doVisit(concrete)
    if (resultRange != null && action(resultRange, concrete)) {
        result = resultRange
    }
    return result
}