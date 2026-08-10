package org.arend.formatting

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.arend.ArendTestBase
import org.arend.ext.ArendExtension
import org.arend.ext.module.ModuleLocation
import org.arend.ext.module.ModulePath
import org.arend.extImpl.ConcreteFactoryImpl
import org.arend.prelude.ConcretePrelude
import org.arend.prelude.Prelude
import org.arend.server.ArendServer
import org.arend.server.ArendServerService
import org.arend.server.impl.ArendServerImpl
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Manual formatter stress test over the whole standard library.
 *
 * It loads every `.ard` file from `arend-lib/src` into the fixture project, launches the server's
 * resolver on each module, and then reformats every file, collecting any [Throwable] the formatter
 * throws. The goal is to surface formatter crashes (e.g. the `ArgumentAppExprBlock`
 * `IllegalStateException`s) across all of arend-lib rather than on a single hand-written snippet.
 *
 * It is **not** part of the normal suite — like the round-trip tests it is excluded from the default
 * `test` task and launched by hand through its own Gradle task (which builds the extension):
 *
 *   ./gradlew :intellij:formatterStressTest
 *
 * It skips (returns without doing work) when `arend-lib` is absent.
 *
 * For metas such as `run`/`later`/`rewrite` to resolve (and therefore for meta-related formatter
 * paths to be exercised), arend-lib's compiled `StdExtension` is loaded at runtime from
 * `arend-lib/meta/build/classes/java/main` via a child class loader — exactly like
 * `ArendLibRoundTripTest` — so the test needs no compile-time dependency on `arend-lib:meta`. If the
 * extension classes are absent the run continues with those metas unresolved (a warning is logged).
 *
 * A per-run log file path is printed at the start and the end; grep it for `REFORMAT_EXCEPTION`
 * and `RESOLVE_EXCEPTION`.
 */
class ArendLibReformatStressTest : ArendTestBase() {

    fun testReformatAllArendLib() {
        val arendLibRoot = locateArendLibRoot() ?: run {
            println("[stress] skipped — arend-lib not found (cwd=${Paths.get("").toAbsolutePath()})")
            return
        }
        val srcDir = arendLibRoot.resolve("src")

        val logFile = Files.createTempFile("arend_reformat_stress_", ".log")
        val out = PrintWriter(Files.newBufferedWriter(logFile), true)
        fun log(msg: String) { println(msg); out.println(msg) }

        log("[stress] log file: ${logFile.toAbsolutePath()}")
        log("[stress] arend-lib src: ${srcDir.toAbsolutePath()}")

        try {
            val server = project.service<ArendServerService>().server
            registerStdExtensionMetas(server, arendLibRoot, ::log)

            // 1. Add every .ard file to the fixture project, remembering its module path.
            val loaded = ArrayList<Pair<ModulePath, PsiFile>>()
            Files.walk(srcDir).use { stream ->
                stream.filter { it.toString().endsWith(".ard") && Files.isRegularFile(it) }
                    .sorted()
                    .forEach { p ->
                        val rel = srcDir.relativize(p).toString().replace('\\', '/')
                        val psi = myFixture.addFileToProject(rel, Files.readString(p))
                        val names = rel.removeSuffix(".ard").split('/')
                        loaded.add(ModulePath(names) to psi)
                    }
            }
            log("[stress] loaded ${loaded.size} .ard files")

            val failures = ArrayList<String>()

            // 2. Launch the resolver on every module.
            loaded.forEachIndexed { i, (modulePath, _) ->
                val loc = ModuleLocation(module.name, ModuleLocation.LocationKind.SOURCE, modulePath)
                try {
                    runReadAction { server.getResolvedDefinitions(loc) }
                } catch (e: Throwable) {
                    failures.add(record("RESOLVE_EXCEPTION", modulePath.toString(), e, ::log))
                }
                if ((i + 1) % 50 == 0) log("[stress] resolved ${i + 1}/${loaded.size}")
            }

            // 3. Reformat every file.
            loaded.forEachIndexed { i, (modulePath, psi) ->
                try {
                    WriteCommandAction.runWriteCommandAction(project) {
                        CodeStyleManager.getInstance(project).reformat(psi)
                    }
                } catch (e: Throwable) {
                    failures.add(record("REFORMAT_EXCEPTION", modulePath.toString(), e, ::log))
                }
                if ((i + 1) % 50 == 0) log("[stress] reformatted ${i + 1}/${loaded.size}")
            }

            log("[stress] done: ${loaded.size} files, ${failures.size} failure(s)")
            if (failures.isNotEmpty()) {
                val summary = failures.joinToString("\n").let { if (it.length > 8000) it.substring(0, 8000) + "…" else it }
                fail("Formatter stress test found ${failures.size} failure(s); full log at $logFile\n$summary")
            }
        } finally {
            out.flush()
            out.close()
            println("[stress] full log: ${logFile.toAbsolutePath()}")
        }
    }

    /**
     * Registers arend-lib's StdExtension metas so that `run`, `later`, `rewrite`, ... resolve.
     * StdExtension is loaded from the compiled meta classes via a child class loader (the API/base
     * classes it references are shared through the parent loader), so no compile dependency on
     * `arend-lib:meta` is needed. Mirrors `LibraryService` for the prelude and factory setup.
     */
    private fun registerStdExtensionMetas(server: ArendServer, arendLibRoot: Path, log: (String) -> Unit) {
        val metaClasses = arendLibRoot.resolve("meta/build/classes/java/main")
        if (!Files.isDirectory(metaClasses)) {
            log("[stress] WARNING: $metaClasses not found (run `./gradlew :arend-lib:meta:classes`); metas will be unresolved")
            return
        }
        try {
            val loader = URLClassLoader(arrayOf(metaClasses.toUri().toURL()), javaClass.classLoader)
            val ext = loader.loadClass("org.arend.lib.StdExtension").getDeclaredConstructor().newInstance() as ArendExtension
            // Prelude from the server's prelude group (fallback Prelude.INSTANCE); factory library name
            // must match the SOURCE module locations built below (module.name) so metaRef gets a
            // LocatedReferable parent and the generated meta modules are visible to the copied sources.
            val preludeData = (server as? ArendServerImpl)?.getGroupData(Prelude.MODULE_LOCATION)
            ext.setPrelude(if (preludeData != null) ConcretePrelude(preludeData.fileScope) else Prelude.INSTANCE)
            ext.setConcreteFactory(ConcreteFactoryImpl(null, module.name))
            addGeneratedModules { ext.declareDefinitions(this) }
            log("[stress] StdExtension metas registered from $metaClasses")
        } catch (e: Throwable) {
            val sw = StringWriter()
            e.printStackTrace(PrintWriter(sw))
            log("[stress] WARNING: could not load StdExtension metas; meta-heavy files will resolve with unresolved metas\n" +
                sw.toString().lineSequence().take(12).joinToString("\n"))
        }
    }

    private fun record(kind: String, context: String, e: Throwable, log: (String) -> Unit): String {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val head = sw.toString().lineSequence().take(12).joinToString("\n")
        log("$kind in $context: $e\n$head")
        return "$kind $context: $e"
    }

    private fun locateArendLibRoot(): Path? =
        listOf("arend-lib", "../arend-lib").map { Paths.get(it) }
            .firstOrNull { Files.isDirectory(it.resolve("src")) }
}
