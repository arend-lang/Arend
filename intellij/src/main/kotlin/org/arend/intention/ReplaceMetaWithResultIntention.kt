package org.arend.intention

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.expr.ErrorWithConcreteExpression
import org.arend.extImpl.definitionRenamer.CachingDefinitionRenamer
import org.arend.psi.*
import org.arend.psi.ext.impl.MetaAdapter
import org.arend.refactoring.*
import org.arend.term.prettyprint.DefinitionRenamerConcreteVisitor

class ReplaceMetaWithResultIntention : BaseArendIntention("Replace meta with result") {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement): Boolean {
        val expr = element.ancestor<ArendExpr>()
        val refElement = (expr as? ArendLiteral)?.ipName ?: ((expr as? ArendLiteral)?.longName ?: (expr as? ArendLongNameExpr)?.longName)?.refIdentifierList?.lastOrNull() ?: return false
        return (refElement.resolve as? MetaAdapter)?.metaRef.let { it?.definition != null || it?.resolver != null }
    }

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val expr = element.ancestor<ArendExpr>() ?: return
        val (subCore, subConcrete, _) = tryCorrespondedSubExpr(expr.textRange, expr.containingFile as? ArendFile ?: return, project, editor ?: return) ?: return
        val definitionRenamer = PsiLocatedRenamer(expr)
        val cExpr = runReadAction {
            if (subCore is ErrorWithConcreteExpression) {
                subCore.expression.accept(DefinitionRenamerConcreteVisitor(CachingDefinitionRenamer(definitionRenamer)), null)
            } else {
                exprToConcrete(project, subCore, null, CachingDefinitionRenamer(definitionRenamer))
            }
        }

        val text = cExpr.toString()
        WriteCommandAction.writeCommandAction(project).run<Exception> {
            replaceExprSmart(editor.document, expr, subConcrete, rangeOfConcrete(subConcrete), null, cExpr, text).length
            definitionRenamer.writeAllImportCommands()
        }
    }
}