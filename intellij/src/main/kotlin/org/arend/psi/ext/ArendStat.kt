package org.arend.psi.ext

import com.intellij.lang.ASTNode
import org.arend.psi.*

class ArendStat(node: ASTNode) : ArendSourceNodeImpl(node), ArendStatement {
    val statCmd: ArendStatCmd?
        get() = childOfType()

    val statAccessMod: ArendStatAccessMod?
        get() = childOfType()

    override fun getGroup(): ArendGroup? = childOfType()

    override fun getNamespaceCommand(): ArendStatCmd? = childOfType()

    companion object {
        fun flatStatements(l: List<ArendStat>?): List<ArendStat> = l?.flatMap {
            val accessMod = it.statAccessMod
            if (accessMod == null) listOf(it) else flatStatements(accessMod.statList)
        } ?: emptyList()
    }
}