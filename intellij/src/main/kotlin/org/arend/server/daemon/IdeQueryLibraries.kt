package org.arend.server.daemon

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.arend.ext.module.ModulePath
import org.arend.module.config.LibraryConfig
import org.arend.query.QueryLibraries
import org.arend.query.QueryLibrary
import org.arend.server.ArendServerService
import org.arend.util.findLibrary
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A library of the project as the query tools see it. Its modules are read as the editor has them,
 * unsaved changes included, which is also what the server parsed: the offsets the tools compute
 * from the text are the server's.
 */
class IdeQueryLibrary(private val config: LibraryConfig) : QueryLibrary {
    override fun getLibraryName() = config.name

    override fun findModules(inTests: Boolean): List<ModulePath> = runReadAction { config.findModules(inTests) }

    /** Null for a zip, whose sources are no files of their own; the tools then read through [readModuleText]. */
    override fun getSourceBasePath(): Path? = runReadAction {
        config.sourcesDirFile?.takeIf { it.isInLocalFileSystem }?.let { Paths.get(it.path) }
    }

    override fun getBinaryBasePath(): Path? = runReadAction {
        config.binariesDirFile?.takeIf { it.isInLocalFileSystem }?.let { Paths.get(it.path) }
    }

    override fun readModuleText(modulePath: ModulePath, inTests: Boolean): String? =
        runReadAction { config.findArendFile(modulePath, inTests)?.text }
}

/** The libraries the project's server holds, by name; one [IdeQueryLibrary] each for the request it serves. */
class IdeQueryLibraries(private val project: Project) : QueryLibraries {
    private val libraries = HashMap<String, QueryLibrary?>()

    override fun getLibraries(): List<String> = project.service<ArendServerService>().server.libraries.toList()

    override fun getLibrary(name: String): QueryLibrary? =
        libraries.getOrPut(name) { project.findLibrary(name)?.let { IdeQueryLibrary(it) } }
}
