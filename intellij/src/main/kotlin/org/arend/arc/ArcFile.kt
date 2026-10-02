package org.arend.arc

import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.FileViewProvider
import org.arend.psi.ArendFile

class ArcFile(viewProvider: FileViewProvider) : ArendFile(viewProvider) {
    // reloadBinaryFiles re-decompiles into the existing document, and a binary document is never
    // committed into PSI, so the tree can be older than the document; follow the document instead of
    // failing the platform's text-mismatch assertion (see ArcTest)
    override fun getText(): String {
        return virtualFile.findDocument()?.text ?: ""
    }
    override fun getTextLength(): Int {
        return virtualFile.findDocument()?.text?.length ?: 0
    }
}
