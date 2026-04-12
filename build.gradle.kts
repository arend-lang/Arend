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

// Normal test suite: exclude the expensive round-trip test.
tasks.test {
    maxHeapSize = "4g"
    exclude("**/ArendLibRoundTripTest.class")
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
