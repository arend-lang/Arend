package org.arend.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import org.arend.ArendFileType
import org.arend.ArendIcons
import org.arend.codeInsight.completion.ReplaceInsertHandler
import org.arend.naming.reference.*
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiModuleReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.parametersText
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.refactoring.ArendNamesValidator
import org.arend.term.abs.Abstract
import org.arend.typechecking.TypeCheckingService

interface ArendReference : PsiReference {
    override fun getElement(): ArendReferenceElement

    override fun resolve(): PsiElement?
}

open class ArendDefReferenceImpl<T : ArendReferenceElement>(element: T) : PsiReferenceBase<T>(element, TextRange(0, element.textLength)), ArendReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun resolve(): PsiElement = element.parent as? PsiReferable ?: element

    override fun isReferenceTo(element: PsiElement): Boolean = false
}

open class ArendPatternDefReferenceImpl<T : ArendDefIdentifier>(element: T) : ArendReferenceImpl<T>(element) {
    override fun resolve() = resolve(true)
}

open class ArendReferenceImpl<T : ArendReferenceElement>(element: T, private val beforeImportDot: Boolean = false) : PsiReferenceBase<T>(element, element.rangeInElement), ArendReference {
    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun bindToElement(element: PsiElement) = element

    override fun getVariants(): Array<Any> {
        var notARecord = false
        var clazz: Class<*>? = null
        val element = element
        val parent = element.parent
        val pParent = (parent as? ArendLongName)?.parent
        if (pParent is ArendDefClass) {
            clazz = ArendDefClass::class.java
        } else {
            val atomFieldsAcc = ((pParent as? ArendLiteral)?.parent as? ArendAtom)?.parent as? ArendAtomFieldsAcc
            val argParent = when {
                atomFieldsAcc == null -> (pParent as? ArendLongNameExpr)?.parent
                atomFieldsAcc.fieldAccList.isNotEmpty() -> null
                else -> atomFieldsAcc.parent
            }
            if ((((argParent as? ArendArgumentAppExpr)?.parent as? ArendNewExpr)?.parent as? ArendReturnExpr)?.parent is ArendDefInstance) {
                clazz = ArendDefClass::class.java
                notARecord = true
            }
        }

        return element.scope.elements.mapNotNull { origElement ->
            val ref = origElement.underlyingReferable
            if (origElement is AliasReferable || ref !is ModuleReferable && (clazz != null && !clazz.isInstance(ref) || notARecord && (ref as? ArendDefClass)?.recordKw != null)) {
                null
            } else when (ref) {
                is PsiNamedElement -> {
                    val alias = (ref as? ReferableAdapter<*>)?.getAlias()?.aliasIdentifier?.id?.text
                    var builder = LookupElementBuilder.create(ref, origElement.textRepresentation() + (if (alias == null) "" else " $alias")).withIcon(ref.getIcon(0))
                    if (alias != null) {
                        builder = builder.withInsertHandler(ReplaceInsertHandler(alias))
                    }
                    (ref as? Abstract.ParametersHolder)?.parametersText?.let {
                        builder = builder.withTailText(it, true)
                    }
                    (ref as? PsiReferable)?.psiElementType?.let { builder = builder.withTypeText(it.oneLineText) }
                    builder
                }
                is ModuleReferable -> {
                    val module = if (ref is PsiModuleReferable) {
                        ref.modules.firstOrNull()
                    } else {
                        element.containingFile?.arendLibrary?.config?.forAvailableConfigs { it.findArendFileOrDirectory(ref.path, withAdditional = true, withTests = true) }
                    }
                    when (module) {
                        null -> LookupElementBuilder.create(ref, ref.path.lastName).withIcon(ArendIcons.DIRECTORY)
                        is ArendFile -> LookupElementBuilder.create(module, ref.path.lastName).withIcon(ArendIcons.AREND_FILE)
                        else -> LookupElementBuilder.createWithIcon(module)
                    }
                }
                else -> LookupElementBuilder.create(ref, origElement.textRepresentation())
            }
        }.toTypedArray()
    }

    override fun resolve() = resolve(false)

    protected fun resolve(onlyConstructor: Boolean): PsiElement? {
        val cache = element.project.service<ArendResolveCache>()
        val resolver = { element : ArendReferenceElement ->
            if (beforeImportDot) {
                val refName = element.referenceName
                var result: Referable? = null
                for (ref in element.scope.elements) {
                    if (ref.textRepresentation() == refName) {
                        result = ref
                        if (ref !is PsiModuleReferable || ref.modules.firstOrNull() is PsiDirectory) {
                            break
                        }
                    }
                }
                result
            } else {
                val ref = element.scope.resolveName(element.referenceName)
                if (!onlyConstructor || ref is GlobalReferable && ref.kind.isConstructor) ref else null
            }
        }

        return when (val ref = cache.resolveCached(resolver, element)?.underlyingReferable) {
            is PsiElement -> ref
            is PsiModuleReferable -> ref.modules.firstOrNull()
            is ModuleReferable -> {
                if (ref.path == Prelude.MODULE_PATH) {
                    element.project.service<TypeCheckingService>().prelude
                } else {
                    element.containingFile?.arendLibrary?.config?.forAvailableConfigs { it.findArendFileOrDirectory(ref.path, withAdditional = true, withTests = true) }
                }
            }
            else -> null
        }
    }
}

private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
    val name = rawName.removeSuffix('.' + ArendFileType.defaultExtension)
    if (!ArendNamesValidator.isIdentifier(name, oldNameIdentifier.project)) return
    val factory = ArendPsiFactory(oldNameIdentifier.project)
    val newNameIdentifier = when (oldNameIdentifier) {
        is ArendDefIdentifier -> factory.createDefIdentifier(name)
        is ArendRefIdentifier -> factory.createRefIdentifier(name)
        is ArendIPName -> if (oldNameIdentifier.postfix != null) factory.createPostfixName(name) else factory.createInfixName(name)
        else -> error("Unsupported identifier type for `$name`")
    }
    oldNameIdentifier.replace(newNameIdentifier)
}
