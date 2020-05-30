package org.arend.codeInsight.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.StandardPatterns.*
import com.intellij.psi.*
import com.intellij.psi.TokenType.BAD_CHARACTER
import com.intellij.psi.util.elementType
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.isNullOrEmpty
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.search.ArendWordScanner
import org.arend.term.abs.Abstract
import java.util.*
import kotlin.math.max
import kotlin.math.min

class ArendCompletionContributor : CompletionContributor() {

    init {
        basic(PREC_CONTEXT, FIXITY_KWS)

        basic(after(and(ofType(LEVEL_KW), elementPattern { o -> (o.parent as? ArendDefFunction)?.functionKw?.useKw != null })), FIXITY_KWS)

        basic(and(withGrandParent(ArendNsId::class.java), withParents(ArendRefIdentifier::class.java, PsiErrorElement::class.java), not(afterLeaves(LPAREN, COMMA))), AS_KW_LIST)
        { parameters -> (parameters.position.parent.parent as ArendNsId).asKw == null }

        basic(NS_CMD_CONTEXT, HU_KW_LIST) { parameters ->
            parameters.originalPosition?.parent is ArendFile
        }

        fun noUsing(cmd: ArendStatCmd): Boolean = cmd.nsUsing?.usingKw == null
        fun noHiding(cmd: ArendStatCmd): Boolean = cmd.hidingKw == null
        fun noUsingAndHiding(cmd: ArendStatCmd): Boolean = noUsing(cmd) && noHiding(cmd)

        basic(NS_CMD_CONTEXT, USING_KW_LIST) { parameters ->
            noUsing(parameters.position.parent.parent as ArendStatCmd)
        }

        basic(NS_CMD_CONTEXT, HIDING_KW_LIST) { parameters ->
            noUsingAndHiding(parameters.position.parent.parent as ArendStatCmd)
        }

        basic(and(withAncestors(PsiErrorElement::class.java, ArendNsUsing::class.java, ArendStatCmd::class.java), not(afterLeaf(COMMA))), HIDING_KW_LIST) { parameters ->
            noHiding(parameters.position.parent.parent.parent as ArendStatCmd)
        }

        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(STATEMENT_WT_KWS))

        fun noDataCondition(cP : ArendCompletionParameters) = cP.nextElement.elementType == DATA_KW

        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(TRUNCATED_DATA_KW_LIST, { completionParameters -> !noDataCondition(completionParameters) }))

        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(listOf(TRUNCATED_KW.toString()), { completionParameters -> noDataCondition(completionParameters) }))

        basic(STATEMENT_END_CONTEXT, JointOfStatementsProvider(IMPORT_KW_LIST, { parameters ->
            val noWhere = { seq: Sequence<PsiElement> -> seq.filter { it is ArendWhere || it is ArendDefClass }.none() }

            parameters.leftStatement?.ancestors.let { if (it != null) noWhere(it) else true } &&
                    parameters.rightStatement?.ancestors.let { if (it != null) noWhere(it) else true } &&
                    (!parameters.leftBrace || parameters.ancestorsPE.isEmpty() || noWhere(parameters.ancestorsPE.asSequence())) &&
                    (!parameters.rightBrace || parameters.ancestorsNE.isEmpty() || noWhere(parameters.ancestorsNE.asSequence()))
        }))

        val definitionWhereModulePattern = { allowData: Boolean, allowFunc: Boolean ->
            elementPattern { o ->
                var foundWhere = false
                var foundLbrace = false
                var result2 = false
                var ancestor: PsiElement? = o
                while (ancestor != null) {
                    if (ancestor is ArendFieldTele && ancestor.lbrace != null) foundLbrace = true
                    if (ancestor is ArendWhere) foundWhere = true
                    if ((allowData && ancestor is ArendDefData || allowFunc && ancestor is ArendDefFunction)) {
                        result2 = foundWhere
                        break
                    } else if (ancestor is ArendDefClass) {
                        if (ancestor.lbrace != null) foundLbrace = true
                        result2 = if (allowData) foundWhere else !foundWhere && foundLbrace
                        break
                    } else if (ancestor is ArendDefinition && foundWhere) {
                        result2 = false
                        break
                    }
                    ancestor = ancestor.parent
                }
                result2
            }
        }

        basic(and(STATEMENT_END_CONTEXT, definitionWhereModulePattern(false, false)), JointOfStatementsProvider(CLASS_MEMBER_KWS, allowBeforeClassFields = true))

        basic(and(STATEMENT_END_CONTEXT, definitionWhereModulePattern(true, true)), JointOfStatementsProvider(USE_KW_LIST))

        basic(and(afterLeaf(USE_KW), definitionWhereModulePattern(true, false)), COERCE_KW_LIST)

        basic(afterLeaf(USE_KW), LEVEL_KW_LIST)

        basic(and(DATA_CONTEXT, afterLeaf(TRUNCATED_KW)), DATA_KW_LIST)//data after \truncated keyword

        val wherePattern = elementPattern { o ->
            var anc: PsiElement? = o
            while (anc != null && anc !is ArendGroup && anc !is ArendClassStat) anc = anc.parent
            anc is ArendGroup && anc !is ArendFile && anc.where == null
        }

        basic(and(WHERE_CONTEXT, after(wherePattern)),
                JointOfStatementsProvider(WHERE_KW_LIST, noCrlfRequired = true, allowInsideBraces = false))

        val inDefClassPattern = or(and(ofType(ID), withAncestors(ArendDefIdentifier::class.java, ArendDefClass::class.java)),
                and(ofType(RPAREN), withAncestors(ArendFieldTele::class.java, ArendDefClass::class.java)),
                and(ofType(ID), withAncestors(ArendAliasIdentifier::class.java, ArendAlias::class.java, ArendDefClass::class.java)))

        val noExtendsPattern = elementPattern {
            val dC = it.ancestor<ArendDefClass>()
            dC != null && dC.extendsKw == null
        }

        val notWithGrandparentFieldTelePattern = elementPattern { o -> o.parent?.parent?.findNextSibling() !is ArendFieldTele }

        basic(and(withAncestors(PsiErrorElement::class.java, ArendDefClass::class.java),
                afterLeaf(ID),
                noExtendsPattern,
                after(inDefClassPattern)),
                EXTENDS_KW_LIST)

        basic(and(withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java, ArendDefClass::class.java),
                noExtendsPattern,
                after(inDefClassPattern),
                notWithGrandparentFieldTelePattern),
                EXTENDS_KW_LIST)

        basic(and(DATA_CONTEXT, afterLeaf(COLON)), DATA_UNIVERSE_KW, tailSpaceNeeded = false)

        val bareSigmaOrPiPattern = elementPattern { o ->
            var result: PsiElement? = o

            val context = ofType(PI_KW, SIGMA_KW)

            var tele: ArendTypeTele? = null
            while (result != null) {
                if (result is ArendTypeTele) tele = result
                if (result is ArendExpr && result !is ArendUniverseAtom) break
                result = result.parent
            }

            if (context.accepts(o)) true else
                if (tele == null || tele.text.startsWith("(")) false else //Not Bare \Sigma or \Pi -- should display all expression keywords in completion
                    result is ArendSigmaExpr || result is ArendPiExpr
        }

        val allowedInReturnPattern = elementPattern { o ->
            var result: PsiElement? = o
            while (result != null) {
                if (result is ArendReturnExpr) break
                result = result?.parent
            }
            val resultC = result
            val resultParent = resultC?.parent
            when (resultC) {
                is ArendReturnExpr -> when {
                    resultC.levelKw != null -> false
                    resultParent is ArendDefInstance -> false
                    resultParent is ArendDefFunction -> !resultParent.isCowith
                    else -> true
                }
                else -> true
            }
        }

        val noExpressionKwsAfterPattern = ofType(SET, PROP_KW, UNIVERSE, TRUNCATED_UNIVERSE, NEW_KW, EVAL_KW, PEVAL_KW)
        val afterElimVarPattern = and(ofType(ID), withAncestors(ArendRefIdentifier::class.java, ArendElim::class.java))

        val expressionPattern = { allowInBareSigmaOrPiExpressions: Boolean, allowInArgumentExpressionContext: Boolean ->
            not(or(after(withAncestors(ArendFieldAcc::class.java, ArendAtomFieldsAcc::class.java)), //No keyword completion after field
                    and(or(withAncestors(*RETURN_EXPR_PREFIX), withAncestors(*(NEW_EXPR_PREFIX + arrayOf(ArendReturnExpr::class.java)))), not(allowedInReturnPattern)),
                    after(and(ofType(RBRACE), withParent(ArendCaseExpr::class.java))), //No keyword completion after \with or } in case expr
                    after(ofType(LAM_KW, LET_KW, LETS_KW, WITH_KW)), //No keyword completion after \lam or \let
                    after(noExpressionKwsAfterPattern), //No expression keyword completion after universe literals or \new keyword
                    or(LPH_CONTEXT, LPH_LEVEL_CONTEXT), //No expression keywords when completing levels in universes
                    after(afterElimVarPattern), //No expression keywords in \elim expression
                    if (allowInBareSigmaOrPiExpressions) PlatformPatterns.alwaysFalse() else after(bareSigmaOrPiPattern), //Only universe expressions allowed inside Sigma or Pi expressions
                    if (allowInArgumentExpressionContext) PlatformPatterns.alwaysFalse() else
                        or(ARGUMENT_EXPRESSION, withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java))))
        }

        basic(and(EXPRESSION_CONTEXT, expressionPattern.invoke(true, true)), DATA_UNIVERSE_KW, tailSpaceNeeded = false)
        basic(or(TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT), DATA_UNIVERSE_KW, tailSpaceNeeded = false)

        basic(and(EXPRESSION_CONTEXT, expressionPattern.invoke(false, false)), BASIC_EXPRESSION_KW)
        basic(and(EXPRESSION_CONTEXT, expressionPattern.invoke(false, true)), NEW_KW_LIST)

        val truncatedTypeInsertHandler = InsertHandler<LookupElement> { insertContext, _ ->
            val document = insertContext.document
            document.insertString(insertContext.tailOffset, " ") // add tail whitespace
            insertContext.commitDocument()
            insertContext.editor.caretModel.moveToOffset(insertContext.startOffset + 1)
            document.replaceString(insertContext.startOffset + 1, insertContext.startOffset + 2, "1") //replace letter n by 1 so that the keyword would be highlighted correctly
            insertContext.editor.selectionModel.setSelection(insertContext.startOffset + 1, insertContext.startOffset + 2)
        }

        val truncatedTypeCompletionProvider = object : KeywordCompletionProvider(FAKE_NTYPE_LIST) {
            override fun insertHandler(keyword: String): InsertHandler<LookupElement> = truncatedTypeInsertHandler
        }

        basic(and(DATA_CONTEXT, afterLeaf(COLON)), truncatedTypeCompletionProvider)
        basic(and(EXPRESSION_CONTEXT, expressionPattern(true, true)), object : KeywordCompletionProvider(FAKE_NTYPE_LIST) {
            override fun insertHandler(keyword: String): InsertHandler<LookupElement> = truncatedTypeInsertHandler
        })
        basic(or(TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT), truncatedTypeCompletionProvider)

        val numberCondition = { parameters: CompletionParameters ->
            val originalPositionParent = parameters.originalPosition?.parent
            originalPositionParent != null && originalPositionParent.text == "\\" && originalPositionParent.nextSibling?.node?.elementType == NUMBER
        }

        basic(and(DATA_OR_EXPRESSION_CONTEXT), object : ConditionalProvider(listOf("-Type"), numberCondition, tailSpaceNeeded = false) {
            override fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String = ""
        })

        basic(DATA_OR_EXPRESSION_CONTEXT, object : ConditionalProvider(listOf("Type"), { parameters ->
            parameters.originalPosition?.text?.matches(Regex("\\\\[0-9]+(-(T(y(pe?)?)?)?)?")) ?: false
        }, tailSpaceNeeded = false) {
            override fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String =
                    super.computePrefix(parameters, resultSet).replace(Regex("\\\\[0-9]+-?"), "")
        })

        basic(LPH_CONTEXT, LPH_KW_LIST) { parameters ->
            when (val pp = parameters.position.parent.parent) {
                is ArendSetUniverseAppExpr, is ArendTruncatedUniverseAppExpr ->
                    pp.children.filterIsInstance<ArendMaybeAtomLevelExpr>().isEmpty()
                else -> pp.children.filterIsInstance<ArendMaybeAtomLevelExpr>().size <= 1
            }
        }

        basic(withParent(ArendLevelExpr::class.java), LPH_KW_LIST) { parameters ->
            when (parameters.position.parent?.firstChild?.node?.elementType) {
                MAX_KW, SUC_KW -> true
                else -> false
            }
        }

        basic(LPH_LEVEL_CONTEXT, LPH_LEVEL_KWS)

        fun pairingWordCondition(condition: (PsiElement?) -> Boolean, position: PsiElement): Boolean {
            var pos: PsiElement? = position
            var exprFound = false
            while (pos != null) {
                if (condition.invoke(pos)) {
                    exprFound = true
                    break
                }
                if (!(pos.nextSibling == null || pos.nextSibling is PsiErrorElement)) break
                pos = pos.parent
            }
            return exprFound
        }

        val pairingInPattern = elementPattern { o -> pairingWordCondition({ position: PsiElement? -> position is ArendLetExpr && position.inKw == null }, o) }
        val pairingWithPattern = elementPattern { o -> pairingWordCondition({ position: PsiElement? -> position is ArendCaseExpr && position.withKw == null }, o) }

        val returnPattern = elementPattern { o ->
            pairingWordCondition({ position: PsiElement? ->
                if (position is ArendCaseArg) {
                    val pp = position.parent as? ArendCaseExpr
                    pp != null && pp.caseArgList.lastOrNull() == position && pp.returnKw == null
                } else false
            }, o)
        }

        val asCondition1 = { position: PsiElement? -> position is ArendCaseArg && position.caseArgExprAs.asKw == null }

        val asCondition2 = { position: PsiElement? ->
            //Alternative condition needed to ensure that as is added before semicolon
            var p = position
            if (p != null && p.nextSibling is PsiWhiteSpace) p = p.nextSibling.nextSibling
            p != null && p.node.elementType == COLON
        }

        val argEndPattern = elementPattern { o ->
            pairingWordCondition({ position: PsiElement? ->
                asCondition1.invoke(position) || (position != null && asCondition2.invoke(position) && asCondition1.invoke(position.parent))
            }, o)
        }

        basic(and(EXPRESSION_CONTEXT, not(or(afterLeaf(IN_KW), afterLeaf(LET_KW), afterLeaf(LETS_KW))), pairingInPattern), IN_KW_LIST)

        val caseContext = and(
                or(EXPRESSION_CONTEXT,
                        withAncestors(PsiErrorElement::class.java, ArendCaseArgExprAs::class.java, ArendCaseArg::class.java, ArendCaseExpr::class.java)),
                not(afterLeaves(WITH_KW, CASE_KW, SCASE_KW, COLON)))

        basic(and(caseContext, pairingWithPattern), KeywordCompletionProvider(WITH_KW_LIST, tailSpaceNeeded = false))

        basic(and(caseContext, argEndPattern), AS_KW_LIST)

        basic(and(caseContext, returnPattern), RETURN_KW_LIST)

        val emptyTeleList = { l: List<Abstract.Parameter> ->
            l.isEmpty() || l.size == 1 && (l[0].type == null || (l[0].type as PsiElement).text == DUMMY_IDENTIFIER_TRIMMED) &&
                    (l[0].referableList.size == 0 || l[0].referableList[0] == null || (l[0].referableList[0] as PsiElement).text == DUMMY_IDENTIFIER_TRIMMED)
        }
        val elimOrCoWithCondition = { coWithMode: Boolean ->
            { cP: CompletionParameters ->
                var pos2: PsiElement? = cP.position
                var exprFound = false
                while (pos2 != null) {
                    if (pos2.nextSibling is PsiWhiteSpace) {
                        val nextElement = pos2.findNextSibling()
                        if (nextElement is ArendFunctionBody || nextElement is ArendDataBody || nextElement is ArendWhere) pos2 = nextElement.parent
                    }

                    if ((pos2 is ArendDefFunction)) {
                        val fBody = pos2.functionBody
                        exprFound = fBody == null || fBody.fatArrow == null && fBody.elim?.elimKw == null
                        exprFound = exprFound &&
                                if (!coWithMode) !emptyTeleList(pos2.nameTeleList)  // No point of writing elim keyword if there are no arguments
                                else {
                                    val returnExpr = pos2.returnExpr
                                    returnExpr != null && returnExpr.levelKw == null
                                } // No point of writing cowith keyword if there is no result type or there is already \level keyword in result type
                        exprFound = exprFound && (fBody == null || fBody.cowithKw == null && fBody.elim.let { it == null || it.elimKw == null && it.withKw == null })
                        break
                    }
                    if ((pos2 is ArendDefData) && !coWithMode) {
                        val dBody = pos2.dataBody
                        exprFound = dBody == null || (dBody.elim?.elimKw == null && dBody.constructorList.isNullOrEmpty() && dBody.constructorClauseList.isNullOrEmpty())
                        exprFound = exprFound && !emptyTeleList(pos2.typeTeleList)
                        break
                    }

                    if (pos2 is ArendConstructor && pos2.elim == null && !coWithMode) {
                        exprFound = !emptyTeleList(pos2.typeTeleList)
                        break
                    }

                    if (pos2 is ArendClause || pos2 is ArendCoClause) break

                    if (pos2?.nextSibling == null) pos2 = pos2?.parent else break
                }
                exprFound
            }
        }

        basic(ELIM_CONTEXT, ELIM_KW_LIST, elimOrCoWithCondition.invoke(false))
        basic(ELIM_CONTEXT, ConditionalProvider(WITH_KW_LIST, elimOrCoWithCondition.invoke(false), tailSpaceNeeded = false))
        basic(ELIM_CONTEXT, ConditionalProvider(COWITH_KW_LIST, elimOrCoWithCondition.invoke(true), tailSpaceNeeded = false))

        basic(and(
                afterLeaves(COMMA, CASE_KW),
                or(withAncestors(PsiErrorElement::class.java, ArendCaseExpr::class.java),
                        withAncestors(*(NEW_EXPR_PREFIX + arrayOf(ArendCaseArgExprAs::class.java, ArendCaseArg::class.java, ArendCaseExpr::class.java))))), ELIM_KW_LIST)

        val isLiteralApp = { argumentAppExpr: ArendArgumentAppExpr ->
            argumentAppExpr.longNameExpr != null ||
                    ((argumentAppExpr.children[0] as? ArendAtomFieldsAcc)?.atom?.literal?.longName != null)
        }

        val unifiedLevelCondition = { atomIndex: Int?, forbidLevelExprs: Boolean, threshold: Int ->
            { cP: CompletionParameters ->
                var anchor: PsiElement? = if (atomIndex != null) cP.position.ancestors.filterIsInstance<ArendAtomFieldsAcc>().elementAtOrNull(atomIndex) else null
                var argumentAppExpr: ArendArgumentAppExpr? =
                        (anchor?.parent as? ArendAtomArgument)?.parent as? ArendArgumentAppExpr
                                ?: anchor?.parent as? ArendArgumentAppExpr
                if (anchor == null) {
                    anchor = cP.position.parent
                    argumentAppExpr = anchor?.parent as? ArendArgumentAppExpr
                    if (argumentAppExpr == null) {
                        anchor = null
                    }
                }

                if (anchor == null) {
                    argumentAppExpr = cP.position.parent as? ArendArgumentAppExpr
                }

                if (argumentAppExpr != null && anchor != null && isLiteralApp(argumentAppExpr)) {
                    var counter = argumentAppExpr.longNameExpr?.atomOnlyLevelExprList?.size ?: 0
                    var forbidden = false
                    val levelsExpr = argumentAppExpr.longNameExpr?.levelsExpr
                    if (levelsExpr != null) {
                        counter += levelsExpr.maybeAtomLevelExprList.size
                        if (forbidLevelExprs) forbidden = true
                    }
                    for (ch in argumentAppExpr.children) {
                        if (ch == anchor || ch == anchor.parent) break
                        if (ch is ArendAtomArgument) forbidden = true
                    }
                    counter < threshold && !forbidden
                } else argumentAppExpr?.longNameExpr?.levelsExpr?.levelsKw != null && isLiteralApp(argumentAppExpr)
            }
        }

        basic(ARGUMENT_EXPRESSION, LPH_KW_LIST, unifiedLevelCondition.invoke(0, false, 2))
        basic(ARGUMENT_EXPRESSION, LEVEL_KW_LIST, unifiedLevelCondition.invoke(0, true, 1))
        basic(ARGUMENT_EXPRESSION_IN_BRACKETS, LPH_LEVEL_KWS, unifiedLevelCondition.invoke(1, false, 2))

        basic(afterLeaf(NEW_KW), listOf(EVAL_KW.toString()))
        basic(afterLeaves(EVAL_KW, PEVAL_KW, SCASE_KW), listOf(SCASE_KW.toString()))

        basic(withAncestors(PsiErrorElement::class.java, ArendArgumentAppExpr::class.java), LPH_LEVEL_KWS, unifiedLevelCondition.invoke(null, false, 2))

        basic(withParent(ArendArgumentAppExpr::class.java), LPH_LEVEL_KWS) { parameters ->
            val argumentAppExpr: ArendArgumentAppExpr? = parameters.position.parent as ArendArgumentAppExpr
            argumentAppExpr?.longNameExpr?.levelsExpr?.levelsKw != null && isLiteralApp(argumentAppExpr)
        }


        basic(NO_CLASSIFYING_CONTEXT, NO_CLASSIFYING_KW_LIST) { parameters ->
            parameters.position.ancestor<ArendDefClass>().let { defClass ->
                defClass != null && defClass.noClassifyingKw == null && !defClass.fieldTeleList.any { it.isClassifying }
            }
        }

        basic(CLASSIFYING_CONTEXT, CLASSIFYING_KW_LIST) { parameters ->
            parameters.position.ancestor<ArendDefClass>().let { defClass ->
                defClass != null && defClass.noClassifyingKw == null && !defClass.fieldTeleList.any { it.isClassifying }
            }
        }


        basic(and(LEVEL_CONTEXT, allowedInReturnPattern), LEVEL_KW_LIST)

        basic(and(afterLeaf(ID), or(
                withAncestors(PsiErrorElement::class.java, ArendDefModule::class.java),
                after(and(withParent(ArendDefIdentifier::class.java), withGrandParents(ArendDefData::class.java, ArendDefInstance::class.java,
                        ArendDefFunction::class.java, ArendConstructor::class.java, ArendDefClass::class.java, ArendClassField::class.java))),
                after(withAncestors(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendClassImplement::class.java)))), ALIAS_KW_LIST)

        //basic(PlatformPatterns.psiElement(), Logger())
    }

    private fun basic(pattern: ElementPattern<PsiElement>, provider: CompletionProvider<CompletionParameters>) {
        extend(CompletionType.BASIC, pattern, provider)
    }

    private fun basic(place: ElementPattern<PsiElement>, keywords: List<String>, tailSpaceNeeded: Boolean = true) {
        basic(place, KeywordCompletionProvider(keywords, tailSpaceNeeded = tailSpaceNeeded))
    }

    private fun basic(place: ElementPattern<PsiElement>, keywords: List<String>, condition: (CompletionParameters) -> Boolean) {
        basic(place, ConditionalProvider(keywords, condition))
    }

    companion object {
        const val KEYWORD_PRIORITY = 0.0
        private val LITERAL_PREFIX = arrayOf<Class<out PsiElement>>(ArendRefIdentifier::class.java, ArendLongName::class.java, ArendLiteral::class.java)
        private val ATOM_PREFIX = LITERAL_PREFIX + arrayOf(ArendAtom::class.java)
        private val ATOM_FIELDS_ACC_PREFIX = ATOM_PREFIX + arrayOf(ArendAtomFieldsAcc::class.java)
        private val ARGUMENT_APP_EXPR_PREFIX = ATOM_FIELDS_ACC_PREFIX + arrayOf(ArendArgumentAppExpr::class.java)
        private val NEW_EXPR_PREFIX = ARGUMENT_APP_EXPR_PREFIX + arrayOf(ArendNewExpr::class.java)
        private val RETURN_EXPR_PREFIX = ATOM_FIELDS_ACC_PREFIX + arrayOf(ArendReturnExpr::class.java)

        private val PREC_CONTEXT = or(
                afterLeaves(FUNC_KW, SFUNC_KW, LEMMA_KW, CONS_KW, DATA_KW, CLASS_KW, RECORD_KW),
                and(afterLeaf(AS_KW), withGrandParent(ArendNsId::class.java)),
                and(afterLeaf(PIPE), withGrandParents(ArendConstructor::class.java, ArendDataBody::class.java)), //simple data type constructor
                and(afterLeaf(FAT_ARROW), withGrandParents(ArendConstructor::class.java, ArendConstructorClause::class.java)), //data type constructors with patterns
                and(afterLeaf(PIPE), withGrandParents(ArendClassField::class.java, ArendClassStat::class.java))) //class field

        private val NS_CMD_CONTEXT = withAncestors(PsiErrorElement::class.java, ArendStatCmd::class.java)

        private val STATEMENT_END_CONTEXT = or(withParents(PsiErrorElement::class.java, ArendRefIdentifier::class.java),
                withAncestors(ArendDefIdentifier::class.java, ArendFieldDefIdentifier::class.java)) //Needed for correct completion inside empty classes

        private val INSIDE_RETURN_EXPR_CONTEXT = or(withAncestors(*RETURN_EXPR_PREFIX), withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendReturnExpr::class.java))

        private val WHERE_CONTEXT = and(
                or(STATEMENT_END_CONTEXT, withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java)),
                not(PREC_CONTEXT),
                not(INSIDE_RETURN_EXPR_CONTEXT),
                not(afterLeaves(COLON, TRUNCATED_KW, FAT_ARROW, WITH_KW, ARROW, IN_KW, INSTANCE_KW, EXTENDS_KW, DOT, NEW_KW, EVAL_KW, PEVAL_KW, CASE_KW, SCASE_KW, LET_KW, WHERE_KW, USE_KW, PIPE, LEVEL_KW, COERCE_KW)),
                not(withAncestors(PsiErrorElement::class.java, ArendDefInstance::class.java)), // don't allow \where in incomplete instance expressions
                not(withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java, ArendDefInstance::class.java)))

        private val DATA_CONTEXT = withAncestors(PsiErrorElement::class.java, ArendDefData::class.java, ArendStatement::class.java)

        private val TELE_CONTAINERS = arrayOf<Class<out PsiElement>>(ArendClassField::class.java, ArendConstructor::class.java, ArendDefData::class.java, ArendPiExpr::class.java, ArendSigmaExpr::class.java, ArendFunctionalDefinition::class.java)
        private val TELE_CONTEXT = or(
                and(withAncestors(PsiErrorElement::class.java, ArendTypeTele::class.java), withGreatGrandParents(*TELE_CONTAINERS)),
                and(withParents(ArendTypeTele::class.java, ArendNameTele::class.java), withGrandParents(*TELE_CONTAINERS)),
                withAncestors(*(LITERAL_PREFIX + arrayOf(ArendTypeTele::class.java))))

        private val EXPRESSION_CONTEXT = and(
                or(withAncestors(*ATOM_PREFIX),
                        withParentOrGrandParent(ArendFunctionBody::class.java),
                        and(withParentOrGrandParent(ArendExpr::class.java), not(INSIDE_RETURN_EXPR_CONTEXT)),
                        withAncestors(PsiErrorElement::class.java, ArendClause::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendTupleExpr::class.java),
                        and(afterLeaf(LPAREN), withAncestors(PsiErrorElement::class.java, ArendReturnExpr::class.java)),
                        and(afterLeaf(COLON), withAncestors(PsiErrorElement::class.java, ArendDefFunction::class.java)),
                        and(afterLeaf(COLON), withParent(ArendDefClass::class.java)),
                        or(withParent(ArendClassStat::class.java), withAncestors(PsiErrorElement::class.java, ArendClassStat::class.java)),
                        and(ofType(INVALID_KW), withAncestors(ArendInstanceBody::class.java, ArendDefInstance::class.java)),
                        and(not(afterLeaf(LPAREN)), not(afterLeaf(ID)), withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java)),
                        TELE_CONTEXT),
                not(afterLeaves(PIPE, COWITH_KW))) // no expression keywords after pipe


        private val FIRST_TYPE_TELE_CONTEXT = and(afterLeaf(ID), withParent(PsiErrorElement::class.java),
                withGrandParents(ArendDefData::class.java, ArendClassField::class.java, ArendConstructor::class.java))

        private val DATA_OR_EXPRESSION_CONTEXT = or(DATA_CONTEXT, EXPRESSION_CONTEXT, TELE_CONTEXT, FIRST_TYPE_TELE_CONTEXT)

        private val ARGUMENT_EXPRESSION = or(withAncestors(*(ATOM_FIELDS_ACC_PREFIX + arrayOf(ArendAtomArgument::class.java))),
                withAncestors(PsiErrorElement::class.java, ArendAtomFieldsAcc::class.java, ArendArgumentAppExpr::class.java))

        private val LPH_CONTEXT = and(withParent(PsiErrorElement::class.java), withGrandParents(ArendSetUniverseAppExpr::class.java, ArendUniverseAppExpr::class.java, ArendTruncatedUniverseAppExpr::class.java))

        private val LPH_LEVEL_CONTEXT = and(withAncestors(PsiErrorElement::class.java, ArendAtomLevelExpr::class.java))

        private val ELIM_CONTEXT = and(
                not(afterLeaves(DATA_KW, FUNC_KW, SFUNC_KW, LEMMA_KW, CONS_KW, COERCE_KW, TRUNCATED_KW, COLON)),
                or(EXPRESSION_CONTEXT, TELE_CONTEXT, ARGUMENT_EXPRESSION,
                        withAncestors(ArendDefIdentifier::class.java, ArendIdentifierOrUnknown::class.java, ArendNameTele::class.java, ArendDefFunction::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendNameTele::class.java, ArendDefFunction::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendDefData::class.java)))

        private val ARGUMENT_EXPRESSION_IN_BRACKETS = withAncestors(*(NEW_EXPR_PREFIX +
                arrayOf<Class<out PsiElement>>(ArendTupleExpr::class.java, ArendTuple::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java, ArendAtomArgument::class.java, ArendArgumentAppExpr::class.java)))

        private val CLASSIFYING_CONTEXT = and(afterLeaf(LPAREN),
                or(withAncestors(ArendDefIdentifier::class.java, ArendFieldDefIdentifier::class.java, ArendFieldTele::class.java, ArendDefClass::class.java),
                        withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java, ArendDefClass::class.java)))

        private val NO_CLASSIFYING_CONTEXT = and(afterLeaf(ID),
                or(withAncestors(ArendFieldTele::class.java, ArendDefClass::class.java),
                    withAncestors(PsiErrorElement::class.java, ArendFieldTele::class.java, ArendDefClass::class.java)))

        private val LEVEL_CONTEXT_0 = withAncestors(*(NEW_EXPR_PREFIX + arrayOf(ArendReturnExpr::class.java)))

        private val LEVEL_CONTEXT = or(
                and(afterLeaf(COLON), or(LEVEL_CONTEXT_0, withAncestors(PsiErrorElement::class.java, ArendDefFunction::class.java),
                        withAncestors(ArendClassStat::class.java, ArendDefClass::class.java), withParent(ArendDefClass::class.java))),
                and(afterLeaf(RETURN_KW), or(LEVEL_CONTEXT_0, withAncestors(PsiErrorElement::class.java, ArendCaseExpr::class.java))))

        // Contribution to LookupElementBuilder
        fun LookupElementBuilder.withPriority(priority: Double): LookupElement = PrioritizedLookupElement.withPriority(this, priority)
    }

    private class Logger : CompletionProvider<CompletionParameters>() {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
            val text = parameters.position.containingFile.text

            val mn = max(0, parameters.position.node.startOffset - 15)
            val mx = min(text.length, parameters.position.node.startOffset + parameters.position.node.textLength + 15)
            println("")
            println("surround text: ${text.substring(mn, mx).replace("\n", "\\n")}")
            println("kind: " + parameters.position.javaClass + " text: " + parameters.position.text)
            var i = 0
            var pp: PsiElement? = parameters.position
            while (i < 13 && pp != null) {
                System.out.format("kind.parent(%2d): %-40s text: %-50s\n", i, pp.javaClass.simpleName, pp.text)
                pp = pp.parent
                i++
            }
            println("originalPosition.parent: " + parameters.originalPosition?.parent?.javaClass)
            println("originalPosition.grandparent: " + parameters.originalPosition?.parent?.parent?.javaClass)
            val jointData = ArendCompletionParameters(parameters)
            println("prevElement: ${jointData.prevElement} text: ${jointData.prevElement?.text}")
            println("prevElement.parent: ${jointData.prevElement?.parent?.javaClass}")
            println("prevElement.grandparent: ${jointData.prevElement?.parent?.parent?.javaClass}")
            println("nextElement: ${jointData.nextElement} text: ${jointData.nextElement?.text}")
            println("nextElement.parent: ${jointData.nextElement?.parent?.javaClass}")
            if (parameters.position.parent is PsiErrorElement) println("errorDescription: " + (parameters.position.parent as PsiErrorElement).errorDescription)
            println("")
            System.out.flush()
        }
    }

    private open class KeywordCompletionProvider(private val keywords: List<String>,
                                                 private val tailSpaceNeeded: Boolean = true,
                                                 private val disableAfter2Crlfs: Boolean = true) : CompletionProvider<CompletionParameters>() {

        open fun insertHandler(keyword: String): InsertHandler<LookupElement> = InsertHandler { insertContext, _ ->
            val document = insertContext.document
            if (tailSpaceNeeded) document.insertString(insertContext.tailOffset, " ") // add tail whitespace
            insertContext.commitDocument()
            insertContext.editor.caretModel.moveToOffset(insertContext.tailOffset)
        }

        open fun lookupElement(keyword: String): LookupElementBuilder = LookupElementBuilder.create(keyword)

        open fun computePrefix(parameters: CompletionParameters, resultSet: CompletionResultSet): String {
            var prefix = resultSet.prefixMatcher.prefix
            val lastInvalidIndex = prefix.mapIndexedNotNull { i, c -> if (!ArendWordScanner.isArendIdentifierPart(c)) i else null }.lastOrNull()
            if (lastInvalidIndex != null) prefix = prefix.substring(lastInvalidIndex + 1, prefix.length)
            val pos = parameters.offset - prefix.length - 1
            if (pos >= 0 && pos < parameters.originalFile.textLength)
                prefix = (if (parameters.originalFile.text[pos] == '\\') "\\" else "") + prefix
            return prefix
        }

        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
            if (parameters.position is PsiComment || afterLeaf(DOT).accepts(parameters.position) ||
                    disableAfter2Crlfs && ArendCompletionParameters.findPrevAnchor(parameters.offset, parameters.originalFile).first > 1) // Prevents showing kw completions in comments and after dot expression
                return

            val prefix = computePrefix(parameters, resultSet)

            val prefixMatcher = object : PlainPrefixMatcher(prefix) {
                override fun prefixMatches(name: String): Boolean = isStartMatch(name)
            }

            for (keyword in keywords)
                resultSet.withPrefixMatcher(prefixMatcher).addElement(lookupElement(keyword).withInsertHandler(insertHandler(keyword)).withPriority(KEYWORD_PRIORITY))
        }
    }

    private open class ConditionalProvider(keywords: List<String>, val condition: (CompletionParameters) -> Boolean,
                                           tailSpaceNeeded: Boolean = true,
                                           disableAfter2Crlfs: Boolean = true) :
            KeywordCompletionProvider(keywords, tailSpaceNeeded, disableAfter2Crlfs) {
        override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet) {
            if (condition.invoke(parameters)) {
                super.addCompletions(parameters, context, resultSet)
            }
        }
    }

    private open class JointOfStatementsProvider(keywords: List<String>,
                                                 additionalCondition: (ArendCompletionParameters) -> Boolean = { true },
                                                 tailSpaceNeeded: Boolean = true,
                                                 noCrlfRequired: Boolean = false,
                                                 allowInsideBraces: Boolean = true,
                                                 allowBeforeClassFields: Boolean = false) :
            ConditionalProvider(keywords, { parameters ->
                val arendCompletionParameters = ArendCompletionParameters(parameters)
                val leftSideOk = (arendCompletionParameters.leftStatement == null || arendCompletionParameters.leftBrace && allowInsideBraces)
                val rightSideOk = (arendCompletionParameters.rightStatement == null || arendCompletionParameters.rightBrace && !arendCompletionParameters.leftBrace)
                val correctStatements = rightSideOk || (leftSideOk || arendCompletionParameters.betweenStatementsOk) && (allowBeforeClassFields || !arendCompletionParameters.isBeforeClassFields)

                (arendCompletionParameters.delimiterBeforeCaret || noCrlfRequired) && additionalCondition.invoke(arendCompletionParameters) && correctStatements
            }, tailSpaceNeeded, disableAfter2Crlfs = false)

    private class ArendCompletionParameters(completionParameters: CompletionParameters) {
        val prevElement: PsiElement?
        val delimiterBeforeCaret: Boolean
        val nextElement: PsiElement?
        val ancestorsNE: List<PsiElement>
        val ancestorsPE: List<PsiElement>
        val leftBrace: Boolean
        val rightBrace: Boolean
        val leftStatement: PsiElement?
        val rightStatement: PsiElement?
        val isBeforeClassFields: Boolean
        val betweenStatementsOk: Boolean

        init {
            val caretOffset = completionParameters.offset
            val file = completionParameters.originalFile

            var ofs = 0
            var next: PsiElement?

            do {
                val pos = caretOffset + (ofs++)
                next = if (pos > file.textLength) null else file.findElementAt(pos)
            } while (next is PsiWhiteSpace || next is PsiComment)

            val p = findPrevAnchor(caretOffset, file)
            val prev = p.second

            delimiterBeforeCaret = p.first > 0
            nextElement = next
            prevElement = prev

            val statementCondition = { psi: PsiElement ->
                if (psi is ArendStatement) {
                    val p = psi.parent
                    !(p is ArendWhere && p.lbrace == null)
                } else psi is ArendClassStat
            }

            ancestorsNE = ancestorsUntil(statementCondition, next)
            ancestorsPE = ancestorsUntil(statementCondition, prev)

            leftBrace = prev?.node?.elementType == LBRACE && parentIsStatementHolder(prev)
            rightBrace = nextElement?.node?.elementType == RBRACE && parentIsStatementHolder(nextElement)
            leftStatement = ancestorsPE.lastOrNull()
            rightStatement = ancestorsNE.lastOrNull()
            isBeforeClassFields = rightStatement is ArendClassStat && rightStatement.definition == null
            betweenStatementsOk = leftStatement != null && rightStatement != null && ancestorsNE.intersect(ancestorsPE).isEmpty()
        }

        companion object {
            fun textBeforeCaret(whiteSpace: PsiWhiteSpace, caretOffset: Int): String = when {
                whiteSpace.textRange.contains(caretOffset) -> whiteSpace.text.substring(0, caretOffset - whiteSpace.textRange.startOffset)
                caretOffset < whiteSpace.textRange.startOffset -> ""
                else -> whiteSpace.text
            }

            fun parentIsStatementHolder(p: PsiElement?) = when (p?.parent) {
                is ArendWhere -> true
                is ArendDefClass -> true
                else -> false
            }

            fun ancestorsUntil(condition: (PsiElement) -> Boolean, element: PsiElement?): List<PsiElement> {
                val ancestors = ArrayList<PsiElement>()
                var elem: PsiElement? = element
                if (elem != null) ancestors.add(elem)
                while (elem != null && !condition.invoke(elem)) {
                    elem = elem.parent
                    if (elem != null) ancestors.add(elem)
                }
                return ancestors
            }

            fun findPrevAnchor(caretOffset: Int, file: PsiFile) : Pair<Int, PsiElement?> {
                var pos = caretOffset - 1
                var prev: PsiElement? = file.findElementAt(caretOffset)
                if (prev != null && STATEMENT_WT_KWS.contains(prev.text)) pos = prev.startOffset - 1

                var skippedFirstErrorExpr: PsiElement? = null
                var numberOfCrlfs = 0;

                do {
                    prev = if (pos <= 0) {
                        numberOfCrlfs = 1;
                        break
                    } else file.findElementAt(pos)

                    numberOfCrlfs += if (prev is PsiWhiteSpace) textBeforeCaret(prev, caretOffset).count { it == '\n' } else 0

                    var skipFirstErrorExpr = (prev?.node?.elementType == BAD_CHARACTER || (prev?.node?.elementType == INVALID_KW && prev?.parent is PsiErrorElement && prev.text.startsWith("\\")))
                    if (skipFirstErrorExpr && skippedFirstErrorExpr != null && skippedFirstErrorExpr != prev) skipFirstErrorExpr = false else skippedFirstErrorExpr = prev

                    pos = (prev?.textRange?.startOffset ?: pos) - 1
                } while (prev is PsiWhiteSpace || prev is PsiComment || skipFirstErrorExpr)

                return Pair(numberOfCrlfs, prev)
            }
        }
    }
}
