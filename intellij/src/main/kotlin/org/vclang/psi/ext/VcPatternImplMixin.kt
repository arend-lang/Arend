package org.vclang.psi.ext

import com.intellij.lang.ASTNode
import com.jetbrains.jetpad.vclang.naming.reference.NamedUnresolvedReference
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import org.vclang.psi.VcAtomPattern
import org.vclang.psi.VcAtomPatternOrPrefix
import org.vclang.psi.VcDefIdentifier

abstract class VcPatternImplMixin(node: ASTNode) : VcSourceNodeImpl(node), Abstract.Pattern {
    override fun getData(): Any? = this

    abstract fun getAtomPattern(): VcAtomPattern?

    abstract fun getDefIdentifier(): VcDefIdentifier?

    open fun getAtomPatternOrPrefixList(): List<VcAtomPatternOrPrefix> = emptyList()

    override fun isUnnamed() = getAtomPattern()?.underscore != null

    override fun isExplicit(): Boolean {
        val atom = getAtomPattern() ?: return true
        if (atom.lbrace != null) {
            return false
        }
        val patterns = atom.patternList
        return if (patterns.size == 1) patterns.first().isExplicit else true
    }

    override fun getHeadReference(): Referable? {
        val conName = getDefIdentifier()
        if (conName != null) {
            return if (getAtomPatternOrPrefixList().isEmpty()) conName else NamedUnresolvedReference(conName, conName.referenceName)
        }

        val patterns = getAtomPattern()?.patternList ?: return null
        return if (patterns.size == 1) patterns.first().headReference else null
    }

    override fun getArguments(): List<Abstract.Pattern> {
        if (getDefIdentifier() != null) return getAtomPatternOrPrefixList()

        val patterns = getAtomPattern()?.patternList ?: return emptyList()
        return if (patterns.size == 1) patterns.first().arguments else patterns
    }
}
