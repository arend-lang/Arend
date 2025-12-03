package org.arend.resolving

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.PsiCodeFragmentImpl
import org.arend.ArendIcons
import org.arend.util.ArendFragmentUtils
import org.arend.codeInsight.completion.ReplaceInsertHandler
import org.arend.ext.module.ModuleLocation
import org.arend.naming.reference.*
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.ArendNamesValidator
import org.arend.server.ArendServerService
import org.arend.term.abs.Abstract
import org.arend.term.abs.AbstractReferable
import org.arend.toolWindow.repl.ArendReplService
import org.arend.util.FileUtils
import org.arend.util.findLibrary

interface ArendReference : PsiReference {
    override fun getElement(): ArendReferenceElement

    override fun resolve(): PsiElement?
}

abstract class ArendReferenceBase<T : ArendReferenceElement>(element: T, range: TextRange)
    : PsiReferenceBase<T>(element, range), ArendReference {

    override fun handleElementRename(newName: String): PsiElement {
        element.referenceNameElement?.let { doRename(it, newName) }
        return element
    }

    override fun resolve() = element.resolvePsi()

    companion object {
        fun createArendLookUpElement(origElement: Referable?, ref: AbstractReferable?, containingFile: PsiFile?, fullName: Boolean, clazz: Class<*>?, notARecord: Boolean, lookup: String? = null): LookupElementBuilder? =
            if (ref !is ModuleReferable && (clazz != null && !clazz.isInstance(ref) || notARecord && (ref as? ArendDefClass)?.isRecord == true)) {
                null
            } else when (ref) {
                is ArendFile -> LookupElementBuilder.create(ref, (origElement as? ModuleReferable)?.path?.lastName ?: ref.name.removeSuffix(FileUtils.EXTENSION)).withIcon(ArendIcons.AREND_FILE)
                is PsiNamedElement -> {
                    val alias = (ref as? ReferableBase<*>)?.alias?.aliasIdentifier?.id?.text
                    val aliasString = if (alias == null) "" else " $alias"
                    val elementName = origElement?.refName ?: ref.name ?: ""
                    val lookupString = lookup ?: (elementName + aliasString)
                    var builder = LookupElementBuilder.create(ref, lookupString).withIcon(ref.getIcon(0))
                    if (fullName) {
                        builder = builder.withPresentableText(((ref as? PsiLocatedReferable)?.fullNameText ?: elementName) + aliasString)
                    }
                    if (alias != null) {
                        builder = builder.withInsertHandler(ReplaceInsertHandler(alias))
                    }
                    (ref as? Abstract.ParametersHolder)?.parametersText?.let {
                        builder = builder.withTailText(it, true)
                    }
                    (ref as? PsiReferable)?.psiElementType?.let { builder = builder.withTypeText(it.oneLineText) } ?:
                    ((ref as? PsiReferable)?.containingFile as? ArendFile)?.fullName?.let { builder = builder.withTypeText("from $it") }
                    builder
                }
                is ModuleReferable -> {
                    val repl = containingFile?.project?.service<ArendReplService>()?.getRepl()
                    val module: Any? = when (ref) {
                        is PsiModuleReferable -> ref.modules.firstOrNull()
                        is FullModuleReferable -> if (ref.location.locationKind == ModuleLocation.LocationKind.GENERATED) ref else {
                            (repl?.replLibraries[ref.location.libraryName] ?: containingFile?.project?.findLibrary(ref.location.libraryName))
                                ?.findArendFileOrDirectory(ref.location.modulePath, false, ref.location.locationKind == ModuleLocation.LocationKind.TEST)
                        }
                        else -> ref.path?.let {
                            (if ((containingFile as? ArendFile)?.isRepl == true) repl?.getServer()
                            else containingFile?.project?.service<ArendServerService>()?.server)
                                ?.findModule(it, null, true, true) }?.let {
                            containingFile?.project?.findLibrary(it.libraryName)?.findArendFileOrDirectory(it.modulePath, false, it.locationKind == ModuleLocation.LocationKind.TEST)
                        } ?: (repl?.replLibraries?.values?.find { it.findArendFileOrDirectory(ref.path, withAdditional = true, withTests = true) != null }
                                ?: (containingFile as? ArendFile)?.arendLibrary)
                            ?.forAvailableConfigs { config -> config.findArendFileOrDirectory(ref.path, withAdditional = true, withTests = true) }
                    }
                    val result = LookupElementBuilder.create(if (ref is FullModuleReferable) ModuleReferable(ref.location.modulePath) else ref, ref.path.lastName)
                    when {
                        (module as? PsiFileSystemItem)?.isDirectory == true -> result.withIcon(ArendIcons.DIRECTORY)
                        module is ArendFile || module is FullModuleReferable -> result.withIcon(ArendIcons.AREND_FILE)
                        else -> result
                    }
                }
                else -> null
            }
    }
}

open class ArendDefReferenceImpl<T : ArendReferenceElement>(element: T) : ArendReferenceBase<T>(element, TextRange(0, element.textLength)) {
    override fun getVariants() = if (element.parent is ArendPattern) {
        val file = element.containingFile
        element.scope.globalSubscope.elements.mapNotNull {
            createArendLookUpElement(it, it.abstractReferable, file, false, ArendConstructor::class.java, false)
        }.toTypedArray()
    } else emptyArray()

    override fun resolve() = when (val parent = element.parent) {
        is PsiReferable -> parent
        is ArendPattern -> super.resolve() ?: element
        else -> element
    }
}

open class ArendReferenceImpl<T : ArendReferenceElement>(element: T) : ArendReferenceBase<T>(element, element.rangeInElement) {
    override fun bindToElement(element: PsiElement) = element

    override fun getVariants(): Array<Any> {
        val (fragment, file) = when (val f = element.containingFile) {
            is PsiCodeFragmentImpl -> Pair(f, f.context?.containingFile as? ArendFile)
            is ArendFile -> Pair(null, f)
            else -> return emptyArray()
        }
        val server = element.project.service<ArendServerService>().server

        val result = if (fragment is PsiCodeFragmentImpl) {
            ArendFragmentUtils.getCompletionItems(element, fragment, server) ?: return emptyArray()
        } else return emptyArray()

        return result.mapNotNull { origElement -> createArendLookUpElement(origElement, origElement.abstractReferable, file, false, null, false) }.toTypedArray()
    }
}

private fun doRename(oldNameIdentifier: PsiElement, rawName: String) {
    val name = rawName.removeSuffix(FileUtils.EXTENSION)
    if (!ArendNamesValidator.INSTANCE.isIdentifier(name, oldNameIdentifier.project)) return
    val factory = ArendPsiFactory(oldNameIdentifier.project)
    val newNameIdentifier = when (oldNameIdentifier) {
        is ArendDefIdentifier, is ArendFieldDefIdentifier -> factory.createDefIdentifier(name)
        is ArendRefIdentifier -> factory.createRefIdentifier(name)
        is ArendIPName -> if (oldNameIdentifier.postfix != null) factory.createPostfixName(name) else factory.createInfixName(name)
        else -> error("Unsupported identifier type for `$name`")
    }
    oldNameIdentifier.replace(newNameIdentifier)
}
