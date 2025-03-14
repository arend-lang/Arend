package org.arend.actions.mark

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.ide.projectView.actions.MarkRootActionBase
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.*
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.arend.actions.mark.DirectoryType.*
import org.arend.module.config.ArendModuleConfigService
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File

enum class DirectoryType {
    SRC,
    TEST_SRC,
    BIN
}

private fun getRelativePath(arendModuleConfigService: ArendModuleConfigService?, virtualFile: VirtualFile?): String? {
    return arendModuleConfigService?.root?.path?.let { File(it) }
        ?.let { virtualFile?.path?.let { path -> File(path).relativeTo(it).path } }
}

private fun getContentFolder(directoryType: DirectoryType, oldDir: String?, entry: ContentEntry?, arendModuleConfigService: ArendModuleConfigService?): ContentFolder? {
    return when (directoryType) {
        SRC, TEST_SRC -> entry?.sourceFolders
        BIN -> entry?.excludeFolders
    }?.find {
        getRelativePath(arendModuleConfigService, it.file) == oldDir
    }
}

private fun getDirByType(directoryType: DirectoryType, arendModuleConfigService: ArendModuleConfigService?): String? {
    return when (directoryType) {
        SRC -> arendModuleConfigService?.sourcesDir
        TEST_SRC -> arendModuleConfigService?.testsDir
        BIN -> arendModuleConfigService?.binariesDirectory
    }
}

fun commitModel(module: Module?, model: ModifiableRootModel?) {
    runUndoTransparentWriteAction {
        model?.commit()
        module?.project?.let { SaveAndSyncHandler.getInstance().scheduleProjectSave(it) }
    }
}

internal fun removeOldFolder(virtualFile: VirtualFile?, model: ModifiableRootModel?, arendModuleConfigService: ArendModuleConfigService?, directoryType: DirectoryType) {
    val entry = model?.let { rootModel -> virtualFile?.let { file -> MarkRootActionBase.findContentEntry(rootModel, file) } }
    val oldDir = getDirByType(directoryType, arendModuleConfigService)
    getContentFolder(directoryType, oldDir, entry, arendModuleConfigService)?.let {
        when (directoryType) {
            SRC, TEST_SRC -> entry?.removeSourceFolder(it as SourceFolder)
            BIN -> entry?.removeExcludeFolder(it as ExcludeFolder)
        }
    }
}

internal fun addNewFolder(virtualFile: VirtualFile, model: ModifiableRootModel?, directoryType: DirectoryType) {
    val entry = model?.let { MarkRootActionBase.findContentEntry(it, virtualFile) }
    when (directoryType) {
        SRC -> entry?.addSourceFolder(virtualFile, JavaSourceRootType.SOURCE)
        TEST_SRC -> entry?.addSourceFolder(virtualFile, JavaSourceRootType.TEST_SOURCE)
        BIN -> entry?.addExcludeFolder(virtualFile)
    }
}

internal fun removeMarkedDirectory(file: VirtualFile?, model: ModifiableRootModel?, arendModuleConfigService: ArendModuleConfigService?, directoryType: DirectoryType) {
    val dir = getDirByType(directoryType, arendModuleConfigService)
    if (!dir.isNullOrEmpty()) {
        removeOldFolder(file, model, arendModuleConfigService, directoryType)
    }
}

internal fun addMarkedDirectory(dir: String?, model: ModifiableRootModel?, arendModuleConfigService: ArendModuleConfigService?, directoryType: DirectoryType) {
    if (!dir.isNullOrEmpty()) {
        val dirFile = File(arendModuleConfigService?.root?.path + File.separator + dir)
        if (!dirFile.exists()) {
            dirFile.mkdirs()
        }

        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dirFile)?.let {
            addNewFolder(it, model, directoryType)
        }
    }
}

internal fun isNotOtherDirectoryType(e: AnActionEvent): Boolean {
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()
    if (files.size != 1) {
        return false
    }
    val dir = files[0]
    val module = e.getData(LangDataKeys.MODULE)
    val arendModuleConfigService = ArendModuleConfigService.getInstance(module)

    val localFileSystem = LocalFileSystem.getInstance()
    return ApplicationManager.getApplication().executeOnPooledThread<Boolean> {
        val (sourceDir, testDir, binariesDirectory) = triple(localFileSystem, arendModuleConfigService)

        when (dir) {
            sourceDir, testDir, binariesDirectory -> false
            else -> true
        }
    }.get()
}

private fun triple(
    localFileSystem: LocalFileSystem,
    arendModuleConfigService: ArendModuleConfigService?
): Triple<VirtualFile?, VirtualFile?, VirtualFile?> {
    val sourceDir =
        localFileSystem.refreshAndFindFileByIoFile(File(arendModuleConfigService?.root?.path + File.separator + arendModuleConfigService?.sourcesDir))
    val testDir =
        localFileSystem.refreshAndFindFileByIoFile(File(arendModuleConfigService?.root?.path + File.separator + arendModuleConfigService?.testsDir))
    val binariesDirectory =
        localFileSystem.refreshAndFindFileByIoFile(File(arendModuleConfigService?.root?.path + File.separator + arendModuleConfigService?.binariesDirectory))
    return Triple(sourceDir, testDir, binariesDirectory)
}

internal fun hasSpecialDirectories(e: AnActionEvent): Boolean {
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: emptyArray()
    val module = e.getData(LangDataKeys.MODULE)
    val arendModuleConfigService = ArendModuleConfigService.getInstance(module)

    val localFileSystem = LocalFileSystem.getInstance()
    return ApplicationManager.getApplication().executeOnPooledThread<Boolean> {
        val (sourceDir, testDir, binariesDirectory) = triple(localFileSystem, arendModuleConfigService)

        var result = false
        for (file in files) {
            when (file) {
                sourceDir, testDir, binariesDirectory -> result = true
                else -> continue
            }
        }
        result
    }.get()
}

internal fun unmarkOldDirectory(e: AnActionEvent, model: ModifiableRootModel?, arendModuleConfigService: ArendModuleConfigService?, directoryType: DirectoryType) {
    val files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
    val virtualFile = files[0]

    val relativePath = getRelativePath(arendModuleConfigService, virtualFile)

    removeOldFolder(virtualFile, model, arendModuleConfigService, directoryType)
    if (relativePath != null) {
        when (directoryType) {
            SRC -> arendModuleConfigService?.updateSourceDirFromIDEA(relativePath)
            TEST_SRC -> arendModuleConfigService?.updateTestDirFromIDEA(relativePath)
            BIN -> arendModuleConfigService?.updateBinDirFromIDEA(relativePath)
        }
    }
    commitModel(arendModuleConfigService?.module, model)
}

fun unmarkDirectory(e: AnActionEvent, directoryType: DirectoryType) {
    val module = e.getData(LangDataKeys.MODULE)
    val arendModuleConfigService = ArendModuleConfigService.getInstance(module)
    unmarkOldDirectory(
        e,
        arendModuleConfigService?.module?.let { ModuleRootManager.getInstance(it).modifiableModel },
        arendModuleConfigService,
        directoryType
    )
}
