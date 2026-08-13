plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "Arend"

include(
    "api",
    "base",
    "cli",
    "proto",
    "parser",
    "arend-lib:meta"
)

// The intellij module needs the IntelliJ Platform Gradle Plugin, which requires Gradle >= 9.0.0.
// This build's own wrapper is 9.6.1 and satisfies that, but ArendProofSearch and
// ArendLLM-DatasetGenerator pull this build in via includeBuild on their own Gradle 8.14
// wrappers, and in a composite the including build's version governs -- so an unconditional
// include breaks them at configuration time.
//
// Include it whenever the running Gradle can actually handle it, i.e. when this is a
// standalone build of Arend (./gradlew runIde works out of the box) but not when we are an
// included build of an older one. Override either way with -PwithIntellij=true|false.
val intellijSupported = gradle.parent == null &&
        GradleVersion.current() >= GradleVersion.version("9.0")

if (providers.gradleProperty("withIntellij").orNull?.toBoolean() ?: intellijSupported) {
    include("intellij")
}
