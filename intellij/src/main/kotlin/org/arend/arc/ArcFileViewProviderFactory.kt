package org.arend.arc

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider

// The factory of the Arend language: an .arc view gets an ArcFile, any other file what it gets without a factory
class ArcFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(file: VirtualFile, language: Language?, manager: PsiManager, eventSystemEnabled: Boolean): FileViewProvider =
        if (file is ArcVirtualFile) ArcFileViewProvider(manager, file, eventSystemEnabled)
        else SingleRootFileViewProvider(manager, file, eventSystemEnabled)
}
