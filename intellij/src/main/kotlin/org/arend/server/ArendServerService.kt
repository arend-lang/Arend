package org.arend.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFileFactory
import org.arend.ArendLanguage
import org.arend.error.DummyErrorReporter
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.server.impl.ArendServerImpl
import org.arend.term.abs.ConcreteBuilder
import org.arend.util.FileUtils
import java.nio.charset.StandardCharsets

@Service(Service.Level.PROJECT)
class ArendServerService(val project: Project) : Disposable {
    private var serverField: ArendServer? = null
    private var preludeField: ArendFile? = null

    private fun initialize(): ArendServer {
        serverField?.let { return it }
        synchronized(this) {
            val server = ArendServerImpl(ArendServerRequesterImpl(project), true, false, !ApplicationManager.getApplication().isUnitTestMode)
            serverField = server
            val preludeFileName = Prelude.MODULE_PATH.toString() + FileUtils.EXTENSION
            val preludeText = String(
                ArendServerService::class.java.getResourceAsStream("/lib/$preludeFileName")!!.readBytes(),
                StandardCharsets.UTF_8
            )
            preludeField = runReadAction {
                val prelude = PsiFileFactory.getInstance(project)
                    .createFileFromText(preludeFileName, ArendLanguage.INSTANCE, preludeText) as? ArendFile
                if (prelude != null) {
                    prelude.virtualFile?.isWritable = false
                    prelude.generatedModuleLocation = Prelude.MODULE_LOCATION
                    server.addReadOnlyModule(Prelude.MODULE_LOCATION) {
                        ConcreteBuilder.convertGroup(prelude, Prelude.MODULE_LOCATION, DummyErrorReporter.INSTANCE)
                    }
                }
                prelude
            }
            return server
        }
    }

    val server: ArendServer
        get() = initialize()

    fun isPrelude(file: VirtualFile) = file == preludeField?.virtualFile

    fun isPrelude(file: ArendFile) = file == preludeField

    val preludeIfInitialized: ArendFile?
        get() = preludeField

    val initializeAndGetPrelude: ArendFile?
        get() {
            initialize()
            return preludeField
        }

    override fun dispose() {}

    fun getLibraries(libraryName: String?, withSelf: Boolean, withPrelude: Boolean): List<ArendLibrary> {
        val library = (if (libraryName != null) server.getLibrary(libraryName) else null) ?: return emptyList()
        val dependencies = library.libraryDependencies.mapNotNull { server.getLibrary(it) }
        val prelude = if (withPrelude) listOfNotNull(server.getLibrary(Prelude.LIBRARY_NAME)) else emptyList()
        return (if (withSelf) listOf(library) else emptyList()) + dependencies + prelude
    }
}