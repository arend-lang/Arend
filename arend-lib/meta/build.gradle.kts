val baseName = "arend-lib"

plugins {
    java
}

tasks.register<JavaExec>("cliCheck") {
    group = "verification"
    dependsOn(task(":cli:jarDep"), tasks.named("classes"))
    mainClass.set("-jar")
    val jarDepPath = projectDir.resolve("cli/build/libs/cli-1.11.0-full.jar").absolutePath
    args(jarDepPath, "-tcr")
    workingDir(baseName)
}

tasks.register("copyJarDep") {
    dependsOn(task(":cli:copyJarDep"))
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
