package org.arend.tracer

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.psi.SmartPointerManager
import org.arend.injection.InjectedArendEditor
import org.arend.toolWindow.errors.tree.ArendErrorTreeElement
import org.arend.typechecking.error.ArendError
import org.arend.util.ArendBundle

class ArendTraceContextView(project: Project) : InjectedArendEditor(project, "Arend Trace Context", null) {
    fun update(traceEntry: ArendTraceEntry) {
        val psiElement = traceEntry.psiElement
        if (psiElement == null) {
            runWriteAction {
                editor?.document?.setText(ArendBundle.message("arend.tracer.no.information"))
            }
            return
        }
        treeElement = ArendErrorTreeElement(ArendError(traceEntry.goalDataHolder, SmartPointerManager.createPointer(psiElement)))
        updateErrorText()
    }
}