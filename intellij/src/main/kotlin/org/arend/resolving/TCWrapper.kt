package org.arend.resolving

import org.arend.naming.reference.*


abstract class TCReferableWrapper : TCReferable {
    protected abstract val referable: LocatedReferable

    override fun getLocation() = referable.location

    override fun getLocatedReferableParent() = referable.locatedReferableParent

    override fun getPrecedence() = referable.precedence

    override fun getTypecheckable(): TCReferable {
        val tc = referable.typecheckable
        return if (tc !== referable && tc is LocatedReferable) (wrap(tc) ?: this) else this
    }

    override fun getData() = referable.underlyingReferable

    override fun getUnderlyingReferable() = referable

    override fun textRepresentation() = referable.textRepresentation()

    override fun getKind() = referable.kind

    override fun getTypeClassReference() = referable.typeClassReference

    override fun getTypeOf() = referable.typeOf

    override fun equals(other: Any?) = this === other || referable == (other as? TCReferableWrapper)?.referable

    override fun hashCode() = referable.hashCode()

    companion object {
        fun wrap(referable: LocatedReferable?) = when (referable) {
            null -> null
            is TCReferable -> referable
            is FieldReferable -> TCFieldReferableWrapper(referable)
            is ClassReferable -> TCClassReferableWrapper(referable)
            else -> TCReferableWrapperImpl(referable)
        }
    }

}

class TCReferableWrapperImpl(override val referable: LocatedReferable) : TCReferableWrapper()

class TCFieldReferableWrapper(override val referable: FieldReferable) : TCFieldReferable, TCReferableWrapper() {
    override fun isExplicitField() = referable.isExplicitField

    override fun isParameterField() = referable.isParameterField
}

class TCClassReferableWrapper(override val referable: ClassReferable) : TCClassReferable, TCReferableWrapper() {
    override fun getUnresolvedSuperClassReferences(): Collection<Reference> = referable.unresolvedSuperClassReferences

    override fun getSuperClassReferences() = referable.superClassReferences.map { TCClassReferableWrapper(it) }

    override fun getFieldReferables() = referable.fieldReferables.map { TCFieldReferableWrapper(it) }

    override fun getImplementedFields() = referable.implementedFields.map { if (it is FieldReferable) TCFieldReferableWrapper(it) else it }

    override fun isRecord() = referable.isRecord
}

object WrapperReferableConverter : BaseReferableConverter() {
    override fun toDataReferable(referable: Referable?) = referable

    override fun toDataLocatedReferable(referable: LocatedReferable?) = TCReferableWrapper.wrap(referable)
}
