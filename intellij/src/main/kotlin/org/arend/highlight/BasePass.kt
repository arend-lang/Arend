package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.*
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.annotation.Annotation
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.xml.util.XmlStringUtil
import org.arend.codeInsight.completion.withAncestors
import org.arend.error.ErrorReporter
import org.arend.error.GeneralError
import org.arend.error.doc.DocFactory.vHang
import org.arend.error.doc.DocStringBuilder
import org.arend.naming.error.NamingError
import org.arend.naming.error.NamingError.Kind.*
import org.arend.naming.error.NotInScopeError
import org.arend.naming.reference.DataContainer
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.quickfix.*
import org.arend.quickfix.AbstractCoClauseInserter.Companion.makeFieldList
import org.arend.quickfix.referenceResolve.ArendImportHintAction
import org.arend.quickfix.removers.RemoveAsPatternQuickFix
import org.arend.quickfix.removers.RemoveClauseQuickFix
import org.arend.quickfix.removers.RemovePatternRightHandSideQuickFix
import org.arend.quickfix.removers.ReplaceWithWildcardPatternQuickFix
import org.arend.term.abs.IncompleteExpressionError
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.error.ArendError
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.*
import org.arend.typechecking.error.local.TypecheckingError.Kind.*

abstract class BasePass(protected val file: ArendFile, editor: Editor, name: String, protected val textRange: TextRange, highlightInfoProcessor: HighlightInfoProcessor)
    : ProgressableTextEditorHighlightingPass(file.project, editor.document, name, file, editor, textRange, false, highlightInfoProcessor), ErrorReporter {

    protected val holder = AnnotationHolderImpl(AnnotationSession(file))
    private val errorList = ArrayList<GeneralError>()

    override fun getDocument(): Document = super.getDocument()!!

    override fun applyInformationWithProgress() {
        val errorService = myProject.service<ErrorService>()

        for (error in errorList) {
            val list = error.cause?.let { it as? Collection<*> ?: listOf(it) } ?: return
            for (cause in list) {
                val psi = getCauseElement(cause)
                if (psi != null && psi.isValid) {
                    if (error !is IncompleteExpressionError) {
                        reportToEditor(error, psi)
                    }
                    errorService.report(ArendError(error, runReadAction { SmartPointerManager.createPointer(psi) }))
                }
            }
        }

        val highlights = holder.map { HighlightInfo.fromAnnotation(it) }
        ApplicationManager.getApplication().invokeLater({
            if (isValid) {
                UpdateHighlightersUtil.setHighlightersToEditor(myProject, document, textRange.startOffset, textRange.endOffset, highlights, colorsScheme, id)
            }
        }, ModalityState.stateForComponent(editor.component))
    }

    private fun createAnnotation(error: GeneralError, range: TextRange): Annotation {
        val ppConfig = PrettyPrinterConfig.DEFAULT
        return holder.createAnnotation(levelToSeverity(error.level), range, error.shortMessage, XmlStringUtil.escapeString(DocStringBuilder.build(vHang(error.getShortHeaderDoc(ppConfig), error.getBodyDoc(ppConfig)))).replace("\n", "<br>").replace(" ", "&nbsp;"))
    }

    fun reportToEditor(error: GeneralError, cause: ArendCompositeElement) {
        if (error is IncompleteExpressionError || file != cause.containingFile) {
            return
        }

        if (error is NotInScopeError) {
            val ref: ArendReferenceElement? = when (cause) {
                is ArendIPNameImplMixin -> cause.parentLongName?.refIdentifierList?.getOrNull(error.index) ?: cause
                is ArendReferenceElement -> cause
                is ArendLongName -> cause.refIdentifierList.getOrNull(error.index)
                else -> null
            }
            when (val resolved = ref?.reference?.resolve()) {
                is PsiDirectory -> holder.createErrorAnnotation(ref, "Unexpected reference to a directory")
                is PsiFile -> holder.createErrorAnnotation(ref, "Unexpected reference to a file")
                else -> {
                    val annotation = createAnnotation(error, (ref ?: cause).textRange)
                    if (resolved == null) {
                        annotation.highlightType = ProblemHighlightType.ERROR
                        if (ref != null && error.index == 0) {
                            val fix = ArendImportHintAction(ref)
                            if (fix.isAvailable(myProject, null, file)) {
                                annotation.registerFix(fix)
                            }
                        }
                    }
                }
            }
        } else {
            val annotation = createAnnotation(error, getImprovedTextRange(error, cause))
            when (error) {
                is FieldsImplementationError ->
                    if (error.alreadyImplemented) {
                        (error.cause.data as? ArendCoClause)?.let {
                            annotation.registerFix(RemoveCoClauseQuickFix(it))
                        }
                    } else {
                        AbstractCoClauseInserter.makeFirstCoClauseInserter(cause)?.let {
                            annotation.registerFix(ImplementFieldsQuickFix(it, false, makeFieldList(error.fields, error.classReferable)))
                        }
                        if (cause is ArendNewExprImplMixin) {
                            cause.putUserData(CoClausesKey, null)
                        }
                    }
                is DesugaringError -> if (error.kind == DesugaringError.Kind.REDUNDANT_COCLAUSE) {
                    annotation.highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    if (cause is ArendCoClause) {
                        annotation.registerFix(RemoveCoClauseQuickFix(cause))
                    }
                }

                is MissingClausesError -> annotation.registerFix(ImplementMissingClausesQuickFix(error, cause))

                is TypecheckingError -> {
                    if (error.level == GeneralError.Level.WEAK_WARNING) {
                        annotation.highlightType = ProblemHighlightType.LIKE_UNUSED_SYMBOL
                    }
                    when (error.kind) {
                        TOO_MANY_PATTERNS, EXPECTED_EXPLICIT_PATTERN, IMPLICIT_PATTERN -> if (cause is ArendPatternImplMixin) {
                            val single = error.kind == EXPECTED_EXPLICIT_PATTERN
                            if (error.kind != TOO_MANY_PATTERNS) {
                                cause.atomPattern?.let {
                                    annotation.registerFix(MakePatternExplicitQuickFix(it, single))
                                }
                            }

                            if (!single || cause.nextSibling.findNextSibling { it is ArendPatternImplMixin } != null) {
                                annotation.registerFix(RemovePatternsQuickFix(cause, single))
                            }
                        }
                        AS_PATTERN_IGNORED -> if (cause is ArendAsPattern) annotation.registerFix(RemoveAsPatternQuickFix(cause))
                        BODY_IGNORED ->
                            cause.ancestor<ArendClause>()?.let {
                                annotation.registerFix(RemovePatternRightHandSideQuickFix(it))
                            }
                        PATTERN_IGNORED ->  if (cause is ArendPatternImplMixin) annotation.registerFix(ReplaceWithWildcardPatternQuickFix(cause))
                        REDUNDANT_CLAUSE -> if (cause is ArendClause) annotation.registerFix(RemoveClauseQuickFix(cause))
                        else -> {}
                    }
                }
            }
        }
    }

    override fun report(error: GeneralError) {
        errorList.add(error)
    }

    companion object {
        fun levelToSeverity(level: GeneralError.Level): HighlightSeverity =
            when (level) {
                GeneralError.Level.ERROR -> HighlightSeverity.ERROR
                GeneralError.Level.WARNING -> HighlightSeverity.WARNING
                GeneralError.Level.WEAK_WARNING -> HighlightSeverity.WEAK_WARNING
                GeneralError.Level.GOAL -> HighlightSeverity.WARNING
                GeneralError.Level.INFO -> HighlightSeverity.INFORMATION
            }

        private fun getCauseElement(data: Any?): ArendCompositeElement? {
            val cause = data?.let { (it as? DataContainer)?.data ?: it }
            return ((cause as? SmartPsiElementPointer<*>)?.let { runReadAction { it.element } } ?: cause) as? ArendCompositeElement
        }

        private fun getImprovedErrorElement(error: GeneralError?, element: ArendCompositeElement): PsiElement? {
            val result = when (error) {
                is NamingError -> when (error.kind) {
                    MISPLACED_USE -> (element as? ArendDefFunction)?.useKw
                    MISPLACED_COERCE, COERCE_WITHOUT_PARAMETERS -> (element as? ArendDefFunction)?.coerceKw
                    LEVEL_IN_FIELD -> element.ancestor<ArendReturnExpr>()?.levelKw
                    CLASSIFYING_FIELD_IN_RECORD -> (element as? ArendFieldDefIdentifier)?.parent
                    INVALID_PRIORITY -> (element as? ReferableAdapter<*>)?.getPrec()?.number
                    MISPLACED_IMPORT -> (element as? ArendStatCmd)?.importKw
                    else -> null
                }
                is TypecheckingError -> when (error.kind) {
                    LEVEL_IN_FUNCTION -> element.ancestor<ArendReturnExpr>()?.levelKw
                    TRUNCATED_WITHOUT_UNIVERSE -> (element as? ArendDefData)?.truncatedKw
                    CASE_RESULT_TYPE -> (element as? ArendCaseExpr)?.caseKw
                    PROPERTY_LEVEL -> ((element as? ArendClassField)?.parent as? ArendClassStat)?.propertyKw
                    LEMMA_LEVEL -> if (element is ArendDefFunction) element.lemmaKw else element.ancestor<ArendReturnExpr>()?.levelKw
                    else -> null
                }
                is ExpectedConstructor -> (element as? ArendPattern)?.firstChild
                is AnotherClassifyingFieldError ->
                    (error.candidate.underlyingReferable as? ArendFieldDefIdentifier)?.let {
                        (it.parent as? ArendFieldTele)?.classifyingKw ?: it
                    }
                is ImplicitLambdaError -> error.parameter.underlyingReferable as? PsiElement
                else -> null
            }

            return result ?: when (element) {
                is PsiLocatedReferable -> element.defIdentifier
                is CoClauseBase -> element.longName
                else -> null
            }
        }

        fun getImprovedCause(error: GeneralError) = getCauseElement(error.cause)?.let { getImprovedErrorElement(error, it) ?: it }

        fun getImprovedTextRange(error: GeneralError) = getCauseElement(error.cause)?.let { getImprovedTextRange(error, it) }

        fun getImprovedTextRange(error: GeneralError?, element: ArendCompositeElement): TextRange {
            val improvedElement = getImprovedErrorElement(error, element) ?: element

            ((improvedElement as? ArendDefIdentifier)?.parent as? ArendDefinition)?.let {
                return TextRange(it.textRange.startOffset, improvedElement.textRange.endOffset)
            }

            ((improvedElement as? ArendLongName)?.parent as? CoClauseBase)?.let { coClause ->
                val endElement = coClause.expr?.let { if (isEmptyGoal(it)) it else null } ?: coClause.fatArrow ?: coClause.lbrace ?: improvedElement
                return TextRange(coClause.textRange.startOffset, endElement.textRange.endOffset)
            }

            if ((error as? TypecheckingError)?.kind == BODY_IGNORED) {
                (improvedElement as? ArendExpr ?: improvedElement.parent as? ArendExpr)?.let { expr ->
                    (expr.topmostEquivalentSourceNode.parentSourceNode as? ArendClause)?.let { clause ->
                        return TextRange((clause.fatArrow ?: expr).textRange.startOffset, expr.textRange.endOffset)
                    }
                }
            }

            if (improvedElement is ArendClause) {
                val prev = improvedElement.extendLeft.prevSibling
                val startElement = if (prev is LeafPsiElement && prev.elementType == ArendElementTypes.PIPE) prev else improvedElement
                return TextRange(startElement.textRange.startOffset, improvedElement.textRange.endOffset)
            }

            if ((error as? TypecheckingError)?.kind == TOO_MANY_PATTERNS && improvedElement is ArendPatternImplMixin) {
                var endElement: ArendPatternImplMixin = improvedElement
                while (true) {
                    var next = endElement.extendRight.nextSibling
                    if (next is LeafPsiElement && next.elementType == ArendElementTypes.COMMA) {
                        next = next.extendRight.nextSibling
                    }
                    if (next is ArendPatternImplMixin) {
                        endElement = next
                    } else {
                        break
                    }
                }
                return TextRange(improvedElement.textRange.startOffset, endElement.textRange.endOffset)
            }

            return improvedElement.textRange
        }

        private val GOAL_IN_COPATTERN = withAncestors(ArendLiteral::class.java, ArendAtom::class.java, ArendAtomFieldsAcc::class.java,
            ArendArgumentAppExpr::class.java, ArendNewExpr::class.java, ArendCoClause::class.java)

        fun isEmptyGoal(element: PsiElement): Boolean {
            val goal: ArendGoal? = element.childOfType()
            return goal != null && GOAL_IN_COPATTERN.accepts(goal)
        }
    }
}
