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
    version = "1.11.0"
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
    gradleVersion = "8.13"
}

dependencies {
    testImplementation("org.jetbrains:annotations:$annotationsVersion")
    testImplementation("org.antlr:antlr4-runtime:$antlrVersion")

    testImplementation(project(":base"))
    testImplementation(project(":parser"))
    testImplementation(project(":cli"))

    testImplementation("junit:junit:4.13.1")
    testImplementation("org.hamcrest:hamcrest-library:1.3")
}

// Normal test suite: exclude the expensive round-trip tests.
tasks.test {
    maxHeapSize = "4g"
    exclude("**/ArendLibRoundTripTest.class")
    exclude("**/ArendLibPartialRoundTripTest.class")
    exclude("**/ArendLibPartialCacheTest.class")
}

// Separate task for the arend-lib round-trip serialization test.
// Typechecks the entire standard library, so it needs more heap and is
// excluded from the default suite.  Run with:
//   ./gradlew roundTripTest [-Darend.roundtrip.modules=Algebra.Ring,Paths]
tasks.register<Test>("roundTripTest") {
    description = "Runs the arend-lib serialization round-trip test"
    group = "verification"
    maxHeapSize = "6g"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/ArendLibRoundTripTest.class")
    System.getProperty("arend.roundtrip.modules")?.let {
        systemProperty("arend.roundtrip.modules", it)
    }
}

// Partial round-trip test: typechecks a target module + its prerequisites
// (the "cone"), serializes the cone, then on a fresh server deserializes the
// cone and typechecks the rest of arend-lib from sources.  Reports only
// secondary typechecking errors (errors that don't appear in modules outside
// the cone).  Uses ARD+ARC overlay for cone modules so concrete-tree state
// (e.g. inline meta bodies) is available.  Run with:
//   ./gradlew partialRoundTripTest [-Darend.partial_roundtrip.targets=AG.Projective,Algebra.Ring.RingHom]
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
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/ArendLibPartialRoundTripTest.class")
    System.getProperty("arend.partial_roundtrip.targets")?.let {
        systemProperty("arend.partial_roundtrip.targets", it)
    }
    System.getProperty("arend.ordering.probeOrder")?.let {
        systemProperty("arend.ordering.probeOrder", it)
    }
}

// Partial-cache repro test: clones arend-lib/bin into a temp dir, deletes one
// upstream module's .arc to simulate `touch <module>.ard`, applies the
// production loadBinaryCache cascade against the trimmed cache, then typechecks
// a downstream target. Fails iff `Meta 'contradiction' failed` /
// `Cannot infer contradiction` errors surface. Reproduces the CLI's
// secondary-contradiction failure mode in JUnit form. Run with:
//   ./gradlew partialCacheTest -Darend.partial_cache.enabled=true \
//     [-Darend.partial_cache.touched=Algebra.Domain] \
//     [-Darend.partial_cache.target=Arith.Exp]
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
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
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
