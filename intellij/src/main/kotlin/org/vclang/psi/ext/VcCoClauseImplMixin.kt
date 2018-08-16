package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.ClassReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.reference.TypedReferable
import org.vclang.psi.VcCoClause
import org.vclang.psi.VcExpr
import org.vclang.psi.VcNameTele


abstract class VcCoClauseImplMixin(node: ASTNode) : VcSourceNodeImpl(node), VcCoClause {
    override fun getData() = this

    override fun getImplementedField() = longName?.referent

    fun getResolvedImplementedField() = longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? Referable

    override fun getParameters(): List<VcNameTele> = nameTeleList

    override fun getImplementation(): VcExpr? = expr

    override fun getClassFieldImpls(): List<VcCoClause> = coClauseList

    override fun getClassReference(): ClassReferable? {
        val resolved = getResolvedImplementedField()
        return resolved as? ClassReferable ?: (resolved as? TypedReferable)?.typeClassReference
    }

    override fun getNumberOfArguments() = 0
}