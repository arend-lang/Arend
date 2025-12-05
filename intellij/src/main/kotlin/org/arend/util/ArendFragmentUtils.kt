package org.arend.util

import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import org.arend.ArendFileTypeInstance
import org.arend.error.DummyErrorReporter
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.doc.DocFactory
import org.arend.ext.reference.Precedence
import org.arend.highlight.HighlightingVisitor
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.DataModuleReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.naming.reference.LongUnresolvedReference
import org.arend.naming.reference.NamedUnresolvedReference
import org.arend.naming.reference.Referable
import org.arend.naming.resolving.CollectingResolverListener
import org.arend.naming.resolving.visitor.ExpressionResolveNameVisitor
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.MergeScope
import org.arend.psi.ArendFile
import org.arend.psi.childOfType
import org.arend.psi.ext.ArendExpr
import org.arend.psi.ext.ArendLongName
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiModuleReferable
import org.arend.psi.ext.ReferableBase
import org.arend.psi.fragments.ArendExpressionCodeFragment
import org.arend.psi.fragments.ArendFileChooserFragment
import org.arend.psi.fragments.ArendLongNameCodeFragment
import org.arend.psi.fragments.ArendReferableChooserFragment
import org.arend.psi.parentOfType
import org.arend.server.ArendServer
import org.arend.server.ArendServerRequesterImpl
import org.arend.term.abs.ConcreteBuilder
import org.arend.term.concrete.Concrete
import org.arend.term.group.AccessModifier
import org.arend.term.group.ConcreteGroup
import org.arend.term.group.ConcreteStatement

class ArendFragmentUtils {

    companion object {
        fun getCompletionItems(
            element: ArendReferenceElement,
            ambientFragment: PsiCodeFragment,
            server: ArendServer
        ): List<Referable>? {
            val result = ArrayList<Referable>()
            val context = ambientFragment.context

            val dummyErrorReporter = DummyErrorReporter.INSTANCE

            val concreteExpression = when (ambientFragment) {
                is ArendLongNameCodeFragment -> {
                    val longNameRef = ambientFragment.childOfType<ArendLongName>() ?: return null
                    Concrete.ReferenceExpression(longNameRef.data, longNameRef.referent)
                }

                is ArendExpressionCodeFragment -> {
                    val rootNode = ambientFragment.childOfType<ArendExpr>() ?: return null
                    ConcreteBuilder.convertExpression(rootNode, dummyErrorReporter)
                }

                else -> return null
            }

            val contextFile = context?.containingFile as? ArendFile
            contextFile?.moduleLocation?.let { moduleLocation ->
                ArendServerRequesterImpl(element.project).doUpdateModule(server, moduleLocation, contextFile)
            }
            val contextFileConcreteGroup = contextFile?.moduleLocation?.let { server.getRawGroup(it) }
            val moduleLocation = contextFile?.moduleLocation?.let { DataModuleReferable(contextFile, it) }
            val parentLocatedReferable: LocatedReferable? =
                (((context as? ReferableBase<*>) ?: (context?.parentOfType<ReferableBase<*>>()))?.tcReferable)
                    ?: moduleLocation

            when (ambientFragment) {
                is ArendFileChooserFragment -> {
                    val module = ambientFragment.getContextModule() ?: return null
                    val pathPrefix =
                        when (val referent = (concreteExpression as Concrete.ReferenceExpression).referent) {
                            is NamedUnresolvedReference -> emptyList<String>()
                            is LongUnresolvedReference -> referent.path.dropLast(1)
                            else -> return null
                        }
                    result.addAll(getModuleRoots(module, pathPrefix, ambientFragment.isInTests()).map {
                        PsiModuleReferable(
                            listOf(it), ModulePath(
                                pathPrefix + listOf(
                                    when (it) {
                                        is ArendFile -> it.refName
                                        else -> it.name
                                    }
                                )
                            )
                        )
                    })
                }

                is ArendReferableChooserFragment -> {
                    contextFileConcreteGroup ?: return null
                    moduleLocation ?: return null

                    val referent = (concreteExpression as Concrete.ReferenceExpression).referent
                    val scope = server.getReferableScope(moduleLocation) ?: return null
                    val group: ConcreteGroup = when (referent) {
                        is NamedUnresolvedReference -> contextFileConcreteGroup
                        is LongUnresolvedReference -> {
                            val shortRef = LongUnresolvedReference(
                                null,
                                referent.referenceList.dropLast(1),
                                referent.path.dropLast(1)
                            )
                            val referable = ExpressionResolveNameVisitor.tryResolve(shortRef, scope, null)
                            fun findGroup(group: ConcreteGroup): ConcreteGroup? {
                                if (group.referable == referable) return group
                                for (dynamicGroup in group.dynamicGroups) {
                                    val result = findGroup(dynamicGroup)
                                    if (result != null) return result
                                }
                                for (statement in group.statements) {
                                    val result = statement.group?.let { findGroup(it) }
                                    if (result != null) return result
                                }
                                return null
                            }
                            findGroup(contextFileConcreteGroup) ?: return null
                        }

                        else -> return null
                    }
                    for (statement in group.statements) statement?.group?.referable?.let { result.add(it) }
                    for (dynamicGroup in group.dynamicGroups) dynamicGroup?.referable?.let { result.add(it) }
                }

                is ArendExpressionCodeFragment -> {
                    contextFileConcreteGroup ?: return null
                    parentLocatedReferable ?: return null

                    val dummyReferable = LocatedReferableImpl(
                        ambientFragment, AccessModifier.PUBLIC, Precedence.DEFAULT, "foo",
                        Precedence.DEFAULT, null, parentLocatedReferable, GlobalReferable.Kind.FUNCTION
                    )
                    val dummyFunction = Concrete.FunctionDefinition(
                        FunctionKind.FUNC, dummyReferable, emptyList(), null, null,
                        Concrete.TermFunctionBody(ambientFragment, concreteExpression)
                    )

                    val dummyGroup = ConcreteGroup(
                        DocFactory.nullDoc(),
                        dummyFunction.data,
                        dummyFunction,
                        emptyList(),
                        emptyList(),
                        emptyList()
                    )

                    val sampleGroup = mapGroup(contextFileConcreteGroup, parentLocatedReferable, dummyGroup)
                    val additionalScope = ambientFragment.getAdditionalScope()
                    result.addAll(additionalScope?.elements ?: emptyList())
                    val variants = server.getCompletionVariants(sampleGroup, element).filter { additionalScope == null || additionalScope.resolveName(it.refName) == null }
                    result.addAll(variants.filter { (it as? LocatedReferableImpl)?.let { it.locatedReferableParent == parentLocatedReferable } != true  })
                }

                else -> return null
            }
            return result
        }

        fun resolveFragment(file: ArendExpressionCodeFragment, visitor: HighlightingVisitor, server: ArendServer) {
            val context = file.context ?: return
            val project = file.project

            val concrete = ConcreteBuilder.convertExpression(file.expr)
            val contextFile = context.containingFile as? ArendFile
            val moduleLocation = contextFile?.moduleLocation?.let { DataModuleReferable(contextFile, it) }

            val resolverListener = CollectingResolverListener(ArendServerRequesterImpl(project), true)
            val parentLocatedReferable: LocatedReferable? = (((context as? ReferableBase<*>) ?: (context.parentOfType<ReferableBase<*>>()))?.tcReferable) ?: moduleLocation

            val scope = MergeScope(listOf(file.getAdditionalScope() ?: EmptyScope.INSTANCE, parentLocatedReferable?.let { server.getReferableScope(it) } ?: EmptyScope.INSTANCE))

            ExpressionResolveNameVisitor(scope, ArrayList(), server.typingInfo, DummyErrorReporter.INSTANCE,
                null, resolverListener).resolve(concrete)

            concrete.accept(visitor, null)
            for (resolvedReference in resolverListener.getCacheStructure(null)?.cache ?: emptyList()) {
                (resolvedReference.reference as? ArendReferenceElement)?.putResolved(resolvedReference.referable)
            }
            file.fragmentResolved()
        }

        private fun mapGroup(
            group: ConcreteGroup,
            groupToInsertInto: LocatedReferable,
            groupToInsert: ConcreteGroup
        ): ConcreteGroup {
            val statements = ArrayList<ConcreteStatement>()
            for (statement in group.statements) {
                val group = statement.group?.let { mapGroup(it, groupToInsertInto, groupToInsert) }
                statements.add(
                    ConcreteStatement(
                        group,
                        statement.command,
                        statement.pLevelsDefinition(),
                        statement.hLevelsDefinition()
                    )
                )
            }

            if (group.referable == groupToInsertInto)
                statements.add(ConcreteStatement(groupToInsert, null, null, null))

            val dynamicGroups = ArrayList<ConcreteGroup>()
            for (dynamicGroup in group.dynamicGroups) {
                dynamicGroups.add(mapGroup(dynamicGroup, groupToInsertInto, groupToInsert))
            }

            return ConcreteGroup(
                group.description,
                group.referable,
                group.definition,
                statements,
                dynamicGroups,
                group.externalParameters()
            )
        }

        private fun getModuleRoots(
            module: Module,
            pathPrefix: List<String>,
            inTests: Boolean
        ): List<PsiFileSystemItem> {
            val config = ArendModuleConfigService.getInstance(module) ?: return emptyList()
            val root = config.root ?: return emptyList()
            val psiManager = PsiManager.getInstance(module.project)
            val result = mutableListOf<PsiFileSystemItem>()

            val sourceRoot =
                if (inTests) config.testsDir.let { root.findFileByRelativePath(it) } else config.sourcesDir.let {
                    root.findFileByRelativePath(it)
                }
            var currentDir: VirtualFile? = sourceRoot
            var pathExists = true

            // Traverse the logical path prefix in the file system
            for (name in pathPrefix) {
                currentDir = currentDir?.findChild(name)
                if (currentDir == null || !currentDir.isDirectory) {
                    pathExists = false
                    break
                }
            }

            if (pathExists && currentDir != null) {
                for (child in currentDir.children) {
                    if (child.isDirectory) {
                        psiManager.findDirectory(child)?.let { result.add(it) }
                    } else if (child.extension == ArendFileTypeInstance.defaultExtension) {
                        // Verify it's actually an ArendFile
                        val psiFile = psiManager.findFile(child)
                        if (psiFile is ArendFile) {
                            result.add(psiFile)
                        }
                    }
                }
            }

            return result
        }
    }


}