plugins {
    java
    idea
    `java-library`
    `maven-publish`
}

var annotationsVersion: String by rootProject.ext
var protobufVersion: String by rootProject.ext
var antlrVersion: String by rootProject.ext

annotationsVersion = "24.0.1"
protobufVersion = "3.24.0"
antlrVersion = "4.10"

allprojects {
    group = "org.arend"
    version = "1.12.0"
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
// Requires arend-lib/bin to be pre-seeded (run `arend -L .. arend-lib -r -ai`
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
