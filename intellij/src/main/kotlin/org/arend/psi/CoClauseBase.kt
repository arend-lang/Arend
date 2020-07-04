package org.arend.psi

import com.intellij.psi.PsiElement
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TypedReferable
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiReferable
import org.arend.term.abs.Abstract
import org.arend.typing.ReferableExtractVisitor


interface CoClauseBase : ClassReferenceHolder, Abstract.ClassFieldImpl, ArendCompositeElement {
    val localCoClauseList: List<ArendLocalCoClause>

    val lbrace: PsiElement?

    val longName: ArendLongName?

    val nameTeleList: List<ArendNameTele>

    val resolvedImplementedField: Referable?

    val fatArrow: PsiElement?

    val expr: ArendExpr?

    companion object {
        fun getClassReference(coClauseBase: CoClauseBase): ClassReferable? {
            val resolved = coClauseBase.resolvedImplementedField
            return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
        }

        fun getClassReferenceData(coClauseBase: CoClauseBase): ClassReferenceData? {
            val resolved = coClauseBase.resolvedImplementedField
            if (resolved is ClassReferable) {
                return ClassReferenceData(resolved, emptyList(), emptySet(), false)
            }

            val visitor = ReferableExtractVisitor(true)
            val classRef = visitor.findReferable((resolved as? PsiReferable)?.typeOf) as? ClassReferable ?: return null
            return ClassReferenceData(classRef, visitor.argumentsExplicitness, visitor.implementedFields, true)
        }
    }
}