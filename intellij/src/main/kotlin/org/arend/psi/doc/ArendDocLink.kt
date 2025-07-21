package org.arend.psi.doc

import com.intellij.lang.ASTNode
import org.arend.psi.childOfType
import org.arend.psi.childOfTypeStrict
import org.arend.psi.ext.ArendCompositeElementImpl

class ArendDocLink(node: ASTNode) : ArendCompositeElementImpl(node) {
    val docReferenceText: ArendDocReferenceText?
        get() = childOfType()

    val docLinkText: ArendDocLinkText?
        get() = childOfTypeStrict()
}
