package org.arend.refactoring.changeSignature

import org.arend.naming.reference.Referable
import org.arend.psi.ArendPsiFactory
import org.arend.term.abs.AbstractReferable

class ArendChangeSignatureDialogParameter(val item: ArendChangeSignatureDialogParameterTableModelItem): Referable {
    override fun textRepresentation(): String = item.parameter.name

    override fun getAbstractReferable(): AbstractReferable =
        (ArendPsiFactory(item.typeCodeFragment.project, "Change Signature Dialog")).createDefIdentifier(textRepresentation())

}