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
    "intellij",
    "arend-lib:meta"
)
