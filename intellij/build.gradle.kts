import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.intellij.platform.gradle.tasks.GenerateLexerTask
import org.jetbrains.intellij.platform.gradle.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

group = "org.arend.lang"
version = "1.12.0"

val baseName = "intellij-arend"

plugins {
    idea
    kotlin("jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("org.jetbrains.intellij.platform.grammarkit") version "2.18.1"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(project(":base"))
    implementation(kotlin("reflect"))
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.scilab.forge:jlatexmath:1.0.7")
    implementation("com.github.vlsi.mxgraph:jgraphx:4.2.2")
    implementation("com.fifesoft:rsyntaxtextarea:3.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.6.1")
    testImplementation("junit:junit:4.13.1")
    implementation("org.apache.xmlgraphics:batik-svggen:1.19")
    implementation("org.apache.xmlgraphics:batik-dom:1.19")

    intellijPlatform {
        create(IntelliJPlatformType.IntellijIdea, "2026.2")
        bundledPlugins("com.intellij.modules.json", "org.jetbrains.plugins.yaml", "com.intellij.java", "com.intellij.modules.jcef")
        testBundledModules("intellij.platform.navbar", "intellij.platform.navbar.backend")
        plugins("IdeaVIM:2.28.0", "com.jetbrains.edu:2026.6-2026.2-98")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
}

/*
tasks["jar"].dependsOn(
        task(":api:jar"),
        task(":proto:jar"),
        task(":base:jar")
)
*/

val generated = arrayOf("src/main/doc-lexer", "src/main/lexer", "src/main/parser")

sourceSets {
    main {
        java.srcDirs(*generated)
    }
}

idea {
    module {
        generatedSourceDirs.addAll(generated.map(::file))
        outputDir = file("${layout.buildDirectory}/classes/main")
        testOutputDir = file("${layout.buildDirectory}/classes/test")
    }
}

tasks {
    val test by getting(Test::class) {
        isScanForTestClasses = false
        // Only run tests from classes that end with "Test"
        include("**/*Test.class")
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "Arend"
    }
    instrumentCode = true
}

configurations.all {
    exclude("xml-apis", "xml-apis")
}

tasks.named<JavaExec>("runIde") {
    jvmArgs = listOf("-Xmx4g")
}

// `buildSearchableOptions` boots an IDE and runs `TraverseUIStarter`, which instantiates *every*
// registered Configurable on the EDT to scrape its labels. Since we hard-depend on `com.jetbrains.edu`
// (see plugin.xml), that includes the JetBrains Academy settings page:
//   EduConfigurable.createComponent -> LoginOptions.initAccounts -> MarketplaceConnector.getAccount
//   -> MarketplaceSettings.getMarketplaceAccount -> getJBAUserInfo -> getJBAIdToken
// `getMarketplaceAccount` returns early only while no JetBrains Account is logged in; otherwise it goes
// on to the *blocking* `JBAccountInfoService.getIdToken()`, called from the EDT, whose future is never
// completed in this IDE mode. The event thread then parks forever and the build never finishes (the
// symptom is `buildPlugin` sitting at 0% CPU in buildSearchableOptions with no network activity).
//
// The account is not part of the sandbox: it lives in the machine-global `java.util.prefs` store, at
// ~/.java/.userPrefs/jetbrains/jetprofile (keys `userid` and `idtoken`). Pointing this throwaway IDE at
// an empty prefs root is enough to make the Academy page render as logged out, so the traversal walks
// past it and we still get searchable options for our own settings pages.
//
// The task also gets its own config/system/log directories. By default it shares them with `runIde`
// (the plugin wires every SandboxAware task to `prepareSandbox`), so building while a sandbox IDE is
// open trips the single-instance lock and the task dies with "Only one instance of IDEA can be run at
// a time." Only the plugins directory stays shared — that is the one `prepareSandbox` populates, and
// loading the plugin under test is all we need from it.
tasks.withType<BuildSearchableOptionsTask>().configureEach {
    val ideDirectory = layout.buildDirectory.dir("tmp/searchableOptionsIde").get().asFile
    val prefsRoot = ideDirectory.resolve("prefs")
    val configDirectory = ideDirectory.resolve("config")
    val systemDirectory = ideDirectory.resolve("system")
    val logDirectory = ideDirectory.resolve("log")

    systemProperty("java.util.prefs.userRoot", prefsRoot.absolutePath)
    sandboxConfigDirectory.set(configDirectory)
    sandboxSystemDirectory.set(systemDirectory)
    sandboxLogDirectory.set(logDirectory)

    doFirst {
        // `SandboxArgumentProvider` silently omits -Didea.{config,system,log}.path for directories that
        // do not exist yet, which would send this IDE to the developer's real IntelliJ config. The
        // arguments are computed in the task action, so creating the directories here is early enough.
        listOf(prefsRoot, configDirectory, systemDirectory, logDirectory).forEach { it.mkdirs() }
    }
}

tasks.withType<PatchPluginXmlTask>().configureEach {
    version = project.version.toString()
    pluginId.set(project.group.toString())
    changeNotes.set(file("src/main/html/change-notes.html").readText())
    pluginDescription.set(file("src/main/html/description.html").readText())
}

tasks.withType<BuildPluginTask>().configureEach {
  archiveBaseName = baseName
}

val generateArendLexer = tasks.register<GenerateLexerTask>("genArendLexer") {
    description = "Generates lexer"
    group = "build setup"
    sourceFile.set(file("src/main/grammars/ArendLexer.flex"))
    targetOutputDir.set(file("src/main/lexer/org/arend/lexer"))
    purgeOldFiles.set(true)
}

val generateArendParser = tasks.register<GenerateParserTask>("genArendParser") {
    description = "Generates parser"
    group = "build setup"
    sourceFile.set(file("src/main/grammars/ArendParser.bnf"))
    targetRootOutputDir.set(file("src/main/parser"))
    pathToParser.set("/org/arend/parser/ArendParser.java")
    pathToPsiRoot.set("/org/arend/psi")
    purgeOldFiles.set(true)
}

val generateArendDocLexer = tasks.register<GenerateLexerTask>("genArendDocLexer") {
    description = "Generates doc lexer"
    group = "build setup"
    sourceFile.set(file("src/main/grammars/ArendDocLexer.flex"))
    targetOutputDir.set(file("src/main/doc-lexer/org/arend/lexer"))
    purgeOldFiles.set(true)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(generateArendLexer, generateArendParser, generateArendDocLexer)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
        languageVersion.set(KotlinVersion.KOTLIN_2_2)
        apiVersion.set(KotlinVersion.KOTLIN_2_2)
        freeCompilerArgs.set(listOf("-Xjvm-default=all"))
    }
    dependsOn(generateArendLexer, generateArendParser, generateArendDocLexer)
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "2048m"
    testLogging {
        if (prop("showTestStatus") == "true") {
            events = setOf(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
        exceptionFormat = TestExceptionFormat.FULL
    }
    // The JetBrains Academy plugin (a hard dependency of ours, see plugin.xml) ships its own outdated copies
    // of the `ai.grazie.*` libraries. In the IDE every plugin gets its own class loader, but tests run from a
    // single flat class path where those jars come first and shadow the copies bundled with the Grazie plugin.
    // Grazie's spell checker then dies with `NoSuchMethodError: ai.grazie.nlp.langs.Language.getEntries()`,
    // failing every test that reaches a name suggestion provider (e.g. the inplace renamer started by the
    // generate-function intentions). Keep the Academy jars available, but let the IDE's own copies win.
    val isAcademyJar = { file: File -> file.path.contains("JetBrainsAcademy") }
    classpath = classpath.filter { !isAcademyJar(it) } + classpath.filter(isAcademyJar)
}

// The whole-standard-library formatter stress test lives in its own `slowTest` source set rather than
// in `src/test/kotlin`. It used to sit alongside the normal tests, kept out of `test` by an `exclude`
// pattern while `formatterStressTest` pointed its `testClassesDirs` back at the `test` source set.
// The mapping an IDE uses to decide which Gradle task can run a given test is the task's
// `testClassesDirs`; `include`/`exclude` patterns and `filter {}` are not part of it. So both tasks
// looked like valid runners for every one of the ~100 classes under `src/test/kotlin`, and running any
// single test from the gutter first popped up a chooser offering `test` and `formatterStressTest`.
//
// Unlike the root project's slow tests, this one extends ArendTestBase, so the source set has to
// compile against the `test` output on top of the IntelliJ Platform test dependencies that the
// platform plugin puts on `test`.
val slowTest by sourceSets.creating {
    val test = sourceSets.test.get()
    compileClasspath += test.output + test.compileClasspath
    runtimeClasspath += test.output + test.runtimeClasspath
}

// The stress test is far too expensive for `check` to run, but it must still be *compiled* by it:
// while it lived in `src/test/kotlin` the `test` source set compiled it even though `test` excluded
// it, so CI caught any change that broke it. Nothing else depends on `slowTestClasses`.
tasks.named("check") {
    dependsOn(tasks.named("slowTestClasses"))
}

idea {
    module {
        testSources.from(file("src/slowTest/kotlin"))
    }
}

// Dedicated task for the whole-standard-library formatter stress test, analogous to the root
// project's `roundTripTest`. It clones the platform configuration of the default `test` task (test
// framework classpath, sandbox JVM arguments and its task dependencies), then narrows it to the
// stress test, builds arend-lib's StdExtension (loaded at runtime so metas resolve) and gives the
// JVM enough heap to resolve+reformat all of arend-lib. Run with:
//   ./gradlew :intellij:formatterStressTest
tasks.register<Test>("formatterStressTest") {
    val defaultTest = tasks.named<Test>("test").get()
    group = "verification"
    description = "Reformats every arend-lib source file to surface formatter exceptions"
    testClassesDirs = slowTest.output.classesDirs
    // The platform test framework classpath and the `test` output (for ArendTestBase) come from the
    // default test task; only the stress test's own classes have to be added on top.
    classpath = defaultTest.classpath + slowTest.output
    jvmArgumentProviders.addAll(defaultTest.jvmArgumentProviders)
    systemProperties(defaultTest.systemProperties)
    dependsOn(defaultTest.dependsOn)
    dependsOn(":arend-lib:meta:classes")
    systemProperty("java.awt.headless", true)
    maxHeapSize = "6g"
    filter { includeTestsMatching("org.arend.formatting.ArendLibReformatStressTest") }
}

tasks.register<Copy>("prelude") {
    from(projectDir.resolve("lib/Prelude.ard"))
    into("src/main/resources/lib")
    // dependsOn(task(":cli:buildPrelude"))
}

tasks.withType<Wrapper> {
    gradleVersion = "9.6.1"
}

tasks.register<RunIdeTask>("generateArendLib") {
    systemProperty("java.awt.headless", true)
    dependsOn(tasks.prepareSandbox)

    val sandbox = tasks.runIde.get().sandboxDirectory.get()
    systemProperty("idea.plugins.path", sandbox.dir("plugins").asFile.absolutePath)

    splitMode.set(false)
    splitModeTarget.set(SplitModeAware.SplitModeTarget.BOTH)
    args = listOf("generateArendLib") +
            (project.findProperty("pathToArendLib") as? String ?: "") +
            (project.findProperty("pathToArendLibInArendSite") as? String ?: "") +
            (project.findProperty("versionArendLib") as? String ?: "null") +
            (project.findProperty("updateColorScheme") as? String ?: "") +
            layout.projectDirectory.asPath.toString() +
            (project.findProperty("classes") as? String ?: "")
}

// Utils

fun prop(name: String): Any? = extra.properties[name]
