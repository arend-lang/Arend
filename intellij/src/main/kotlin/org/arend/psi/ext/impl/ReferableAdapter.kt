package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ArendPrec
import org.arend.psi.ancestor
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiStubbedReferableImpl
import org.arend.psi.stubs.ArendNamedStub
import org.arend.term.Precedence

abstract class ReferableAdapter<StubT> : PsiStubbedReferableImpl<StubT>, PsiLocatedReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    abstract fun getPrec(): ArendPrec?

    override fun getPrecedence() = stub?.precedence ?: calcPrecedence(getPrec())

    override fun getTypecheckable(): PsiLocatedReferable = ancestor<ArendDefinition>() ?: this

    override fun getLocation() = (containingFile as? ArendFile)?.modulePath

    override fun getLocatedReferableParent() = parent.ancestor<PsiLocatedReferable>()

    companion object {
        fun calcPrecedence(prec: ArendPrec?): Precedence {
            if (prec == null) return Precedence.DEFAULT
            val assoc = when {
                prec.rightAssocKw != null || prec.infixRightKw != null -> Precedence.Associativity.RIGHT_ASSOC
                prec.leftAssocKw != null || prec.infixLeftKw != null -> Precedence.Associativity.LEFT_ASSOC
                prec.nonAssocKw != null || prec.infixNonKw != null -> Precedence.Associativity.NON_ASSOC
                else -> return Precedence.DEFAULT
            }
            val priority = prec.number
            return Precedence(assoc, if (priority == null) Precedence.MAX_PRIORITY else priority.text?.toByteOrNull() ?: (Precedence.MAX_PRIORITY + 1).toByte(), prec.infixRightKw != null || prec.infixLeftKw != null || prec.infixNonKw != null)
        }
    }
}
