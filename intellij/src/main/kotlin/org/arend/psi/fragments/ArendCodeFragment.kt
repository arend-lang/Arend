package org.arend.psi.fragments

import org.arend.psi.IArendFile
import org.arend.psi.ext.ArendExpr

interface ArendCodeFragment : IArendFile {
    val expr: ArendExpr?
    fun fragmentResolved()
}