import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.grammarkit.tasks.GenerateLexerTask
import org.jetbrains.grammarkit.tasks.GenerateParserTask
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

group = "org.arend.lang"
version = "1.10.0.3"

val baseName = "intellij-arend"

plugins {
    idea
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.intellij.platform") version "2.9.0"
    id("org.jetbrains.grammarkit") version "2022.3.2.2"
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

    intellijPlatform {
        create(IntelliJPlatformType.IntellijIdeaCommunity, "2025.2.1")
        testBundledModule("com.intellij.modules.json")
        bundledPlugins("org.jetbrains.plugins.yaml", "com.intellij.java")
        plugins("IdeaVIM:2.27.2")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_21
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

tasks.named<JavaExec>("runIde") {
    jvmArgs = listOf("-Xmx4g")
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

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
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
}

tasks.register<Copy>("prelude") {
    from(projectDir.resolve("lib/Prelude.ard"))
    into("src/main/resources/lib")
    dependsOn(task(":cli:buildPrelude"))
}

tasks.withType<Wrapper> {
    gradleVersion = "8.5"
}

tasks.register<RunIdeTask>("generateArendLibHTML") {
    systemProperty("java.awt.headless", true)
    args = listOf("generateArendLibHtml") +
            (project.findProperty("pathToArendLib") as String? ?: "") +
            (project.findProperty("pathToArendLibInArendSite") as String? ?: "") +
            (project.findProperty("versionArendLib") as String? ?: "") +
            (project.findProperty("updateColorScheme") as String? ?: "")
}

// Utils

fun prop(name: String): Any? = extra.properties[name]
