package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ArendElementTypes.*
import org.arend.term.abs.Abstract

class ArendStatCmd(node: ASTNode) : ArendSourceNodeImpl(node), Abstract.NamespaceCommand {
    val longName: ArendLongName?
        get() = childOfType()

    val nsUsing: ArendNsUsing?
        get() = childOfType()

    val lparen: PsiElement?
        get() = findChildByType(LPAREN)

    val rparen: PsiElement?
        get() = findChildByType(RPAREN)

    val hidingKw: PsiElement?
        get() = findChildByType(HIDING_KW)

    val importKw: PsiElement?
        get() = findChildByType(IMPORT_KW)

    val openKw: PsiElement?
        get() = findChildByType(OPEN_KW)

    override fun isImport() = importKw != null

    override fun getModuleReference() = longName?.referent

    override fun isUsing(): Boolean {
        val using = nsUsing
        return using == null || using.usingKw != null
    }

    override fun getRenamings(): List<ArendNsId> = nsUsing?.nsIdList ?: emptyList()

    override fun getHidings(): List<ArendScId> = getChildrenOfType()

    override fun getOpenedReference() = longName

    companion object {
        fun getScopeContext(element: PsiElement?): Scope.ScopeContext = when (element?.elementType) {
            DOT -> Scope.ScopeContext.DYNAMIC
            PLEVEL_KW -> Scope.ScopeContext.PLEVEL
            HLEVEL_KW -> Scope.ScopeContext.HLEVEL
            else -> Scope.ScopeContext.STATIC
        }
    }
}
