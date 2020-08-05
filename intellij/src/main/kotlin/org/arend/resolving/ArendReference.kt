package org.arend.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.arend.ArendFileType
import org.arend.ArendIcons
import org.arend.codeInsight.completion.ReplaceInsertHandler
import org.arend.error.DummyErrorReporter
import org.arend.naming.reference.*
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.resolving.ResolverListener
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.ext.impl.ModuleAdapter
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.refactoring.ArendNamesValidator
import org.arend.term.abs.Abstract
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
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

private object ArendIdReferableConverter : ReferableConverter {
    override fun toDataLocatedReferable(referable: LocatedReferable?) = when (referable) {
        null -> null
        is TCReferable -> referable
        is FieldReferable -> FieldReferableImpl(referable.precedence, referable.refName, referable.isExplicitField, referable.isParameterField, TCReferable.NULL_REFERABLE)
        else -> LocatedReferableImpl(referable.precedence, referable.refName, TCReferable.NULL_REFERABLE, referable.kind)
    }

    override fun convert(referable: Referable?) = (referable as? ModuleAdapter)?.metaReferable ?: referable
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
        val pParent = parent?.parent
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

        val expr = if (element is ArendIPName && element.longName.size > 1 || parent is ArendLongName && pParent !is ArendLocalCoClause && pParent !is ArendCoClause && element.findPrevSibling { (it as? LeafPsiElement)?.elementType == ArendElementTypes.DOT } == null) {
            element.ancestor<ArendExpr>()
        } else null
        val def = expr?.ancestor<PsiConcreteReferable>()
        var elements = if (expr == null) element.scope.elements else emptyList()
        val resolverListener = if (expr == null) null else object : ResolverListener {
                override fun referenceResolved(argument: Concrete.Expression?, originalRef: Referable, refExpr: Concrete.ReferenceExpression, resolvedRefs: List<Referable?>, scope: Scope) {
                    if (refExpr.data == parent || refExpr.data == element) {
                        elements = scope.elements
                    }
                }
            }
        when {
            def != null -> PsiConcreteProvider(def.project, DummyErrorReporter.INSTANCE, null, true, resolverListener, ArendIdReferableConverter).getConcrete(def)
            expr != null -> ConcreteBuilder.convertExpression(expr).accept(ExpressionResolveNameVisitor(ArendIdReferableConverter, CachingScope.make(element.scope), ArrayList<Referable>(), DummyErrorReporter.INSTANCE, resolverListener), null)
            else -> {}
        }

        return elements.mapNotNull { origElement ->
            createArendLookUpElement(origElement, element.containingFile, clazz, notARecord)
        }.toTypedArray()
    }

    override fun resolve() = resolve(false)

    protected fun resolve(onlyConstructor: Boolean): PsiElement? {
        val cache = element.project.service<ArendResolveCache>()
        val resolver = { when {
            beforeImportDot -> {
                val refName = element.referenceName
                var result: Referable? = null
                for (ref in element.scope.elements) {
                    val name = if (ref is ModuleReferable) ref.path.lastName else ref.refName
                    if (name == refName) {
                        result = ref
                        if (ref !is PsiModuleReferable || ref.modules.firstOrNull() is PsiDirectory) {
                            break
                        }
                    }
                }
                result
            }
            onlyConstructor -> {
                val ref = element.scope.globalSubscope.resolveName(element.referenceName)
                if (ref is GlobalReferable && ref.kind.isConstructor) ref else element as? ArendDefIdentifier
            }
            else -> {
                val expr = element.ancestor<ArendExpr>()
                val def = expr?.ancestor<PsiConcreteReferable>()
                when {
                    def != null -> {
                        val project = def.project
                        PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true, ArendResolverListener(cache)).getConcrete(def)
                        cache.getCached(element)
                    }
                    expr != null -> {
                        ConcreteBuilder.convertExpression(expr).accept(ExpressionResolveNameVisitor(ArendReferableConverter, CachingScope.make(element.scope), ArrayList<Referable>(), DummyErrorReporter.INSTANCE, ArendResolverListener(cache)), null)
                        cache.getCached(element)
                    }
                    else -> element.scope.resolveName(element.referenceName)
                }
            }
        } }

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

    companion object {
        fun createArendLookUpElement(origElement: Referable, containingFile: PsiFile?, clazz: Class<*>?, notARecord: Boolean): LookupElementBuilder? {
            val ref = origElement.underlyingReferable
            return if (origElement is AliasReferable || ref !is ModuleReferable && (clazz != null && !clazz.isInstance(ref) || notARecord && (ref as? ArendDefClass)?.recordKw != null)) {
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
                        containingFile?.arendLibrary?.config?.forAvailableConfigs { it.findArendFileOrDirectory(ref.path, withAdditional = true, withTests = true) }
                    }
                    when (module) {
                        null -> LookupElementBuilder.create(ref, ref.path.lastName).withIcon(ArendIcons.DIRECTORY)
                        is ArendFile -> LookupElementBuilder.create(module, ref.path.lastName).withIcon(ArendIcons.AREND_FILE)
                        else -> LookupElementBuilder.createWithIcon(module)
                    }
                }
                else -> LookupElementBuilder.create(ref, origElement.textRepresentation())
            }
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
