package org.arend.quickfix.instance

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodHelper.addSiblingAfter
import org.arend.ext.reference.DataContainer
import org.arend.ext.variable.VariableImpl
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.scope.EmptyScope
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendDefClass
import org.arend.server.ArendServerService
import org.arend.typechecking.error.local.inference.RecursiveInstanceInferenceError
import org.arend.util.ArendBundle
import java.util.*

class AddRecursiveInstanceArgumentQuickFix(private val error: RecursiveInstanceInferenceError, private val cause: SmartPsiElementPointer<ArendCompositeElement>) : IntentionAction {
    override fun startInWriteAction(): Boolean = true

    override fun getFamilyName(): String = text

    override fun getText(): String = ArendBundle.message("arend.instance.addLocalRecursiveInstance")

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = cause.element != null &&
            ((error.definition as? DataContainer?)?.data as? PsiElement)?.originalElement is ArendDefClass

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val defClass = ((error.definition as? DataContainer)?.data as? PsiElement)?.originalElement as? ArendDefClass ?: return

        val classRefName = error.classRef.refName

        val server = project.service<ArendServerService>().server
        val referable = defClass.tcReferable
        val scope = referable?.let { server.getReferableScope(it) } ?: EmptyScope.INSTANCE
        val referablesInScope = scope.getElements(null).map { VariableImpl(it.textRepresentation()) }
        val fieldName = StringRenamer().generateFreshName({ classRefName.lowercase(Locale.getDefault()) }, referablesInScope)

        val psiFactory = ArendPsiFactory(project)
        val fieldTele = psiFactory.createFieldTele(fieldName, classRefName, false)

        defClass.defIdentifier?.addSiblingAfter(fieldTele)
        defClass.defIdentifier?.addSiblingAfter(psiFactory.createWhitespace(" "))
    }
}
