package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.Referable
import org.arend.term.abs.Abstract
import org.arend.psi.ArendFieldTele
import org.arend.psi.ArendNameTele
import org.arend.psi.ArendNameTeleUntyped
import org.arend.psi.ArendTypeTele


abstract class ArendNameTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendNameTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> = identifierOrUnknownList.map { it.defIdentifier }

    override fun getType() = expr

    override fun isStrict() = strictKw != null
}

abstract class ArendNameTeleUntypedImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendNameTeleUntyped {
    override fun getData() = this

    override fun isExplicit() = true

    override fun getReferableList(): List<Referable?> = listOf(defIdentifier)

    override fun getType() = null

    override fun isStrict() = false
}

abstract class ArendTypeTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendTypeTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable?> {
        val list = typedExpr?.identifierOrUnknownList?.map { it.defIdentifier } ?: listOf(null)
        return if (list.isEmpty()) listOf(null) else list
    }

    override fun getType(): Abstract.Expression? = typedExpr?.expr ?: literal ?: universeAtom

    override fun isStrict() = strictKw != null
}

abstract class ArendFieldTeleImplMixin(node: ASTNode): ArendSourceNodeImpl(node), ArendFieldTele {
    override fun getData() = this

    override fun isExplicit() = lbrace == null

    override fun getReferableList(): List<Referable> = fieldDefIdentifierList

    override fun getType() = expr

    override fun isStrict() = false

    override fun isClassifying() = classifyingKw != null
}
