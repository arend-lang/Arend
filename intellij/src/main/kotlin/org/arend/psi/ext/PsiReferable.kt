package org.arend.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiNameIdentifierOwner
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.ModuleReferable
import org.arend.naming.reference.TypedReferable
import org.arend.navigation.getPresentation
import org.arend.psi.ArendDefIdentifier
import org.arend.psi.ArendPsiFactory
import org.arend.psi.childOfType
import org.arend.psi.oneLineText
import org.arend.psi.stubs.ArendNamedStub
import org.arend.term.abs.Abstract

interface PsiReferable : ArendCompositeElement, PsiNameIdentifierOwner, NavigatablePsiElement, TypedReferable {
    val psiElementType: PsiElement?
        get() = null
}

val Abstract.ParametersHolder.parametersText: String?
    get() {
        val parameters = (this as? Abstract.ParametersHolder)?.parameters ?: return null
        if (parameters.isEmpty()) {
            return null
        }

        val stringBuilder = StringBuilder()
        for (parameter in parameters) {
            if (parameter is PsiElement) {
                stringBuilder.append(' ').append(parameter.oneLineText)
            }
        }
        return stringBuilder.toString()
    }

class PsiModuleReferable(val modules: List<PsiFileSystemItem>, modulePath: ModulePath) : ModuleReferable(modulePath)

abstract class PsiReferableImpl(node: ASTNode) : ArendCompositeElementImpl(node), PsiReferable {

    override fun getNameIdentifier(): PsiElement? = childOfType<ArendDefIdentifier>()

    override fun getName(): String? = nameIdentifier?.text

    override fun textRepresentation(): String = name ?: "_"

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(ArendPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}

abstract class PsiStubbedReferableImpl<StubT> : ArendStubbedElementImpl<StubT>, PsiReferable
where StubT : ArendNamedStub, StubT : StubElement<*> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getNameIdentifier(): ArendCompositeElement? = childOfType<ArendDefIdentifier>()

    override fun getName(): String? = stub?.name ?: childOfType<ArendDefIdentifier>()?.referenceName

    override fun textRepresentation(): String = name ?: "_"

    override fun setName(name: String): PsiElement? {
        nameIdentifier?.replace(ArendPsiFactory(project).createDefIdentifier(name))
        return this
    }

    override fun getNavigationElement(): PsiElement = nameIdentifier ?: this

    override fun getTextOffset(): Int = nameIdentifier?.textOffset ?: super.getTextOffset()

    override fun getPresentation(): ItemPresentation = getPresentation(this)
}
