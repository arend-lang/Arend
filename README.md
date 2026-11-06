# Arend proof assistant

![](https://arend-lang.github.io/assets/images/Logo_byJB.svg)

[![JetBrains offical project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Actions Status](https://github.com/arend-lang/Arend/actions/workflows/gradle.yml/badge.svg)](https://github.com/arend-lang/Arend/actions/workflows/gradle.yml)
[![Gitter](https://badges.gitter.im/arend-lang/community.svg)](https://gitter.im/arend-lang/community?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![](https://jitpack.io/v/arend-lang/Arend.svg)](https://jitpack.io/#arend-lang/Arend)

Arend is a theorem prover and a programming language based on [Homotopy Type Theory](https://ncatlab.org/nlab/show/homotopy+type+theory).

+ For more information about the Arend language,
  visit [arend-lang.github.io](https://arend-lang.github.io/)
  or the [documentation](https://arend-lang.github.io/documentation/).
+ For instructions on building Arend locally, general description of the codebase,
  there's [ARCHITECTURE.md](ARCHITECTURE.md). How releases are made is in [RELEASING.md](RELEASING.md).
+ For community forums, checkout [this link](https://arend-lang.github.io/documentation/#forums).
+ For editing Arend code, we suggest [IntelliJ Arend](https://plugins.jetbrains.com/plugin/11162-arend)
  ([instructions](https://arend-lang.github.io/documentation/getting-started#intellij-arend)).
+ The standard library of Arend is in [arend-lib](arend-lib).
  It serves as a math library.

## Usage

### As a binary

You can find the Arend binary (a jar file named "Arend.jar") and the standard library ("arend-lib.zip")
in the [latest release](https://github.com/arend-lang/Arend/releases/latest).
It needs JDK 21 or later.
The Arend jar can be used to typecheck a library:

```bash
$ java -jar Arend.jar [path-to-library]
```

You can also start a REPL:

```bash
$ java -jar Arend.jar -i
```

If you start the REPL at the root directory of a library, the REPL will load the library.
For more information and usage about command line usage of Arend, please refer to `--help`:

```bash
$ java -jar Arend.jar -h
```

### As a library

Arend is under active development, so you may expect to depend your project on
a development version of Arend,
either a certain git revision or the SNAPSHOT version.
This is possible via [JitPack](https://jitpack.io/#arend-lang/Arend/-SNAPSHOT),
simply add this to your `build.gradle`:

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
dependencies {
    // The version of Arend -- can be a short revision, "[branch]-SNAPSHOT",
    // "-SNAPSHOT", or a tag (or a release, like "v1.4.0").
    String arendVersion = "master-SNAPSHOT"
    // Open API for writing Arend extensions
    implementation "com.github.arend-lang.Arend:api:$arendVersion"
    // The generated ANTLR parser
    implementation "com.github.arend-lang.Arend:parser:$arendVersion"
    // The generated protobuf classes
    implementation "com.github.arend-lang.Arend:proto:$arendVersion"
    // The main compiler
    implementation "com.github.arend-lang.Arend:base:$arendVersion"
}
```

In case you prefer Gradle Kotlin DSL,
use the following syntax in your `build.gradle.kts`:

```kotlin
repositories {
    maven("https://jitpack.io")
}
dependencies {
    // The version of Arend
    val arendVersion = "master-SNAPSHOT"
    implementation("com.github.arend-lang.Arend:api:$arendVersion")
    implementation("com.github.arend-lang.Arend:parser:$arendVersion")
    implementation("com.github.arend-lang.Arend:proto:$arendVersion")
    implementation("com.github.arend-lang.Arend:base:$arendVersion")
}
```
