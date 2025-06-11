package org.arend.intention.generating

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.elementType
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import org.arend.error.DummyErrorReporter
import org.arend.ext.error.MissingClausesError
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.psi.ArendElementTypes
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.quickfix.ImplementMissingClausesQuickFix
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.typechecking.computation.UnstoppableCancellationIndicator

internal fun checkMissingClauses(element: PsiElement): Boolean {
    return element.elementType == ArendElementTypes.TGOAL
}

internal fun deleteFunctionBody(element: PsiElement): Pair<ArendGroup, Int>? {
    var parent: PsiElement? = element
    while (parent !is ArendFunctionBody) {
        parent = parent?.parent
        if (parent == null) {
            return null
        }
    }

    val group = parent.parent as? ArendGroup? ?: return null
    val startOffsetParent = parent.startOffset
    runWriteAction {
        parent.delete()
    }
    return Pair(group, startOffsetParent)
}

internal fun fixMissingClausesError(project: Project, file: ArendFile, editor: Editor, group: ArendGroup, offset: Int) {
    val server = project.service<ArendServerService>().server
    file.moduleLocation?.let { server.getCheckerFor(listOf(it)).typecheck(listOf(group.fullName), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty()) }

    val error = server.errorMap.values.flatten().filter { it is MissingClausesError }.find {
        (((it as MissingClausesError).definition as? LocatedReferableImpl)?.data as? PsiElement)?.endOffset == offset
    } ?: return
    runWriteAction {
        ImplementMissingClausesQuickFix(
            error as MissingClausesError,
            SmartPointerManager.createPointer((error.definition as LocatedReferableImpl).data as ArendCompositeElement)
        ).invoke(project, editor, file)
    }
}
