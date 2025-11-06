package org.arend.psi

import org.arend.psi.ext.ArendExpr

interface ArendCodeFragment : IArendFile {
    val expr: ArendExpr?
    fun fragmentResolved()
}