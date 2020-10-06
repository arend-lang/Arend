package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.*
import org.arend.naming.resolving.visitor.TypeClassReferenceExtractVisitor
import org.arend.naming.scope.LazyScope
import org.arend.psi.*
import org.arend.psi.ext.ArendFunctionalBody
import org.arend.psi.ext.ArendFunctionalDefinition
import org.arend.psi.stubs.ArendDefFunctionStub
import org.arend.term.FunctionKind
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.resolving.util.ParameterImpl
import org.arend.resolving.util.ReferableExtractVisitor
import org.arend.resolving.util.getTypeOf
import javax.swing.Icon

abstract class FunctionDefinitionAdapter : DefinitionAdapter<ArendDefFunctionStub>, ArendDefFunction, ArendFunctionalDefinition {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefFunctionStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getParameters(): List<ArendNameTele> = nameTeleList

    override fun getResultType(): ArendExpr? = returnExpr?.let { it.exprList.firstOrNull() ?: it.atomFieldsAccList.firstOrNull() }

    override val body: ArendFunctionalBody? get() = functionBody

    override fun getResultTypeLevel(): ArendExpr? = returnExpr?.let { it.exprList.getOrNull(1) ?: it.atomFieldsAccList.getOrNull(1) }

    override fun getTerm(): ArendExpr? = functionBody?.expr

    override fun getEliminatedExpressions(): List<ArendRefIdentifier> = functionBody?.elim?.refIdentifierList ?: emptyList()

    override fun getClauses(): List<ArendClause> = functionBody?.functionClauses?.clauseList ?: emptyList()

    override fun getUsedDefinitions(): List<LocatedReferable> = where?.statementList?.mapNotNull {
        val def = it.definition
        if ((def as? ArendDefFunction)?.functionKw?.useKw != null) def else null
    } ?: emptyList()

    override fun getSubgroups(): List<ArendGroup> = (functionBody?.coClauseList?.mapNotNull { it.coClauseDef } ?: emptyList()) + super.getSubgroups()

    override fun withTerm() = functionBody?.fatArrow != null

    override fun isCowith() = functionBody?.cowithKw != null

    override fun getFunctionKind() = functionKw.let {
        when {
            it.lemmaKw != null -> FunctionKind.LEMMA
            it.sfuncKw != null -> FunctionKind.SFUNC
            it.levelKw != null -> FunctionKind.LEVEL
            it.coerceKw != null -> FunctionKind.COERCE
            else -> FunctionKind.FUNC
        }
    }

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitFunction(this)

    override fun getIcon(flags: Int): Icon = ArendIcons.FUNCTION_DEFINITION

    override fun getTypeClassReference(): ClassReferable? {
        val type = resultType ?: return null
        return if (parameters.all { !it.isExplicit }) ReferableExtractVisitor().findClassReferable(type) else null
    }

    override fun getBodyReference(visitor: TypeClassReferenceExtractVisitor): Referable? {
        val expr = functionBody?.expr ?: return null
        return ReferableExtractVisitor(requiredAdditionalInfo = false, isExpr = true).findReferable(expr)
    }

    private val allParameters
        get() = if (enclosingClass == null) parameters else listOf(ParameterImpl(false, listOf(null), null)) + parameters

    override val typeOf: Abstract.Expression?
        get() = getTypeOf(allParameters, resultType)

    override fun getClassReference(): ClassReferable? {
        val type = resultType ?: return null
        val visitor = ReferableExtractVisitor()
        return if (isCowith) visitor.findClassReference(visitor.findReferableInType(type), LazyScope { type.scope }) else visitor.findClassReferable(type)
    }

    override fun getClassReferenceData(onlyClassRef: Boolean): ClassReferenceData? {
        val type = resultType ?: return null
        val visitor = ReferableExtractVisitor(true)
        val classRef = (if (isCowith) visitor.findReferableInType(type) as? ClassReferable else visitor.findClassReferable(type)) ?: return null
        return ClassReferenceData(classRef, visitor.argumentsExplicitness, visitor.implementedFields, true)
    }

    override fun getCoClauseElements(): List<ArendCoClause> = functionBody?.coClauseList ?: emptyList()

    override fun getImplementedField(): Abstract.Reference? = null

    override fun getKind() = GlobalReferable.Kind.FUNCTION

    override val psiElementType: PsiElement?
        get() = resultType

    override val tcReferable: TCDefReferable?
        get() = super.tcReferable as TCDefReferable?
}
