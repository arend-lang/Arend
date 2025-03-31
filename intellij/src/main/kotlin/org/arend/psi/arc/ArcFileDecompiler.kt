package org.arend.psi.arc

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.BinaryFileDecompiler
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.compiled.ClassFileDecompilers
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.ui.EditorNotifications
import org.arend.core.definition.ClassDefinition
import org.arend.core.definition.Definition
import org.arend.core.expr.DefCallExpression
import org.arend.core.expr.visitor.VoidExpressionVisitor
import org.arend.error.DummyErrorReporter
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.server.ArendServer
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.term.group.ConcreteGroup
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer
import org.arend.term.prettyprint.ToAbstractVisitor
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.FileUtils.SERIALIZED_EXTENSION
import org.arend.util.arendModules
import org.arend.util.getRelativeFile
import org.arend.util.getRelativePath

class ArcFileDecompiler : BinaryFileDecompiler {
    override fun decompile(file: VirtualFile): CharSequence {
        val decompiler = ClassFileDecompilers.getInstance().find(file, ClassFileDecompilers.Decompiler::class.java)
        if (decompiler is ArcDecompiler) {
            return Companion.decompile(file)
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
        fun decompile(virtualFile: VirtualFile): String {
            val project = ProjectLocator.getInstance().guessProjectForFile(virtualFile) ?: return ""

            val builder = StringBuilder()

            val server = project.service<ArendServerService>().server
            val (group, arendFile, modules) = getGroup(server, virtualFile, project) ?: return builder.toString()

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
            definitions.forEach { it.accept(statementVisitor, null) }

            val filesToDefinitions = mutableMapOf<ArendFile, MutableList<String>>()
            for (module in modules) {
                (module as? ArendFile?)?.let { filesToDefinitions.put(it, mutableListOf()) }
            }

            val definitionsToFiles = mutableSetOf<String>()
            for (referable in statementVisitor.referables) {
                val file = referable.containingFile as ArendFile
                if (file == project.service<ArendServerService>().prelude) {
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
                CachingScope.make(arendFile?.scope ?: LexicalScope.opened(group) ?: EmptyScope.INSTANCE)
            )

            val lastStatement = group.statements.lastOrNull()
            for (statement in group.statements.dropLast(1)) {
                if (addStatement(statement.group, builder, config)) {
                    builder.append("\n\n")
                }
            }
            addStatement(lastStatement?.group, builder, config)
            return builder.toString()
        }

        private fun getGroup(server: ArendServer, virtualFile: VirtualFile, project: Project): Triple<ConcreteGroup, ArendFile?, List<PsiFile?>>? {
            val psiManager = PsiManager.getInstance(project)
            if (psiManager.findFile(virtualFile) !is ArcFile) return null

            val config = project.arendModules.map { ArendModuleConfigService.getInstance(it) }.find {
                it?.binariesDirFile?.let { binFile -> VfsUtilCore.isAncestor(binFile, virtualFile, true) } ?: false
            } ?: if (ApplicationManager.getApplication().isUnitTestMode) {
                ArendModuleConfigService.getInstance(project.arendModules.getOrNull(0))
            } else {
                null
            }
            val path = config?.binariesDirFile?.getRelativePath(virtualFile, SERIALIZED_EXTENSION) ?: emptyList()
            val arendFile = config?.sourcesDirFile?.getRelativeFile(path, EXTENSION)?.let { psiManager.findFile(it) } as? ArendFile?
            val moduleLocation = arendFile?.moduleLocation!!
            server.getCheckerFor(listOf(moduleLocation)).typecheck(null, DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
            val group = server.getRawGroup(moduleLocation) ?: return null

            project.service<ArcUnloadedModuleService>().removeLoadedModule(virtualFile)
            EditorNotifications.getInstance(project).updateNotifications(virtualFile)
            return Triple(
                group,
                arendFile,
                getModules(group).map { config.sourcesDirFile?.getRelativeFile(it.toList(), EXTENSION)
                    ?.let { virtualFile -> psiManager.findFile(virtualFile) } }
            )
          }

        private fun getModules(group: ConcreteGroup): List<List<String>> {
            return group.statements.mapNotNull {
                it.command?.module?.path
            }
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
