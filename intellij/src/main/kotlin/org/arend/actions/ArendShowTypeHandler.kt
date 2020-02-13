package org.arend.actions

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.arend.core.definition.FunctionDefinition
import org.arend.core.expr.Expression
import org.arend.error.DummyErrorReporter
import org.arend.naming.BinOpParser
import org.arend.naming.reference.converter.IdReferableConverter
import org.arend.psi.*
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.visitor.CorrespondedSubExprVisitor
import org.arend.util.BinOpExpansionVisitor


class ArendShowTypeHandler(private val requestFocus: Boolean) : CodeInsightActionHandler {
    override fun startInWriteAction() = false

    private inline fun displayHint(crossinline f: HintManager.() -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            HintManager.getInstance().apply { setRequestFocusForNextHint(requestFocus) }.f()
        }
    }

    fun doInvoke(project: Project, editor: Editor, file: PsiFile): String? {
        val range = EditorUtil.getSelectionInAnyMode(editor)
        val possibleParent = (if (range.isEmpty)
            file.findElementAt(range.startOffset)
        else PsiTreeUtil.findCommonParent(
                file.findElementAt(range.startOffset),
                file.findElementAt(range.endOffset - 1)
        )) ?: return "selected expr in bad position"
        // if (possibleParent is PsiWhiteSpace) return "selected text are whitespaces"
        val parent = possibleParent.ancestor<ArendExpr>()?.parent
                ?: return "selected text is not an arend expression"
        val psiDef = parent.ancestor<ArendDefinition>()
                ?: return "selected text is not in a definition"
        val service = project.service<TypeCheckingService>()
        // Only work for functions right now
        val concreteDef = PsiConcreteProvider(
                project,
                service.newReferableConverter(false),
                DummyErrorReporter.INSTANCE,
                null
        ).getConcrete(psiDef)
                as? Concrete.FunctionDefinition
                ?: return "selected text is not in a function definition"
        val def = service.getTypechecked(psiDef) as FunctionDefinition

        val children = collectArendExprs(parent, range)
                .map { ConcreteBuilder.convertExpression(IdReferableConverter.INSTANCE, it) }
                .map(Concrete::BinOpSequenceElem)
                .toList()
                .takeIf { it.isNotEmpty() }
                ?: return "cannot find a suitable subexpression"
        val parser = BinOpParser(DummyErrorReporter.INSTANCE)
        val body = def.actualBody as? Expression
                ?: return "function body is not an expression"
        // Only work for single clause right now
        val concreteBody = concreteDef.body.term
                ?: return "does not yet support multiple clauses"

        val subExpr = if (children.size == 1)
            children.first().expression
        else parser.parse(Concrete.BinOpSequenceExpression(null, children))
        val subExprVisitor = CorrespondedSubExprVisitor(subExpr.accept(BinOpExpansionVisitor, parser))
        val visited = concreteBody
                .accept(BinOpExpansionVisitor, parser)
                .accept(subExprVisitor, body)
                ?: return "cannot find a suitable subexpression"
        val subCore = visited.proj1
        val textRange = rangeOf(visited.proj2)
        editor.selectionModel.setSelection(textRange.startOffset, textRange.endOffset)
        displayHint { showInformationHint(editor, subCore.type.toString()) }
        return null
    }

    private tailrec fun lastAppArg(concrete: Concrete.Expression): Concrete.Expression =
        if (concrete is Concrete.AppExpression)
            lastAppArg(concrete.arguments.last().expression)
        else concrete

    private fun rangeOf(subConcrete: Concrete.Expression): TextRange {
        if (subConcrete is Concrete.AppExpression) {
            val function = subConcrete.data as PsiElement
            val lastArg = lastAppArg(subConcrete).data as PsiElement
            val e = PsiTreeUtil.findCommonParent(function, lastArg) ?: return function.textRange
            val left = e.childrenWithLeaves.first { PsiTreeUtil.isAncestor(it, function, false) }
            val right = e.childrenWithLeaves.first { PsiTreeUtil.isAncestor(it, lastArg, false) }
            return TextRange.create(
                minOf(left.textRange.startOffset, right.textRange.startOffset),
                maxOf(left.textRange.endOffset, right.textRange.endOffset)
            )
        }
        return (subConcrete.data as PsiElement).textRange
    }

    private fun collectArendExprs(parent: PsiElement, range: TextRange): List<ArendExpr> {
        val exprs = parent.childrenWithLeaves
                .dropWhile { it.textRange.endOffset < range.startOffset }
                .takeWhile { it.textRange.startOffset < range.endOffset }
                .toList()
        if (exprs.isEmpty()) return emptyList()
        if (exprs.size == 1) {
            val first = exprs.first()
            val subCollected = collectArendExprs(first, range)
            return when {
                subCollected.isNotEmpty() -> subCollected
                first is ArendExpr -> listOf(first)
                else -> emptyList()
            }
        } else return exprs.asSequence().mapNotNull {
            it.linearDescendants.filterIsInstance<ArendExpr>().firstOrNull()
        }.toList()
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val s = doInvoke(project, editor, file)
        if (s != null) displayHint { showErrorHint(editor, "Failed to obtain type because $s") }
    }
}
