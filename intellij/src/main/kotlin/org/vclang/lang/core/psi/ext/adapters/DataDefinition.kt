package org.vclang.lang.core.psi.ext.adapters

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.term.Abstract
import com.jetbrains.jetpad.vclang.term.AbstractDefinitionVisitor
import org.vclang.lang.core.Surrogate
import org.vclang.lang.core.psi.VcDefData
import org.vclang.lang.core.resolve.Namespace
import org.vclang.lang.core.resolve.NamespaceProvider

abstract class DataDefinitionAdapter(node: ASTNode) : DefinitionAdapter(node),
                                                      VcDefData {
    private var parameters: List<Surrogate.TypeParameter>? = null
    private var eliminatedReferences: List<Surrogate.ReferenceExpression>? = null
    private var constructorClauses: MutableList<Surrogate.ConstructorClause>? = null
    private var universe: Surrogate.UniverseExpression? = null

    override val namespace: Namespace
        get() = NamespaceProvider.forDefinition(this)

    fun reconstruct(
            position: Surrogate.Position?,
            name: String?,
            precedence: Abstract.Precedence?,
            parameters: List<Surrogate.TypeParameter>?,
            eliminatedReferences: List<Surrogate.ReferenceExpression>?,
            universe: Surrogate.UniverseExpression?,
            constructorClauses: MutableList<Surrogate.ConstructorClause>?
    ): DataDefinitionAdapter {
        super.reconstruct(position, name, precedence)
        this.parameters = parameters
        this.eliminatedReferences = eliminatedReferences
        this.constructorClauses = constructorClauses
        this.universe = universe
        return this
    }

    override fun getParameters(): List<Surrogate.TypeParameter> =
            parameters ?: throw IllegalStateException()

    override fun getEliminatedReferences(): List<Surrogate.ReferenceExpression>? =
            eliminatedReferences

    override fun getConstructorClauses(): MutableList<Surrogate.ConstructorClause> =
            constructorClauses ?: throw IllegalStateException()

    override fun isTruncated(): Boolean = truncatedKw != null

    override fun getUniverse(): Surrogate.UniverseExpression? = universe

    override fun <P, R> accept(visitor: AbstractDefinitionVisitor<in P, out R>, params: P): R =
            visitor.visitData(this, params)
}
