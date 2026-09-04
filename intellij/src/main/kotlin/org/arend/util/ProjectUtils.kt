package org.arend.util

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurable
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.testFramework.TestModeFlags
import org.arend.ArendLanguage
import org.arend.educational.ArendConfigurator.Companion.getStudyLibrary
import org.arend.ext.error.ListErrorReporter
import org.arend.ext.prettyprinting.doc.BaseDocVisitor
import org.arend.ext.prettyprinting.doc.ReferenceDoc
import org.arend.ext.reference.ArendRef
import org.arend.injection.InjectedArendEditor
import org.arend.module.AREND_LIB
import org.arend.module.ArendModuleType
import org.arend.module.Reason
import org.arend.module.showDownloadNotification
import org.arend.ext.module.ModuleLocation
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.ExternalLibraryConfig
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.DataModuleReferable
import org.arend.naming.reference.LocatedReferableImpl
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.UnresolvedReference
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.ReferableBase
import org.arend.server.ArendLibrary
import org.arend.server.ArendServer
import org.arend.server.ArendServerService
import org.arend.settings.ArendProjectSettings
import org.arend.term.group.ConcreteGroup
import org.arend.term.prettyprint.PrettyPrintVisitor
import org.arend.typechecking.ArendExtensionChangeService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.yaml.createFromText
import org.arend.yaml.dependencies
import org.jetbrains.yaml.psi.YAMLFile
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap

private val LOG = Logger.getInstance("org.arend.util.ProjectUtils")

val Project.arendModules: List<Module>
    get() = runReadAction { ModuleManager.getInstance(this).modules.filter { ArendModuleType.has(it) } }

val Project.allModules: List<Module>
    get() = runReadAction {
        ProjectStructureConfigurable.getInstance(this)
            ?.modulesConfig?.context?.modulesConfigurator?.moduleModel?.modules
            ?.filter { ArendModuleType.has(it) } ?: arendModules
    }

val stdLib: Key<LibraryConfig?> = Key.create("AREND_TEST_STD_LIBRARY")

fun Project.findInternalLibrary(name: String): LibraryConfig? =
    ModuleManager.getInstance(this).modules.firstOrNull { ArendModuleType.has(it) && it.name == name }?.service<ArendModuleConfigService>()
        ?: if (ApplicationManager.getApplication().isUnitTestMode) {
            TestModeFlags.get(stdLib)
        } else {
            null
        }

val Project.moduleConfigs: List<ArendModuleConfigService>
    get() = allModules.map { it.service<ArendModuleConfigService>() }

fun Project.findLibrary(name: String): LibraryConfig? =
    findInternalLibrary(name) ?: findExternalLibrary(name)

fun Project.findExternalLibrary(name: String): LibraryConfig? {
    val libRoot = service<ArendProjectSettings>().librariesRoot
    return if (libRoot.isEmpty()) null else findExternalLibrary(Paths.get(libRoot), name)
}

/**
 * The `arend.yaml` of a zip library, as a [YAMLFile], together with the archive root it describes.
 *
 * The PSI is used only while it still agrees with the bytes the VFS holds. After an archive is
 * replaced, the VFS picks the change up immediately but the PSI built from the previous archive can
 * survive for a few seconds, and a config built in that window reports the *previous* library's
 * `langVersion` -- which let an extension compiled against another language version through the
 * check in `LibraryService.updateLibrary` and end in a `NoSuchMethodError` while typechecking.
 * Reparsing the bytes costs a small PSI file, so it happens only when they disagree.
 */
private fun Project.findConfigInZip(zipFile: VirtualFile): Pair<YAMLFile, VirtualFile>? {
    val zipRoot = JarFileSystem.getInstance().getJarRootForLocalFile(zipFile) ?: return null
    val configFile = zipRoot.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return null

    val fromPsi = PsiManager.getInstance(this).findFile(configFile) as? YAMLFile
    val text = try {
        String(configFile.contentsToByteArray(), Charsets.UTF_8)
    } catch (e: IOException) {
        return fromPsi?.let { it to zipRoot }
    }

    if (fromPsi != null && fromPsi.text == text) return fromPsi to zipRoot
    LOG.info("Reparsing ${configFile.path}: the PSI does not match the archive on disk")
    return createFromText(text, this)?.let { it to zipRoot }
}

fun Project.findExternalLibrary(root: VirtualFile, libName: String): ExternalLibraryConfig? {
    root.findChild(libName + FileUtils.ZIP_EXTENSION)?.let { zip ->
        findConfigInZip(zip)?.let { (yaml, zipRoot) -> return ExternalLibraryConfig(libName, yaml, zipRoot) }
    }

    val configFile = root.findChild(libName)?.findChild(FileUtils.LIBRARY_CONFIG_FILE) ?: return null
    val yaml = PsiManager.getInstance(this).findFile(configFile) as? YAMLFile ?: return null
    return ExternalLibraryConfig(libName, yaml)
}

fun Project.findExternalLibrary(root: Path, libName: String): ExternalLibraryConfig? {
    val dir = VfsUtil.findFile(root, true) ?: return null
    return runReadAction { findExternalLibrary(dir, libName) }
}

private fun Project.addDependencies(server: ArendServer, library: ArendLibrary, loaded: HashSet<String>) {
    for (dependency in library.libraryDependencies) {
        if (!loaded.add(dependency) || server.getLibrary(dependency) != null) continue
        val config = findExternalLibrary(dependency) ?: continue
        addDependencies(server, config, loaded)
        registerLibrary(server, config)
    }
}

private val reportedFailures: Key<MutableMap<String, String>> = Key.create("AREND_REPORTED_LIBRARY_FAILURES")

/**
 * The failure last reported for each library, so that a refusal is reported once rather than on every
 * attempt to register it. A lost race here can only duplicate a report, never drop one, so the map is
 * published without locking.
 */
private val Project.reportedLibraryFailures: MutableMap<String, String>
    get() = getUserData(reportedFailures) ?: ConcurrentHashMap<String, String>().also { putUserData(reportedFailures, it) }

/**
 * Registers [config] with [server] and reports whatever went wrong.
 *
 * A library the server refuses -- today, one whose declared language version excludes the running one --
 * must not disappear silently, which is how the incompatible-`arend-lib` case went unnoticed. But
 * registration is retried on every module registration, dependency synchronization and library reload,
 * and a shared dependency is retried once per Arend module, so reporting unconditionally would turn one
 * refusal into a stream of balloons. Each distinct failure is therefore reported once per library, and
 * a successful registration re-arms reporting for that library.
 */
internal fun Project.registerLibrary(server: ArendServer, config: ArendLibrary) {
    val libraryName = config.libraryName
    val errors = ListErrorReporter()
    server.updateLibrary(config, errors)

    if (server.getLibrary(libraryName) != null) {
        reportedLibraryFailures.remove(libraryName)
        errors.reportTo(NotificationErrorReporter(this))
        return
    }

    val details = errors.errorList.joinToString("\n") { it.message }
        .ifEmpty { "'$libraryName' does not support language version ${Prelude.VERSION}" }
    if (reportedLibraryFailures.put(libraryName, details) == details) return

    // For arend-lib the actionable report is the one offering the download that fixes it.
    if (libraryName == AREND_LIB && config.isExternalLibrary()) {
        showDownloadNotification(this, Reason.WRONG_VERSION, details = details)
    } else {
        errors.reportTo(NotificationErrorReporter(this))
    }
}

/**
 * Orders Arend [modules] so that every module comes after the modules it depends on.
 */
fun orderModules(modules: List<Module>): List<Module> {
    val arendModules = modules.filter { ArendModuleType.has(it) }
    if (arendModules.size < 2) return arendModules

    val byName = arendModules.associateBy { it.name }
    val result = ArrayList<Module>(arendModules.size)
    val visited = HashSet<String>()

    fun visit(module: Module) {
        if (!visited.add(module.name)) return
        val dependencies = runReadAction {
            ArendModuleConfigService.getInstance(module)?.yamlFile?.dependencies.orEmpty().map { it.name }
        }
        for (dependency in dependencies) {
            byName[dependency]?.let { visit(it) }
        }
        result.add(module)
    }

    arendModules.forEach(::visit)
    return result
}

fun Module.register(modules: List<Module> = emptyList()) {
    val config = runReadAction {
        val config = ArendModuleConfigService.getInstance(this) ?: return@runReadAction null
        config.copyFromYAML(false)
        config
    } ?: return
    refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)

    val server = project.service<ArendServerService>().server
    val loaded = modules.mapNotNullTo(HashSet()) { if (ArendModuleType.has(it)) it.name else null }
    runReadAction {
        loaded.addAll(project.arendModules.map { it.name })
        project.addDependencies(server, config, loaded)
        project.registerLibrary(server, config)
    }

    project.service<ArendExtensionChangeService>().initializeModule(config)
    config.isInitialized = true
    if (!ApplicationManager.getApplication().isUnitTestMode) {
        DaemonCodeAnalyzer.getInstance(project).restart()
    }
}

fun Module.unregister() {
    project.service<ArendExtensionChangeService>().removeModule(this)
}

fun Project.registerStudyLibrary() {
    val studyLibrary = runReadAction { getStudyLibrary(this) } ?: return
    val server = service<ArendServerService>().server
    if (server.getLibrary(studyLibrary.name) != null) return

    val loaded = HashSet<String>()
    loaded.addAll(arendModules.map { it.name })
    runReadAction {
        addDependencies(server, studyLibrary, loaded)
        registerLibrary(server, studyLibrary)
    }
}

private fun groupMatch(group1: ConcreteGroup, group2: ArendGroup, function: (ConcreteGroup, ArendGroup) -> Boolean): Boolean {
    val stats1 = group1.statements()
    val stats2 = group2.getStatements()
    if (stats1.size != stats2.size) return false
    for (i in stats1.indices) {
        val subgroup1 = stats1[i].group()
        val subgroup2 = stats2[i].getGroup()
        if (subgroup1 == null && subgroup2 == null) continue
        if (subgroup1 == null || subgroup2 == null || !function(subgroup1, subgroup2) || !groupMatch(subgroup1, subgroup2, function)) return false
    }
    return true
}

fun Project.addGeneratedModule(module: ModuleLocation, group: ConcreteGroup) {
    val builder = StringBuilder()
    PrettyPrintVisitor(builder, 0).printStatements(group.statements())
    runReadAction {
        val file = PsiFileFactory.getInstance(this).createFileFromText(module.modulePath.toList().joinToString("/") + FileUtils.EXTENSION, ArendLanguage.INSTANCE, builder.toString()) as? ArendFile ?: return@runReadAction
        file.virtualFile.isWritable = false
        file.generatedModuleLocation = module
        (group.referable as? DataModuleReferable)?.data = file
        groupMatch(group, file) { subgroup1, subgroup2 ->
            val ref2 = subgroup2.referable
            (subgroup1.referable as? LocatedReferableImpl)?.data = ref2
            val ref1 = subgroup1.referable
            if (ref1 is TCDefReferable) (ref2 as? ReferableBase<*>)?.tcReferable = ref1
            val doc1 = subgroup1.description()
            val doc2 = subgroup2.description
            if (!doc1.isNull && !doc2.isNull) {
                val refs1 = ArrayList<ArendRef>()
                val refs2 = ArrayList<ArendRef>()
                doc1.accept(CollectingDocVisitor(refs1), null)
                doc2.accept(CollectingDocVisitor(refs2), null)
                if (refs1.size == refs2.size) {
                    for ((referable, reference) in refs1.zip(refs2)) {
                        if (referable is Referable && reference is UnresolvedReference) {
                            ArendReferenceElement.cacheResolved(reference, referable)
                        }
                    }
                }
            }
            // TODO: Set data in definitions and namespace commands
            true
        }
    }
}

private class CollectingDocVisitor(private val references: MutableList<ArendRef>) : BaseDocVisitor<Void>() {
    override fun visitReference(doc: ReferenceDoc, params: Void?): Void? {
        references.add(doc.reference)
        return null
    }
}

fun Editor.isDetailedViewEditor() : Boolean = getUserData(InjectedArendEditor.AREND_GOAL_EDITOR) != null