import java.util.zip.ZipFile

plugins {
    java
}

val libDir = projectDir.parentFile

// cliCheck runs the jar of :cli
evaluationDependsOn(":cli")

tasks.register<JavaExec>("cliCheck") {
    group = "verification"
    description = "Typechecks arend-lib and its tests with the CLI built from this checkout"
    dependsOn(tasks.named("classes"))
    classpath(project(":cli").tasks.named("jarDep"))
    mainClass = "org.arend.frontend.ConsoleMain"
    maxHeapSize = "6g"
    // -t: the tests too, -c: double check, -r: ignore the .arc caches; and no daemon, which would run older code
    args("--no-daemon", "-tcr")
    workingDir(libDir)
}

tasks.register("copyJarDep") {
    dependsOn(":cli:copyJarDep")
}

// arend-lib.zip as a release publishes it: arend.yaml, the sources and the compiled extension in ext/.
val libZip = tasks.register<Zip>("libZip") {
    group = "distribution"
    description = "Packs arend-lib.zip"
    archiveFileName = "arend-lib.zip"
    destinationDirectory = layout.buildDirectory.dir("distributions")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    from(libDir) { include("arend.yaml") }
    from(libDir.resolve("src")) {
        include("**/*.ard")
        into("src")
    }
    from(sourceSets.main.map { it.output }) { into("ext") }
}

// The 1.11.0 and 1.12.0 zips were packed by hand from a working copy, and shipped modules that were never committed and
// vim swap files. On a CI checkout libZip cannot pick up either; this check makes a zip built from a working copy with
// uncommitted changes in arend-lib fail too.
val verifyLibZip = tasks.register("verifyLibZip") {
    group = "verification"
    description = "Checks that arend-lib.zip has exactly the committed sources of arend-lib and its extension"
    val zipFile = libZip.flatMap { it.archiveFile }
    val gitFiles = providers.exec {
        commandLine("git", "ls-files", "-z", "--", "src")
        workingDir(libDir)
    }.standardOutput.asText
    val gitStatus = providers.exec {
        commandLine("git", "status", "--porcelain", "--", "arend.yaml", "src", "meta/src")
        workingDir(libDir)
    }.standardOutput.asText
    val config = libDir.resolve("arend.yaml")
    inputs.file(zipFile)
    inputs.file(config)
    doLast {
        val status = gitStatus.get()
        check(status.isBlank()) { "arend-lib has uncommitted changes in what goes into arend-lib.zip:\n$status" }

        val entries = ZipFile(zipFile.get().asFile).use { zip ->
            zip.entries().asSequence().filterNot { it.isDirectory }.map { it.name }.toSet()
        }
        val sources = entries.filterNot { it.startsWith("ext/") }.toSet()
        val expected = gitFiles.get().split('\u0000').filter { it.endsWith(".ard") }.toSet() + "arend.yaml"
        check(sources == expected) {
            "arend-lib.zip does not have the committed sources: it also has ${sources - expected}, and lacks ${expected - sources}"
        }

        val mainClass = config.readLines().single { it.startsWith("extensionMainClass:") }.substringAfter(':').trim()
        check("ext/" + mainClass.replace('.', '/') + ".class" in entries) { "arend-lib.zip has no ext/ class $mainClass" }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":api"))
}



java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<Wrapper>().configureEach {
    gradleVersion = "8.13"
}

tasks.compileJava {
    options.encoding = "UTF-8"
}
