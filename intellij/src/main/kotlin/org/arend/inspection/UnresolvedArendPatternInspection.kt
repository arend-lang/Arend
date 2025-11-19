package org.arend.inspection

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.stubs.StubIndex
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.TypeConstructorExpression
import org.arend.intention.SplitAtomPatternIntention
import org.arend.psi.ArendFile
import org.arend.psi.ArendFileScope
import org.arend.psi.ext.*
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.refactoring.isPrelude
import org.arend.util.ArendBundle

class UnresolvedArendPatternInspection : ArendInspectionBase() {
    override fun buildArendVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {

            fun registerProblem(element: PsiElement) {
                val message = ArendBundle.message("arend.inspection.unresolved.pattern", element.text)
                holder.registerProblem(element, message)
            }

            override fun visitElement(element: PsiElement) {
                super.visitElement(element)
                if (element !is ArendPattern) {
                    return
                }

                val identifier = element.singleReferable ?: return
                val name = identifier.refName
                val project = element.project
                val constructors = StubIndex.getElements(ArendDefinitionIndex.KEY, name, project, ArendFileScope(project), PsiReferable::class.java).filterIsInstance<ArendConstructor>()
                val resolve = identifier.reference?.resolve() ?: return

                if (constructors.isNotEmpty() && (!constructors.contains(resolve) &&
                    (resolve.containingFile as? ArendFile?)?.let { isPrelude(it) } == false)) {
                    val arendDefinition = ((SplitAtomPatternIntention.getElementType(element, element.project)?.first)
                        ?.let { TypeConstructorExpression.unfoldType(it) } as? DefCallExpression)
                        ?.definition?.referable?.data as? ArendDefinition<*>
                    val groupConstructors = arendDefinition?.internalReferables ?: emptyList()
                    if (groupConstructors.any { constructors.contains(it) }) {
                        registerProblem(element)
                    }
                }
            }
        }
    }
}
