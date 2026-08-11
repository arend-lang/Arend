package org.arend.psi.stubs

import org.arend.ext.reference.Precedence
import org.arend.term.group.AccessModifier

interface ArendNamedStub {
    val name: String?
    val precedence: Precedence?
    val accessModifier: AccessModifier
}
