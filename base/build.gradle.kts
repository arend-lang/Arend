dependencies {
    val protobufVersion: String by rootProject.ext
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    implementation(project(":proto"))
    // The daemon's frames are JSON; the CLI bundles Jackson, and IntelliJ ships its own.
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.11.2")

    api(project(":api"))
}

val genDir = file("src/main/gen")
val generateVersion = tasks.register<org.arend.gradle.GenerateVersionTask>("generateVersion") {
    basePackage = project.group.toString()
    outputDir = genDir.resolve("org/arend/prelude")
}

idea {
    module {
        generatedSourceDirs.add(genDir)
    }
}

sourceSets {
    main {
        java {
            srcDirs(genDir)
        }
    }
}

tasks.compileJava {
    dependsOn(generateVersion)
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(generateVersion)
}
