package org.arend.arc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.SequentialProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Definition
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.visitor.VoidExpressionVisitor
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.serialization.MissingDependencyException
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.prelude.Prelude
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.server.ArendServerService
import org.arend.server.BinaryCacheLoader
import org.arend.server.ProgressReporter
import org.arend.source.FileBinarySource
import org.arend.source.GZIPStreamBinarySource
import org.arend.term.concrete.Concrete
import org.arend.term.group.ConcreteGroup
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.CoroutineCancellationIndicator
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.typechecking.runner.IntellijProgressReporter
import org.arend.util.ArendBundle
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import org.arend.util.arendModules
import org.arend.util.getRelativeFile
import org.arend.util.getRelativePath
import kotlin.collections.iterator

class ArcFileDecompiler : BinaryFileDecompiler {
    override fun decompile(file: VirtualFile): CharSequence {
        val decompiler = ClassFileDecompilers.getInstance().find(file, ClassFileDecompilers.Decompiler::class.java)
        if (decompiler is ArcDecompiler) {
            val project = ProjectLocator.getInstance().guessProjectForFile(file) ?: return ""
            return project.service<ArcDecompilationService>().getText(file)
        }

        if (decompiler is ClassFileDecompilers.Full) {
            val manager = PsiManager.getInstance(DefaultProjectFactory.getInstance().defaultProject)
            return decompiler.createFileViewProvider(file, manager, true).contents
        }

        if (decompiler is ClassFileDecompilers.Light) {
            return try {
                decompiler.getText(file)
            } catch (e: ClassFileDecompilers.Light.CannotDecompileException) {
                ClsFileImpl.decompile(file)
            }
        }

        throw IllegalStateException(decompiler.javaClass.name +
                    " should be on of " +
                    ClassFileDecompilers.Full::class.java.name +
                    " or " +
                    ClassFileDecompilers.Light::class.java.name
        )
    }

    companion object {
        private class Source(val config: ArendModuleConfigService, val path: List<String>, val arendFile: ArendFile?, val module: ModuleLocation?)

        private class LoadedModule(val group: ConcreteGroup, val arendFile: ArendFile?, val modules: List<PsiFile?>)

        /**
         * Typechecks the module of [virtualFile] if it has a source, or else loads the .arc with its imports,
         * and prints the definitions. Reports to the current progress step, and holds a read action only
         * while it reads PSI, so it must not be called under one.
         */
        suspend fun decompile(project: Project, virtualFile: VirtualFile): String = reportSequentialProgress { reporter ->
            val module = getModule(project, virtualFile, reporter)
            if (module == null) "" else reporter.nextStep(100, ArendBundle.message("arend.arc.printing")) {
                reportRawProgress { progress -> readAction { print(project, module, progress) } }
            }
        }

        private fun print(project: Project, module: LoadedModule, progress: RawProgressReporter): String {
            val builder = StringBuilder()

            val server = project.service<ArendServerService>().server
            val group = module.group

            val definitions = getDefinitions(group)
            val statementVisitor = object : VoidExpressionVisitor<Void>() {
                val referables = mutableSetOf<PsiLocatedReferable>()

                override fun visitDefCall(expr: DefCallExpression?, params: Void?): Void? {
                    val referable = expr?.definition?.referable?.data as? PsiLocatedReferable? ?: return super.visitDefCall(expr, params)
                    if (referable !is ArendFieldDefIdentifier) {
                        referables.add(referable)
                    } else {
                        ((referable.resultType as? ArendNewExpr?)
                            ?.appExpr as? ArendArgumentAppExpr?)
                            ?.atomFieldsAcc?.atom?.literal?.refIdentifier?.resolve?.let {
                                resultReferable -> (resultReferable as? PsiLocatedReferable?)?.let {
                                    referables.add(it)
                                }
                            }
                    }
                    return super.visitDefCall(expr, params)
                }

                override fun visitClass(def: ClassDefinition?, params: Void?): Void? {
                    for (superClass in def?.superClasses ?: emptyList()) {
                        val referable = superClass.referable?.data as? PsiLocatedReferable? ?: continue
                        referables.add(referable)
                    }
                    return super.visitClass(def, params)
                }
            }
            for (definition in definitions) {
                ProgressManager.checkCanceled()
                definition.accept(statementVisitor, null)
            }

            val filesToDefinitions = mutableMapOf<ArendFile, MutableList<String>>()
            for (file in module.modules) {
                (file as? ArendFile?)?.let { filesToDefinitions.put(it, mutableListOf()) }
            }

            val definitionsToFiles = mutableSetOf<String>()
            for (referable in statementVisitor.referables) {
                val file = referable.containingFile as ArendFile
                if (project.service<ArendServerService>().isPrelude(file)) {
                    continue
                }
                val fullName = referable.fullName.toString()
                filesToDefinitions.getOrPut(file) { mutableListOf() }.add(fullName)
                definitionsToFiles.add(fullName)
            }

            val fullFiles = mutableMapOf<ArendFile, Boolean>()
            filesLoop@ for ((file, fileDefinitions) in filesToDefinitions) {
                val moduleLocation = file.moduleLocation ?: continue@filesLoop
                val fileGroup = server.getRawGroup(moduleLocation)
                for (element in LexicalScope.opened(fileGroup).elements) {
                    val fullName = (element as? LocatedReferable?)?.refFullName?.toString() ?: continue
                    if (!fileDefinitions.contains(fullName) && definitionsToFiles.contains(fullName)) {
                        fullFiles[file] = false
                        continue@filesLoop
                    }
                    fullFiles[file] = true
                }
            }

            for ((file, importedDefinitions) in filesToDefinitions) {
                if (fullFiles[file] == true) {
                    builder.append("\\import ${file.fullName}\n")
                } else {
                    builder.append("\\import ${file.fullName}(${importedDefinitions.joinToString(",")})\n")
                }
            }
            if (filesToDefinitions.isNotEmpty()) {
                builder.append("\n")
            }

            val config = PrettyPrinterConfigWithRenamer(
                CachingScope.make(module.arendFile?.scope ?: LexicalScope.opened(group) ?: EmptyScope.INSTANCE)
            )

            val statements = group.statements
            for ((i, statement) in statements.withIndex()) {
                // A write action interrupts the read action here, which is then restarted
                ProgressManager.checkCanceled()
                if (addStatement(statement.group, builder, config) && i < statements.lastIndex) {
                    builder.append("\n\n")
                }
                progress.fraction((i + 1).toDouble() / statements.size)
            }
            return builder.toString()
        }

        private fun findSource(project: Project, virtualFile: VirtualFile): Source? {
            // The .arc may have been deleted while its decompilation was waiting for the previous one
            if (!virtualFile.isValid) return null
            val psiManager = PsiManager.getInstance(project)
            if (psiManager.findFile(virtualFile) !is ArcFile) return null

            val config = (project.arendModules.map { ArendModuleConfigService.getInstance(it) }.find {
                it?.binariesDirFile?.let { binFile -> VfsUtilCore.isAncestor(binFile, virtualFile, true) } ?: false
            } ?: if (ApplicationManager.getApplication().isUnitTestMode) {
                ArendModuleConfigService.getInstance(project.arendModules.getOrNull(0))
            } else {
                null
            }) ?: return null
            val path = config.binariesDirFile?.getRelativePath(virtualFile, SERIALIZED_EXTENSION) ?: mutableListOf(virtualFile.name.removeSuffix(SERIALIZED_EXTENSION))
            val arendFile = config.sourcesDirFile?.getRelativeFile(path, EXTENSION)?.let { psiManager.findFile(it) } as? ArendFile?
            return Source(config, path, arendFile, arendFile?.moduleLocation)
        }

        private suspend fun getModule(project: Project, virtualFile: VirtualFile, reporter: SequentialProgressReporter): LoadedModule? {
            val server = project.service<ArendServerService>().server
            val source = readAction { findSource(project, virtualFile) } ?: return null

            if (source.module != null) {
                reporter.nextStep(70, ArendBundle.message("arend.arc.typechecking", source.module.modulePath)) {
                    reportRawProgress { progress ->
                        val progressReporter = IntellijProgressReporter<List<Concrete.ResolvableDefinition>>(progress) {
                            it.firstOrNull()?.data?.refLongName?.toString()
                        }
                        server.getCheckerFor(listOf(source.module)).typecheck(null, DummyErrorReporter.INSTANCE, CoroutineCancellationIndicator(this), progressReporter)
                    }
                }
                // Interrupted typechecking returns normally
                currentCoroutineContext().ensureActive()
            }

            val config = source.config
            val moduleLocation = source.module
                ?: ModuleLocation(config.name, ModuleLocation.LocationKind.SOURCE, ModulePath(source.path))

            server.getRawGroup(moduleLocation)?.let { liveGroup ->
                if (BinaryCacheLoader.hasTypechecked(liveGroup)) {
                    project.service<ArcUnloadedModuleService>().removeLoadedModule(virtualFile)
                    EditorNotifications.getInstance(project).updateNotifications(virtualFile)
                    return LoadedModule(liveGroup, source.arendFile, emptyList())
                }
            }

            if (!Prelude.isInitialized()) {
                readAction {
                    server.getCheckerFor(listOf(Prelude.MODULE_LOCATION)).typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
                }
            }

            val binaryBasePath = (config.binariesDirFile ?: virtualFile.parent)?.toNioPath() ?: return null

            val result = reporter.nextStep(85, ArendBundle.message("arend.arc.loading", virtualFile.name)) {
                try {
                    GZIPStreamBinarySource(FileBinarySource(binaryBasePath, moduleLocation)).loadWithImports(server, DummyErrorReporter.INSTANCE)
                } catch (_: MissingDependencyException) {
                    project.service<ArcUnloadedModuleService>().addUnloadedModule(virtualFile)
                    EditorNotifications.getInstance(project).updateNotifications(virtualFile)
                    null
                }
            } ?: return null

            val group = result.proj1
            if (BinaryCacheLoader.hasIncompleteDefinition(group) || BinaryCacheLoader.hasOrphanShellReference(group)) {
                BinaryCacheLoader.clearTypechecked(group)
                return null
            }

            project.service<ArcUnloadedModuleService>().removeLoadedModule(virtualFile)
            EditorNotifications.getInstance(project).updateNotifications(virtualFile)
            val modules = readAction {
                val psiManager = PsiManager.getInstance(project)
                result.proj2.map { config.sourcesDirFile?.getRelativeFile(it.toList(), EXTENSION)
                    ?.let { file -> psiManager.findFile(file) } }
            }
            return LoadedModule(group, source.arendFile, modules)
        }

        private fun getDefinitions(group: ConcreteGroup): List<Definition> {
            return group.statements.mapNotNull {
                (it.group?.referable as? TCDefReferable?)?.typechecked
            }
        }

        private fun addStatement(group: ConcreteGroup?, builder: StringBuilder, config: PrettyPrinterConfig): Boolean {
            (group?.referable as? TCDefReferable?)?.typechecked?.let {
                ToAbstractVisitor.convert(it, config)
                    .prettyPrint(builder, config)
            } ?: return false
            return true
        }
    }
}
