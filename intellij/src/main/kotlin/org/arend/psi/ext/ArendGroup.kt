package org.arend.psi.ext

import org.arend.term.abs.Abstract

interface ArendStatement : ArendCompositeElement, Abstract.Statement {
    override fun getGroup(): ArendGroup?
    override fun getNamespaceCommand(): ArendStatCmd?
}

interface ArendGroup: PsiLocatedReferable, ArendSourceNode, Abstract.Group {
    val where: ArendWhere?

    val parentGroup: ArendGroup?

    val internalReferables: List<ReferableBase<*>>

    override fun getStatements(): List<ArendStatement>

    override fun getDynamicSubgroups(): List<ArendGroup>
}

fun ArendGroup.traverse(function: (ArendGroup) -> Unit) {
    function(this)
    for (statement in statements) {
        statement.group?.traverse(function)
    }
    for (group in dynamicSubgroups) {
        group.traverse(function)
    }
}