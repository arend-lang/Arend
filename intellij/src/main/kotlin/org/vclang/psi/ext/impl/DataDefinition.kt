package org.vclang.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.abs.AbstractDefinitionVisitor
import org.vclang.VcIcons
import org.vclang.psi.*
import org.vclang.psi.stubs.VcDefDataStub
import javax.swing.Icon

abstract class DataDefinitionAdapter : DefinitionAdapter<VcDefDataStub>, VcDefData, Abstract.DataDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: VcDefDataStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getConstructors(): List<VcConstructor> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList.flatMap { it.constructorList } + body.constructorList
    }

    override fun getParameters(): List<VcTypeTele> = typeTeleList

    override fun getEliminatedExpressions(): List<VcRefIdentifier>? = dataBody?.elim?.refIdentifierList

    override fun isTruncated(): Boolean = truncatedKw != null

    override fun getUniverse(): VcExpr? = universeExpr

    override fun getClauses(): List<Abstract.ConstructorClause> {
        val body = dataBody ?: return emptyList()
        return body.constructorClauseList + body.constructorList
    }

    override fun getPrecedence(): Precedence = calcPrecedence(prec)

    override fun getCoercingFunctions(): List<LocatedReferable> = where?.statementList?.mapNotNull {
        val def = it.definition
        if (def is VcDefFunction && def.coerceKw != null) def else null
    } ?: emptyList()

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitData(this)

    override fun getIcon(flags: Int): Icon = VcIcons.DATA_DEFINITION

    override val psiElementType: PsiElement?
        get() = universe
}
