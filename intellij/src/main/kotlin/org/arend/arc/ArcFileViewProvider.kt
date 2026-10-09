package org.arend.arc

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider
import org.arend.ArendLanguage

class ArcFileViewProvider(manager: PsiManager, virtualFile: VirtualFile, eventSystemEnabled: Boolean = true) :
    SingleRootFileViewProvider(manager, virtualFile, eventSystemEnabled, ArendLanguage.INSTANCE) {

    // The PSI of a text file is created by the parser definition of its language, which would give an ArendFile
    override fun createFile(lang: Language): PsiFile? =
        if (lang == ArendLanguage.INSTANCE) ArcFile(this) else super.createFile(lang)

    override fun createCopy(copy: VirtualFile) = ArcFileViewProvider(manager, copy, false)
}
