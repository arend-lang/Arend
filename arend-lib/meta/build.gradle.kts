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
