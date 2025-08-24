package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.psi.childOfTypeStrict
import org.arend.psi.firstRelevantChild
import org.arend.term.abs.Abstract

class ArendScId(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.NameHiding {
    val refIdentifier: ArendRefIdentifier
        get() = childOfTypeStrict()

    override fun getScopeContext() = ArendStatCmd.getScopeContext(firstRelevantChild)

    override fun getHiddenReference(): NamedUnresolvedReference =
        refIdentifier.referent
}