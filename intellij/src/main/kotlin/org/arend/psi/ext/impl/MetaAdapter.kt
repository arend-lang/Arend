package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.MetaReferable
import org.arend.psi.ArendDefMeta
import org.arend.psi.ArendNameTeleUntyped
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.stubs.ArendDefMetaStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import java.util.function.Supplier


abstract class MetaAdapter : DefinitionAdapter<ArendDefMetaStub>, ArendDefMeta, Abstract.MetaDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefMetaStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    var metaRef: MetaReferable?
        get() = tcReferableCache as MetaReferable?
        set(value) {
            tcReferableCache = value
        }

    private fun prepareTCRef(parent: LocatedReferable?) =
        MetaReferable(precedence, refName, aliasPrecedence, aliasName, documentation?.toString() ?: "", null, null, parent)

    override fun makeTCReferable(data: SmartPsiElementPointer<PsiLocatedReferable>, parent: LocatedReferable?) =
        prepareTCRef(parent).apply { underlyingReferable = Supplier { runReadAction { data.element } } }

    fun makeTCReferable(parent: LocatedReferable?) =
        prepareTCRef(parent).apply { underlyingReferable = Supplier { this } }

    override fun getKind() = GlobalReferable.Kind.OTHER

    override fun getIcon(flags: Int) = ArendIcons.META_DEFINITION

    override fun getTerm(): Abstract.Expression? = expr

    override fun getParameters(): MutableList<ArendNameTeleUntyped> = nameTeleUntypedList

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R =
        visitor.visitMeta(this)
}