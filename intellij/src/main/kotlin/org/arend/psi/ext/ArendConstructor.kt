package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.psi.*
import org.arend.psi.stubs.ArendConstructorStub
import org.arend.resolving.DataLocatedReferable
import org.arend.term.abs.Abstract
import org.arend.resolving.util.ParameterImpl
import org.arend.resolving.util.ReferenceImpl
import org.arend.resolving.util.getTypeOf
import org.arend.term.group.AccessModifier
import javax.swing.Icon

class ArendConstructor : ReferableBase<ArendConstructorStub>, ArendInternalReferable, Abstract.Constructor, Abstract.ConstructorClause, StubBasedPsiElement<ArendConstructorStub> {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendConstructorStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    val elim: ArendElim?
        get() = childOfType()

    override fun getKind() = GlobalReferable.Kind.CONSTRUCTOR

    override fun getData() = this

    override fun getPatterns(): List<Abstract.Pattern> = emptyList()

    override fun getConstructors(): List<ArendConstructor> = listOf(this)

    override fun getReferable() = this

    override fun getParameters(): List<ArendTypeTele> = getChildrenOfType()

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = getChildrenOfType()

    override fun isVisible(): Boolean = true

    override fun getResultType(): ArendExpr? = childOfType()

    override fun isCoerce() = hasChildOfType(ArendElementTypes.COERCE_KW)

    override fun getAccessModifier(): AccessModifier =
        (childOfType<ArendAccessMod>()?.accessModifier ?: AccessModifier.PUBLIC).max(dataAccessModifier)

    private val allParameters
        get() = (ancestor<ArendDefData>()?.allParameters?.map { ParameterImpl(false, it.referableList, it.type) } ?: emptyList()) + parameters

    override val typeOf: Abstract.Expression?
        get() = getTypeOf(allParameters, ancestor<ArendDefData>()?.let { ReferenceImpl(it) })

    override fun getIcon(flags: Int): Icon = ArendIcons.CONSTRUCTOR

    override val psiElementType: ArendDefIdentifier?
        get() = ancestor<ArendDefData>()?.defIdentifier

    private val dataAccessModifier: AccessModifier
        get() = ancestor<ArendDefData>()?.accessModifier ?: AccessModifier.PUBLIC

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?) =
        DataLocatedReferable(data, accessModifier, this, parent)
}
