package org.arend.highlight

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.descendantsOfType
import org.arend.IArendFile
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.ArendDefIdentifier
import org.arend.search.ClassDescendantsSearch

class ArendSubclassesPass(file: IArendFile, editor: Editor, textRange: TextRange)
    : BasePass(file, editor, "Arend subclasses annotator", textRange) {

    override fun collectInformationWithProgress(indicator: ProgressIndicator) {
        val defIdentifiers = file.descendantsOfType<ArendDefIdentifier>().filter { it.parent is ArendDefClass }
        val newDefIdentifiers = defIdentifiers.filter { it.getUserData(ArendSubclassesKey) == null }.toList()
        if (newDefIdentifiers.isNotEmpty()) {
            val project = file.project
            for (newDefIdentifier in newDefIdentifiers) {
                indicator.checkCanceled()
                val hasSubclasses = runReadAction {
                    val clazz = newDefIdentifier.parent as ArendDefClass
                    if (clazz.isValid) {
                        project.service<ClassDescendantsSearch>().search(clazz).isNotEmpty()
                    } else {
                        false
                    }
                }
                if (hasSubclasses) {
                    newDefIdentifier.putUserData(ArendSubclassesKey, true)
                } else {
                    newDefIdentifier.putUserData(ArendSubclassesKey, false)
                }
            }
            if (!ApplicationManager.getApplication().isUnitTestMode) {
                DaemonCodeAnalyzer.getInstance(file.project).restart(file, newDefIdentifiers)
            }
        }
    }

    companion object {
        object ArendSubclassesKey : Key<Boolean>("AREND_SUBCLASSES_KEY")
    }
}
