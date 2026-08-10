package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict
import org.arend.psi.getChildrenOfType
import org.arend.term.abs.AbstractExpressionVisitor


class ArendAtomFieldsAcc(node: ASTNode) : ArendExpr(node) {
    val atom: ArendAtom
        get() = childOfTypeStrict()

    val fieldAccList: List<ArendFieldAcc>
        get() = getChildrenOfType()

    val ipName: ArendIPName?
        get() = childOfType()

    override fun <P, R> accept(visitor: AbstractExpressionVisitor<in P, out R>, params: P?): R {
        val fieldAccs = fieldAccList
        val ipName = ipName
        return if (fieldAccs.isEmpty() && ipName == null) {
            atom.accept(visitor, params)
        } else {
            visitor.visitFieldAccs(this, atom, fieldAccs, ipName, ipName?.referenceName, ipName?.fixity, params)
        }
    }

    val isVariable: Boolean
        get() {
            for (fieldAcc in fieldAccList) {
                if (fieldAcc.refIdentifier == null) {
                    return false
                }
            }

            val literal = atom.literal ?: return false
            return literal.refIdentifier != null || literal.ipName != null
        }
}