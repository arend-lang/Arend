package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.util.elementType
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.psi.ArendElementTypes
import org.arend.psi.childOfTypeStrict
import org.arend.psi.firstRelevantChild
import org.arend.term.abs.Abstract

class ArendScId(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.NameHiding {
    val refIdentifier: ArendRefIdentifier
        get() = childOfTypeStrict()

    override fun isStatic() = firstRelevantChild?.elementType != ArendElementTypes.DOT

    override fun getHiddenReference(): NamedUnresolvedReference =
        refIdentifier.referent
}