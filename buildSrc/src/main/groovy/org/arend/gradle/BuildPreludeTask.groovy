package org.arend.gradle

import org.gradle.api.tasks.*

abstract class BuildPreludeTask extends JavaExec {
    {
        description = "Builds the prelude cache"
        group = "build"
        mainClass.set("${project.group}.frontend.PreludeBinaryGenerator".toString())
        args = [".", "--recompile"]

        dependsOn(project.tasks.getByName("classes"))
    }

    @Input
    final String projectVersion = project.version

    @InputFile
    final File preludeDotArd = project.rootProject.file("lib/Prelude.ard")

    @OutputFile
    final File preludeDotArc = project.rootProject.file("lib/Prelude.arc")

    @InputDirectory
    final File protoDefinitions = project.rootProject.file("proto/src/main/proto")

    @InputFile
    final File preludeDotJava = project.rootProject.file("base/src/main/java/org/arend/prelude/Prelude.java")
}
