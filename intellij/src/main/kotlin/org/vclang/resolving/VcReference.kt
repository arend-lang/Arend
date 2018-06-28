package org.vclang.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.jetbrains.jetpad.vclang.naming.reference.ModuleReferable
import com.jetbrains.jetpad.vclang.naming.reference.RedirectingReferable
import org.vclang.VcFileType
import org.vclang.VcIcons
import org.vclang.module.util.findVcFilesAndDirectories
import org.vclang.psi.*
import org.vclang.psi.ext.PsiModuleReferable
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.VcReferenceElement
import org.vclang.refactoring.VcNamesValidator

interface VcReference : PsiReference {
    override fun getElement(): VcCompositeElement

    override fun resolve(): PsiElement?
}

open class VcDefReferenceImpl<T : VcReferenceElement>(element: T): PsiReferenceBase<T>(element, TextRange(0, element.textLength)), VcReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun resolve(): PsiElement = element.parent as? PsiReferable ?: element
}

open class VcPatternDefReferenceImpl<T : VcDefIdentifier>(element: T, private val onlyResolve: Boolean): VcReferenceImpl<T>(element, VcConstructor::class.java) {
    override fun resolve(): PsiElement? = super.resolve() ?: if (onlyResolve) null else element
}

open class VcReferenceImpl<T : VcReferenceElement>(element: T, private val clazz: Class<*>?): PsiReferenceBase<T>(element, TextRange(0, element.textLength)), VcReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = element.scope.elements.mapNotNull {
        val ref = (it as? RedirectingReferable)?.originalReferable ?: it
        val origRef: Any? = if (ref is DataLocatedReferable) ref.data.element else ref
        if (clazz != null && !clazz.isInstance(origRef)) {
            null
        } else when (origRef) {
            is PsiNamedElement -> LookupElementBuilder.createWithIcon(origRef)
            is ModuleReferable -> {
                val module = if (origRef is PsiModuleReferable) (origRef.modules.firstOrNull()) else element.module?.findVcFilesAndDirectories(origRef.path)?.firstOrNull()
                module?.let {
                    if (it is VcFile)
                        LookupElementBuilder.create(it, it.textRepresentation()).withIcon(VcIcons.MODULE) else
                        LookupElementBuilder.createWithIcon(it)
                } ?: LookupElementBuilder.create(origRef, origRef.textRepresentation()).withIcon(VcIcons.DIRECTORY)
            }
            else -> LookupElementBuilder.create(ref, ref.textRepresentation())
        }
    }.toTypedArray()

    override fun resolve(): PsiElement? {
        var ref: Any? = VcResolveCache.resolveCached( { element ->
            element.scope.resolveName(element.referenceName)
        }, this.element)

        if (ref is RedirectingReferable) ref = ref.originalReferable
        if (ref is DataLocatedReferable) ref = ref.data.element
        return when (ref) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            is ModuleReferable -> {
                val list = element.module?.findVcFilesAndDirectories(ref.path) ?: return null
                list.firstOrNull { it is VcFile } ?: list.firstOrNull()
            }
            else -> null
        }
    }
}

private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
    val name = rawName.removeSuffix('.' + VcFileType.defaultExtension)
    if (!VcNamesValidator().isIdentifier(name, oldNameIdentifier.project)) return
    val factory = VcPsiFactory(oldNameIdentifier.project)
    val newNameIdentifier = when (oldNameIdentifier) {
        is VcDefIdentifier -> factory.createDefIdentifier(name)
        is VcRefIdentifier -> factory.createRefIdentifier(name)
        is VcInfixArgument -> factory.createInfixName(name)
        is VcPostfixArgument -> factory.createPostfixName(name)
        else -> error("Unsupported identifier type for `$name`")
    }
    oldNameIdentifier.replace(newNameIdentifier)
}

open class VcPolyReferenceImpl<T : VcReferenceElement>(element: T): VcReferenceImpl<T>(element, null), PsiPolyVariantReference {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        var ref: Any? = element.scope.resolveName(element.referenceName)
        if (ref is RedirectingReferable) ref = ref.originalReferable
        if (ref is DataLocatedReferable) ref = ref.data.element
        return when (ref) {
            is PsiElement -> arrayOf(PsiElementResolveResult(ref))
            is PsiModuleReferable -> ref.modules.map { PsiElementResolveResult(it) }.toTypedArray()
            is ModuleReferable -> element.module?.findVcFilesAndDirectories(ref.path)?.map { PsiElementResolveResult(it) }?.toTypedArray<ResolveResult>() ?: emptyArray()
            else -> emptyArray()
        }
    }
}
