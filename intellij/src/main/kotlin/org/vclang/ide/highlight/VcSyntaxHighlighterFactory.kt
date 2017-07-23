package org.vclang.ide.highlight

import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class VcSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(
            project: Project?,
            virtualFile: VirtualFile?
    ) = VcSyntaxHighlighter()
}
