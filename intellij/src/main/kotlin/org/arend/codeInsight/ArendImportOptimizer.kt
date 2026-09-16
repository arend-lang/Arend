package org.arend.codeInsight

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.getCompleteWhere
import org.arend.server.ArendServer
import org.arend.server.ArendServerService
import org.arend.server.ImportAnalyzer
import org.arend.server.ImportFinding
import org.arend.server.ImportStructure
import org.arend.server.ImportedName
import org.arend.server.ProgressReporter
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.settings.ArendCustomCodeStyleSettings
import org.arend.settings.ArendCustomCodeStyleSettings.OptimizeImportsPolicy
import org.arend.util.ArendBundle
import org.arend.util.getFileScope
import org.arend.util.getReferableScope
import org.arend.util.mapToSet

/**
 * Brings the \import and \open commands of a file to what they should be.
 *
 * Nothing about which commands those are is decided here. [ImportAnalyzer] decides it on the server,
 * from what the resolver and the typechecker recorded and without reading the file, and this asks it
 * in one of two ways: the soft policy takes the commands it calls unused and deletes exactly those,
 * while the others take the whole block it derives and write it afresh. What is left here is the
 * writing -- where each command goes, whether a long list of names is better spelled as a bare
 * command with a \hiding clause, and how the text reaches the PSI tree.
 *
 * @see ArendCustomCodeStyleSettings.OptimizeImportsPolicy
 */
class ArendImportOptimizer : ImportOptimizer {

    override fun supports(file: PsiFile): Boolean = file is ArendFile && file.isWritable

    override fun processFile(file: PsiFile): Runnable {
        if (file !is ArendFile) return EmptyRunnable.getInstance()
        if (CodeStyle.getCustomSettings(file, ArendCustomCodeStyleSettings::class.java).OPTIMIZE_IMPORTS_POLICY == OptimizeImportsPolicy.SOFT) {
            val findings = getUnusedImports(file, ignoreErrors = true) ?: return EmptyRunnable.getInstance()
            return removingRunnable(file, findings)
        }
        val structure = getOptimalImportStructure(file, ignoreErrors = true) ?: return EmptyRunnable.getInstance()
        return rewritingRunnable(file, structure)
    }

    private fun removingRunnable(file: ArendFile, findings: List<ImportFinding>) = object : ImportOptimizer.CollectingInfoRunnable {
        override fun run() {
            removeFindings(file, findings)
            sortFileImports(file)
        }

        override fun getUserNotificationInfo(): String = notificationInfo(instancesKnown(file))
    }

    private fun rewritingRunnable(file: ArendFile, structure: ImportStructure) = object : ImportOptimizer.CollectingInfoRunnable {
        override fun run() {
            val fileImports = structure.fileImports()
            val optimalTree = structure.openStructure()
            val definitelyToHide = HashMap<String, Referable>()
            val fileScopeProvider = getScopeProvider(true, file)
            fileImports.forEach { (path, names) ->
                val scope = fileScopeProvider(path) ?: return@forEach
                names.forEach { refName ->
                    scope.resolveName(refName.visibleName())?.let { definitelyToHide[refName.visibleName()] = it }
                }
            }
            optimalTree.usages.forEach { (path, names) ->
                val scope = fileScopeProvider(path) ?: return@forEach
                names.forEach { refName ->
                    scope.resolveName(refName.visibleName())?.let { definitelyToHide[refName.visibleName()] = it }
                }
            }
            addFileImports(file, fileImports, definitelyToHide)
            addModuleOpens(file, optimalTree, definitelyToHide)
        }

        override fun getUserNotificationInfo(): String = notificationInfo(structure.instancesKnown())
    }

    private fun notificationInfo(instancesKnown: Boolean): String =
        if (instancesKnown) ArendBundle.message("arend.optimize.imports.message.core.used")
        else ArendBundle.message("arend.optimize.imports.message.scope.used")
}

private fun removeFindings(file: ArendFile, findings: List<ImportFinding>) {
    for (finding in findings) {
        when (val element = finding.data()) {
            is ArendNsId -> sameIn(file, element, ArendNsId::class.java)?.let { removeEntry(it) }
            is ArendStatCmd -> sameIn(file, element, ArendStatCmd::class.java)?.let { removeCommand(it) }
        }
    }
}

private fun <T : PsiElement> sameIn(file: ArendFile, element: T, clazz: Class<T>): T? {
    if (element.containingFile == file) return element
    val range = element.textRange
    return PsiTreeUtil.findElementOfClassAtRange(file, range.startOffset, range.endOffset, clazz)
}

private fun removeEntry(nsId: ArendNsId) {
    val nextComma = nsId.findNextSibling()?.takeIf { it.elementType == ArendElementTypes.COMMA }
    val prevComma = nsId.findPrevSibling()?.takeIf { it.elementType == ArendElementTypes.COMMA }
    // A command whose every entry is unused is reported as a whole, so an entry always remains here
    // and one of the two commas is the one to drop.
    if (nextComma != null) nextComma.delete() else prevComma?.delete()
    nsId.delete()
}

private fun removeCommand(command: ArendStatCmd) {
    val statement = command.parentOfType<ArendStat>() ?: return
    val singularWhere = statement.parentOfType<ArendWhere>()?.takeIf { it.statList.singleOrNull() == statement }
    statement.delete()
    singularWhere?.delete()
}

private fun sortFileImports(file: ArendFile) {
    val factory = ArendPsiFactory(file.project)
    val imports = file.statements.filter { it.statCmd?.isImport == true }.map {
        val text = it.text
        it.delete()
        factory.createFromText(text)!!.statements[0]
    }.sortedByDescending { it.text }
    imports.forEach { file.addBefore(it, file.firstChild) }
}

fun removeUnusedImports(group: ArendGroup) {
    val file = group.containingFile as? ArendFile ?: return
    val findings = getUnusedImports(group) ?: return
    removeFindings(file, findings.filter { finding ->
        val element = finding.data() as? PsiElement ?: return@filter false
        PsiTreeUtil.isAncestor(group, element, false)
    })
}

private fun ArendGroup.qualifiedName() : MutableList<String> {
    return if (this is ArendFile) mutableListOf()
    else this.parentGroup?.qualifiedName()?.also { it.add(this.refName) } ?: mutableListOf()
}

private val LOG = Logger.getInstance(ArendImportOptimizer::class.java)

@RequiresWriteLock
private fun addFileImports(
    file: ArendFile,
    imports: Map<ModulePath, Set<ImportedName>>,
    allIdentifiers: HashMap<String, Referable>,
) {
    eraseNamespaceCommands(file)
    doAddNamespaceCommands(file, imports, allIdentifiers,"\\import")
}

@RequiresWriteLock
private fun addModuleOpens(
        group: ArendGroup,
        rootStructure: ImportStructure.Group?,
        alreadyImported: HashMap<String, Referable>
) {
    if (group !is PsiFile) {
        eraseNamespaceCommands(group)
    }
    if (rootStructure != null && rootStructure.usages.isNotEmpty()) {
        doAddNamespaceCommands(group, rootStructure.usages, alreadyImported)
    }
    for (subgroup in group.statements.flatMap { stat -> stat.group?.let { listOf(it) + it.dynamicSubgroups } ?: emptyList() }) {
        val substructure = rootStructure?.subgroups?.find { it.name == subgroup.name }
        val subset = if (substructure == null) alreadyImported else HashMap(alreadyImported)
        addModuleOpens(subgroup, substructure, subset) // remove nested opens
    }
}

private fun doAddNamespaceCommands(
        group: ArendGroup,
        importMap: Map<ModulePath, Set<ImportedName>>,
        alreadyImported: MutableMap<String, Referable>,
        prefix: String = "\\open"
) {
    val importStatements = mutableListOf<String>()
    val settings = CodeStyle.getCustomSettings(group.containingFile, ArendCustomCodeStyleSettings::class.java)
    val scopeProvider = getScopeProvider(prefix == "\\open", group)
    for ((path, identifiers) in importMap.toSortedMap { a, b -> a.toList().joinToString().compareTo(b.toList().joinToString()) }) {
        if (path.toList().isEmpty()) continue
        if (settings.OPTIMIZE_IMPORTS_POLICY == OptimizeImportsPolicy.ONLY_IMPLICIT || identifiers.size > settings.EXPLICIT_IMPORTS_LIMIT) {
            importStatements.add(createImplicitImport(prefix, path, scopeProvider, alreadyImported, identifiers.mapToSet(ImportedName::visibleName)))
            scopeProvider(path)?.globalSubscope?.getElements(null)?.forEach { alreadyImported[it.refName] = it }
        } else {
            importStatements.add(createExplicitImport("$prefix ${path.toList().joinToString(".")}", identifiers))
        }
    }
    if (importStatements.isEmpty()) {
        return
    }
    val factory = ArendPsiFactory(group.project)
    val (groupContainer, anchorElement) = if (group is ArendFile) {
        group to group.statements.lastOrNull { it.statCmd != null }
    } else {
        val where = getCompleteWhere(group, factory)
        where to where.lbrace
    }
    val commands = factory.createFromText(importStatements.joinToString(" "))?.statements ?: return
    for (command in commands.reversed()) {
        if (anchorElement == null)
            groupContainer.addBefore(command, group.firstChild)
        else
            groupContainer.addAfter(command, anchorElement)
    }
}

private fun getScopeProvider(isForModule: Boolean, group: ArendGroup): ScopeProvider {
    val server = group.project.service<ArendServerService>().server
    return if (isForModule) {
        { (if (group is ArendFile) {
            group.moduleLocation?.let { moduleLocation -> getFileScope(group.project, moduleLocation) }
        } else {
            getReferableScope(group as? ReferableBase<*>)
        })?.resolveNamespace(it.toList()) }
    } else {
        { path -> server.getModuleScopeProvider(null, true).forModule(path) }
    }
}

private fun createExplicitImport(
    longPrefix: String,
    identifiers: Set<ImportedName>
) = longPrefix + identifiers.map { it.toString() }.sorted().joinToString(", ", " (", ")")

private typealias ScopeProvider = (ModulePath) -> Scope?

private fun createImplicitImport(
    prefix: String,
    modulePath: ModulePath,
    scopeProvider: ScopeProvider,
    alreadyImportedNames: Map<String, Referable>,
    toImportHere: Set<String>
) : String {
    // todo: modules with equal names from different libraries
    val currentScope = scopeProvider(modulePath)
    if (currentScope == null) {
        LOG.error("No library containing required module found while optimizing imports. Please report it to maintainers")
    }
    currentScope!!
    val namesToHide = currentScope.globalSubscope.getElements(null).mapNotNull { ref -> ref.refName.takeIf { it !in toImportHere && it in alreadyImportedNames && alreadyImportedNames[it] != ref } }
    val baseName = "$prefix ${modulePath.toList().joinToString(".")}"
    return if (namesToHide.isEmpty()) {
        baseName
    } else {
        "$baseName ${namesToHide.joinToString(", ", "\\hiding (", ")")}"
    }
}

@RequiresWriteLock
private fun eraseNamespaceCommands(group: ArendGroup) {
    val statCommands = group.statements.filter { it.namespaceCommand != null }
    val deleteWhere = statCommands.size == group.statements.size
    statCommands.forEach { it.delete() }
    if (deleteWhere) {
        group.where?.delete()
    }
}

internal fun getOptimalImportStructure(group: ArendGroup, ignoreErrors: Boolean = false): ImportStructure? {
    val file = group.containingFile as? ArendFile ?: return null
    val (server, module) = resolvedModuleOf(file) ?: return null
    val soft = CodeStyle.getCustomSettings(file, ArendCustomCodeStyleSettings::class.java).OPTIMIZE_IMPORTS_POLICY == OptimizeImportsPolicy.SOFT
    val structure = ImportAnalyzer(server).optimize(module, soft, ignoreErrors) ?: return null
    if (group is ArendFile) return structure

    var node = structure.openStructure()
    for (name in group.qualifiedName()) {
        // A group the lifting emptied is dropped from the structure, and it needs no commands at all.
        node = node.getSubgroup(name) ?: return ImportStructure(structure.fileImports(), ImportStructure.Group(group.refName, emptyList(), emptyMap()), structure.instancesKnown())
    }
    return ImportStructure(structure.fileImports(), node, structure.instancesKnown())
}

internal fun getUnusedImports(group: ArendGroup, ignoreErrors: Boolean = false): List<ImportFinding>? {
    val file = group.containingFile as? ArendFile ?: return null
    val (server, module) = resolvedModuleOf(file) ?: return null
    return ImportAnalyzer(server).findUnused(module, ignoreErrors)
}

internal fun instancesKnown(file: ArendFile): Boolean {
    val (server, module) = resolvedModuleOf(file) ?: return false
    return ImportAnalyzer(server).instancesTrusted(module)
}

private fun resolvedModuleOf(file: ArendFile): Pair<ArendServer, ModuleLocation>? {
    val module = file.moduleLocation ?: return null
    val server = file.project.service<ArendServerService>().server
    if (!server.scopeUsages.isCollected(module)) {
        server.getCheckerFor(listOf(module)).resolveModules(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())
    }
    return server to module
}
