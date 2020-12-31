package org.arend.psi.ext.impl

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.arend.ArendIcons
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.stubs.ArendDefClassStub
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractDefinitionVisitor
import org.arend.resolving.util.Universe
import javax.swing.Icon

abstract class ClassDefinitionAdapter : DefinitionAdapter<ArendDefClassStub>, ArendDefClass, Abstract.ClassDefinition, ClassReferenceHolder {
    constructor(node: ASTNode) : super(node)

    constructor(stub: ArendDefClassStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getReferable() = this

    override fun isRecord(): Boolean = recordKw != null

    override fun withoutClassifying(): Boolean = noClassifyingKw != null

    override fun getSuperClassReferences(): List<ClassReferable> = longNameList.mapNotNull { it.refIdentifierList.lastOrNull()?.reference?.resolve() as? ClassReferable }

    override fun getDynamicSubgroups(): List<ArendGroup> = classStatList.mapNotNull { it.definition ?: it.coClause?.coClauseDef ?: it.defModule }

    private inline val parameterFields: List<ArendFieldDefIdentifier> get() = fieldTeleList.flatMap { it.fieldDefIdentifierList }

    override fun getInternalReferables(): List<ArendInternalReferable> =
        (parameterFields as List<ArendInternalReferable> ) +
            classStatList.mapNotNull { it.classField } +
            classFieldList

    override fun getFields() = internalReferables

    override fun getFieldReferables(): List<FieldReferable> =
        (parameterFields as List<FieldReferable>) +
            classStatList.mapNotNull { it.classField } +
            classFieldList

    override fun getImplementedFields(): List<LocatedReferable> =
        coClauseElements.mapNotNull { it.longName.refIdentifierList.lastOrNull()?.reference?.resolve() as? LocatedReferable }

    override fun getParameters(): List<Abstract.FieldParameter> = fieldTeleList

    override fun getSuperClasses(): List<ArendLongName> = longNameList

    override fun getClassElements(): List<Abstract.ClassElement> = children.mapNotNull {
        when (it) {
            is ArendClassField -> it
            is ArendClassImplement -> it
            is ArendClassStat -> it.classField ?: it.classImplement ?: it.overriddenField ?: it.coClause
            else -> null
        }
    }

    override fun getCoClauseElements(): List<ArendClassImplement> = classStatList.mapNotNull { it.classImplement } + classImplementList

    override fun getClassReference() = this

    override fun getClassReferenceData(onlyClassRef: Boolean): ClassReferenceData? =
        ClassReferenceData(this, emptyList(), emptySet(), false)

    override fun getUsedDefinitions(): List<LocatedReferable> =
        (dynamicSubgroups + subgroups).mapNotNull { if ((it as? ArendDefFunction)?.functionKw?.useKw != null) it else null }

    override val typeOf: Abstract.Expression
        get() = Universe

    override fun getKind() = GlobalReferable.Kind.CLASS

    override fun <R : Any?> accept(visitor: AbstractDefinitionVisitor<out R>): R = visitor.visitClass(this)

    override fun getIcon(flags: Int): Icon = if (recordKw != null) ArendIcons.RECORD_DEFINITION else ArendIcons.CLASS_DEFINITION

    override val tcReferable: TCDefReferable?
        get() = super.tcReferable as TCDefReferable?
}
