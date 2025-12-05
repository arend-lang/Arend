package org.arend.psi.fragments

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiBuilderFactory
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiCodeFragmentImpl
import com.intellij.psi.impl.source.tree.ICodeFragmentElementType
import com.intellij.psi.tree.IElementType
import org.arend.ArendLanguage
import org.arend.IArendFile
import org.arend.naming.scope.Scope
import org.arend.parser.ArendParser
import org.arend.psi.ArendElementTypes
import org.arend.psi.ext.ArendExpr
import org.arend.resolving.ArendReference
import org.arend.server.impl.SingleFileReferenceResolver

class ArendExpressionCodeFragment(project: Project, expression: String,
                                  context: PsiElement?,
                                  private val fragmentController: ArendCodeFragmentController?):
    PsiCodeFragmentImpl(project, ArendExpressionCodeFragmentElementType, true, "fragment.ard", expression, context), IArendFile {
    override fun getReference(): ArendReference? = null

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null

    val expr: ArendExpr? get() = firstChild as? ArendExpr?

    fun fragmentResolved() {
        fragmentController?.expressionFragmentResolved(this)
    }

    fun getAdditionalScope(): Scope? = fragmentController?.getAdditionalScope(this)

    fun getMultiResolver(): SingleFileReferenceResolver? = fragmentController?.getMultiResolver()
}

abstract class ArendCodeFragmentElementType(debugName: String, val elementType: IElementType) : ICodeFragmentElementType(debugName, ArendLanguage.INSTANCE) {
    override fun parseContents(chameleon: ASTNode): ASTNode {
        val project: Project = chameleon.psi.project
        var builder = PsiBuilderFactory.getInstance().createBuilder(project, chameleon, null, ArendLanguage.INSTANCE, chameleon.chars)
        val parser = ArendParser()
        builder = GeneratedParserUtilBase.adapt_builder_(this, builder, parser, ArendParser.EXTENDS_SETS_)
        val marker = GeneratedParserUtilBase.enter_section_(builder, 0, GeneratedParserUtilBase._COLLAPSE_ , null)
        val success = doParse(builder) //ArendParser.longName(builder, 1)
        GeneratedParserUtilBase.exit_section_(builder, 0, marker, elementType, success, true, GeneratedParserUtilBase.TRUE_CONDITION)
        return builder.treeBuilt
    }

    abstract fun doParse(builder: PsiBuilder): Boolean
}

object ArendExpressionCodeFragmentElementType : ArendCodeFragmentElementType("AREND_EXPRESSION_CODE_FRAGMENT", ArendElementTypes.EXPR) {
    override fun doParse(builder: PsiBuilder): Boolean = ArendParser.expr(builder, 1, -1)
}

interface ArendCodeFragmentController {
    fun expressionFragmentResolved(codeFragment: ArendExpressionCodeFragment)

    fun getMultiResolver(): SingleFileReferenceResolver

    fun getAdditionalScope(codeFragment: ArendExpressionCodeFragment): Scope?
}