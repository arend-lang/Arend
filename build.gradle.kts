import org.jetbrains.changelog.Changelog
import java.security.MessageDigest

plugins {
    java
    idea
    `java-library`
    `maven-publish`
    id("org.jetbrains.changelog") version "2.5.0"
}

// The one place a release sets its version; see gradle.properties.
val arendVersion = providers.gradleProperty("arendVersion").get()
val pluginRevision = providers.gradleProperty("pluginRevision").orNull?.trim()?.takeIf { it.isNotEmpty() }
// The version of :intellij, and the one a release tag names: 1.12.0, or 1.12.0.1 for a release of the plugin alone.
val pluginVersion by extra(arendVersion + pluginRevision?.let { ".$it" }.orEmpty())

var annotationsVersion: String by rootProject.ext
var protobufVersion: String by rootProject.ext
var antlrVersion: String by rootProject.ext

annotationsVersion = "24.0.1"
protobufVersion = "3.24.0"
antlrVersion = "4.10"

allprojects {
    group = "org.arend"
    version = arendVersion
    repositories {
        mavenCentral()
    }

    apply {
        plugin("java")
        plugin("idea")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    idea {
        module {
            outputDir = file("$buildDir/classes/java/main")
            testOutputDir = file("$buildDir/classes/java/test")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.isDeprecation = true
        options.release.set(21)
        // options.compilerArgs.add("-Xlint:unchecked")
    }
}

subprojects {
    apply {
        plugin("maven-publish")
        plugin("java-library")
    }

    java {
        withSourcesJar()
        // Enable on-demand
        // withJavadocJar()
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                groupId = this@subprojects.group.toString()
                version = this@subprojects.version.toString()
                artifactId = this@subprojects.name
                from(components["java"])
                pom {
                    url.set("https://arend-lang.github.io")
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://github.com/JetBrains/Arend/blob/master/LICENSE")
                        }
                    }
                }
            }
        }
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "9.6.1"
}

// The expensive arend-lib round-trip / cache tests live in their own source set rather than in
// `src/test/java`. They used to sit alongside the normal tests and be kept out of `test` by
// `exclude` patterns, with each of their tasks pointing back at the `test` source set. That made
// every one of those tasks a candidate runner for *every* class under `src/test/java` as far as an
// IDE is concerned -- the mapping IDEs use is a task's `testClassesDirs`, and `include`/`exclude`
// filters are invisible to them -- so running any single test from the gutter popped up a chooser
// listing `test`, `roundTripTest`, `partialRoundTripTest` and `partialCacheTest`. Giving them a
// separate source set makes the mapping unambiguous for the ~120 normal tests.
val slowTest by sourceSets.creating

// Mirrors the `test` source set, minus what these three tests do not use (antlr, hamcrest).
dependencies {
    testImplementation("org.jetbrains:annotations:$annotationsVersion")
    testImplementation("org.antlr:antlr4-runtime:$antlrVersion")
    // :cli keeps this as `implementation`, so it is not on the test classpath by
    // inheritance; the CLI tests parse argv the way ConsoleMain does, which needs it.
    testImplementation("commons-cli:commons-cli:1.4")

    testImplementation(project(":base"))
    testImplementation(project(":parser"))
    testImplementation(project(":cli"))

    testImplementation("junit:junit:4.13.1")
    testImplementation("org.hamcrest:hamcrest-library:1.3")

    "slowTestImplementation"("org.jetbrains:annotations:$annotationsVersion")
    "slowTestImplementation"(project(":base"))
    "slowTestImplementation"(project(":parser"))
    "slowTestImplementation"(project(":cli"))
    "slowTestImplementation"("junit:junit:4.13.1")
}

// Mark the new source set as test (not production) sources for IDE imports.
idea {
    module {
        testSources.from(slowTest.java.srcDirs)
    }
}

tasks.test {
    maxHeapSize = "4g"
}

// The slow tests are too expensive for `check` to run, but they must still be *compiled* by it:
// while they lived in `src/test/java` the `test` source set compiled them even though `test`
// excluded them, so CI caught it whenever a core API change broke them (e.g. the
// getTypeExpr -> getType rename). Nothing depends on `slowTestClasses` otherwise, so without this
// they would silently rot until someone ran them by hand.
tasks.check {
    dependsOn(tasks.named("slowTestClasses"))
}

// Separate task for the arend-lib round-trip serialization test.
// Typechecks the entire standard library, so it needs more heap and is
// excluded from the default suite.  Run with:
//   ./gradlew roundTripTest [-Darend.roundtrip.modules=Algebra.Ring,Paths]
tasks.register<Test>("roundTripTest") {
    description = "Runs the arend-lib serialization round-trip test"
    group = "verification"
    maxHeapSize = "6g"
    testClassesDirs = slowTest.output.classesDirs
    classpath = slowTest.runtimeClasspath
    include("**/ArendLibRoundTripTest.class")
    System.getProperty("arend.roundtrip.modules")?.let {
        systemProperty("arend.roundtrip.modules", it)
    }
}

// Partial round-trip test: serializes a "cone" of arend-lib, then on a fresh
// server deserializes the cone and typechecks the rest of arend-lib from
// sources.  Reports only secondary typechecking errors (errors that don't
// appear in modules outside the cone).  Uses ARD+ARC overlay for cone modules
// so concrete-tree state (e.g. inline meta bodies) is available.
//
// Two ways to choose the cone; they reproduce different cache states and are not
// interchangeable (see the test's javadoc):
//   targets — cone = the target's transitive prerequisites (small deserialized side)
//   touched — cone = everything except the listed modules and their transitive
//             dependents; this is the split a real post-edit `arend --serialize`
//             produces (large deserialized side).  Reproduces the spurious
//             Topology.CoverSpace.Locale / Topology.Locale.Points errors.
// Run with:
//   ./gradlew partialRoundTripTest [-Darend.partial_roundtrip.targets=AG.Projective,Algebra.Ring.RingHom]
//   ./gradlew partialRoundTripTest -Darend.partial_roundtrip.touched=Topology.Locale.PreorderSite
tasks.register<Test>("partialRoundTripTest") {
    description = "Runs the arend-lib partial serialization round-trip test"
    group = "verification"
    maxHeapSize = "6g"
    // A larger per-thread stack helps substitution of deep cross-module types
    // surface as test errors instead of raw StackOverflowError kill-switches.
    jvmArgs("-Xss16m")
    // The subst-depth guard makes the SubstVisitor throw a diagnostic
    // SubstDepthExceeded exception instead of blowing the stack, so the test can
    // log which PiExpression was cycling.
    systemProperty("arend.subst.maxDepth", "2000")
    // The instance-depth guard makes GlobalInstancePool throw a diagnostic
    // InstanceDepthExceeded when instance resolution runs away (cycle caused by
    // deserialized instances matching each other transitively).
    systemProperty("arend.instance.maxDepth", "200")
    testClassesDirs = slowTest.output.classesDirs
    classpath = slowTest.runtimeClasspath
    include("**/ArendLibPartialRoundTripTest.class")
    System.getProperty("arend.partial_roundtrip.targets")?.let {
        systemProperty("arend.partial_roundtrip.targets", it)
    }
    System.getProperty("arend.partial_roundtrip.touched")?.let {
        systemProperty("arend.partial_roundtrip.touched", it)
    }
    System.getProperty("arend.ordering.probeOrder")?.let {
        systemProperty("arend.ordering.probeOrder", it)
    }
    // The test's whole output is its phase log on stdout; without this the task
    // prints nothing at all and a pass/fail is indistinguishable from a no-op.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

// Partial-cache repro test: clones arend-lib/bin into a temp dir, deletes one
// upstream module's .arc to simulate `touch <module>.ard`, applies the
// production loadBinaryCache cascade against the trimmed cache, then typechecks
// the whole library. Fails on any non-GOAL error: the sources are untouched, so
// every error is caused by the partial cache. Unlike partialRoundTripTest this
// drives the real BinaryLoader, so it is the one that catches loader defects
// rather than round-trip fidelity defects. Run with:
//   ./gradlew partialCacheTest -Darend.partial_cache.enabled=true \
//     [-Darend.partial_cache.touched=Algebra.Domain] \
//     [-Darend.partial_cache.target=Arith.Exp]
// Known-reproducing invalidation points:
//   -Darend.partial_cache.touched=Topology.Locale.PreorderSite
//       -> 25 errors in Topology.CoverSpace.Locale / Topology.Locale.Points
//   -Darend.partial_cache.touched=Algebra.Domain            (the default)
//       -> 9 errors in Algebra.Field.Splitting
// Requires arend-lib/bin to be pre-seeded (run `arend -L .. arend-lib -r`
// once before invoking).
tasks.register<Test>("partialCacheTest") {
    description = "Runs the partial-binary-cache contradiction-meta repro test"
    group = "verification"
    maxHeapSize = "6g"
    jvmArgs("-Xss16m")
    systemProperty("arend.subst.maxDepth", "2000")
    systemProperty("arend.instance.maxDepth", "200")
    // Required gate — without this the test self-skips via Assume.assumeTrue.
    systemProperty("arend.partial_cache.enabled",
        System.getProperty("arend.partial_cache.enabled", "true"))
    testClassesDirs = slowTest.output.classesDirs
    classpath = slowTest.runtimeClasspath
    include("**/ArendLibPartialCacheTest.class")
    System.getProperty("arend.partial_cache.touched")?.let {
        systemProperty("arend.partial_cache.touched", it)
    }
    System.getProperty("arend.partial_cache.target")?.let {
        systemProperty("arend.partial_cache.target", it)
    }
    System.getProperty("arend.partial_cache.skipDelete")?.let {
        systemProperty("arend.partial_cache.skipDelete", it)
    }
    // Don't cache results — we want explicit re-runs while iterating.
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

// Releases. RELEASING.md describes the procedure; .github/workflows/release.yml runs these tasks.

// A section of CHANGELOG.md is both the plugin's change notes (see :intellij) and the body of the GitHub release.
changelog {
    version = pluginVersion
    // The default accepts semantic versions only, and a release of the plugin alone has a fourth component
    headerParserRegex = """^(\d+\.\d+\.\d+(?:\.\d+)?)$"""
    groups = listOf("Language", "Plugin", "CLI", "arend-lib")
}

// Fails `check` on the version slips of past releases: 1.11 did not bump the version here, 1.10 shipped without its
// line in arend-lib/versions (which every installed plugin reads from master to find the library), and 1.7 had a line
// there naming a tag that did not exist. With -PreleaseTag, as the release workflow runs it, the tag must also name this
// version, and CHANGELOG.md must have its section.
val verifyReleaseMetadata = tasks.register("verifyReleaseMetadata") {
    group = "verification"
    description = "Checks that the version agrees with arend-lib, the changelog and the release tag"
    val arendLibConfig = file("arend-lib/arend.yaml")
    val arendLibVersions = file("arend-lib/versions")
    val changelogFile = file("CHANGELOG.md")
    val prelude = file("lib/Prelude.ard")
    val pluginPrelude = file("intellij/src/main/resources/lib/Prelude.ard")
    val releaseTag = providers.gradleProperty("releaseTag").orNull
    inputs.files(arendLibConfig, arendLibVersions, changelogFile, prelude, pluginPrelude)
    doLast {
        val errors = mutableListOf<String>()
        fun expect(condition: Boolean, message: () -> String) {
            if (!condition) errors += message()
        }

        expect(Regex("""\d+\.\d+\.\d+""").matches(arendVersion)) {
            "gradle.properties: arendVersion=$arendVersion must have three components, like 1.13.0"
        }
        expect(pluginRevision == null || pluginRevision.all(Char::isDigit)) {
            "gradle.properties: pluginRevision=$pluginRevision must be empty or a number"
        }

        val majorMinor = arendVersion.split('.').take(2).joinToString(".")
        val config = arendLibConfig.readLines().associate { it.substringBefore(':').trim() to it.substringAfter(':', "").trim() }
        for (key in listOf("langVersion", "version")) {
            expect(config[key] == majorMinor) { "arend-lib/arend.yaml: $key is ${config[key]}, must be $majorMinor" }
        }
        val versionsLine = arendLibVersions.readLines().lastOrNull { it.isNotBlank() }?.trim()
        expect(versionsLine == "$majorMinor -> $arendVersion") {
            "arend-lib/versions: the last line is '$versionsLine', must be '$majorMinor -> $arendVersion'"
        }

        // The CLI reads the first copy, the plugin the second
        expect(prelude.readText().replace("\r\n", "\n") == pluginPrelude.readText().replace("\r\n", "\n")) {
            "intellij/src/main/resources/lib/Prelude.ard differs from lib/Prelude.ard; copy it with ./gradlew :intellij:prelude"
        }

        if (releaseTag != null) {
            expect(releaseTag == "v$pluginVersion" || releaseTag.startsWith("v$pluginVersion-")) {
                "The tag $releaseTag does not name version $pluginVersion: it must be v$pluginVersion, or v$pluginVersion-<suffix> for a rehearsal"
            }
            val section = Regex("""^## \[?${Regex.escape(pluginVersion)}]?(\s|$)""", RegexOption.MULTILINE)
            expect(section.containsMatchIn(changelogFile.readText())) {
                "CHANGELOG.md has no section '## $pluginVersion - <date>'; ./gradlew patchChangelog renames the Unreleased section to it"
            }
        }

        if (errors.isNotEmpty()) {
            throw GradleException(errors.joinToString("\n", "Release metadata is inconsistent:\n"))
        }
    }
}

tasks.check {
    dependsOn(verifyReleaseMetadata)
}

val releaseNotes = tasks.register("releaseNotes") {
    description = "Writes the CHANGELOG.md section of this version as the body of its GitHub release"
    val output = layout.buildDirectory.file("releaseNotes/NOTES.md")
    inputs.file("CHANGELOG.md")
    inputs.property("pluginVersion", pluginVersion)
    outputs.file(output)
    doLast {
        val item = changelog.get(pluginVersion).withHeader(false).withLinks(false).withEmptySections(false)
        output.get().asFile.writeText(changelog.renderItem(item, Changelog.OutputType.MARKDOWN).trim() + "\n")
    }
}

// Everything a release publishes, built from this checkout and nothing else, in build/release: the GitHub release files
// under the names they are downloaded by, the plugin zip that is uploaded to Marketplace by hand, SHA256SUMS of all of
// them, and the release notes. Every file used to be built by hand, and that shipped a plugin jar Marketplace could not
// load and arend-lib zips with uncommitted modules in them.
val releaseAssets = tasks.register<Sync>("releaseAssets") {
    group = "distribution"
    description = "Builds and checks the release files in build/release"
    dependsOn(":arend-lib:meta:verifyLibZip", ":intellij:verifyPluginPackage")
    from(project(":cli").tasks.named("jarDep")) { rename { "Arend.jar" } }
    from(project(":api").tasks.named("jar")) { rename { "Arend-api.jar" } }
    from(project(":api").tasks.named("sourcesJar")) { rename { "Arend-api-sources.jar" } }
    from(project(":arend-lib:meta").tasks.named("libZip"))
    from(project(":intellij").tasks.named("buildPlugin"))
    from(releaseNotes)
    into(layout.buildDirectory.dir("release"))
    doLast {
        val files = destinationDir.listFiles()!!.filter { it.isFile && it.name != "NOTES.md" }.sortedBy { it.name }
        destinationDir.resolve("SHA256SUMS").writeText(files.joinToString("") { file ->
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            digest.joinToString("") { "%02x".format(it) } + "  " + file.name + "\n"
        })
    }
}
