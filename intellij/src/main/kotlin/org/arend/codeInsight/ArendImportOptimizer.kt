package org.arend.codeInsight

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.components.service
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.server.ArendServerService
import org.arend.server.imports.ImportUsageData
import org.arend.util.ArendBundle

class ArendImportOptimizer : ImportOptimizer {

    override fun supports(file: PsiFile): Boolean = file is ArendFile && file.isWritable

    override fun processFile(file: PsiFile): Runnable {
        if (file !is ArendFile) return EmptyRunnable.getInstance()
        val findings = getUnusedImports(file) ?: return EmptyRunnable.getInstance()
        return optimizationRunnable(file, findings)
    }

    internal fun optimizationRunnable(file: ArendFile, findings: List<ImportUsageData.Part>) =
        object : ImportOptimizer.CollectingInfoRunnable {
            override fun run() {
                removeFindings(file, findings)
                sortFileImports(file)
            }

            override fun getUserNotificationInfo(): String =
                ArendBundle.message("arend.optimize.imports.message.core.used")
        }
}

/**
 * @return the commands and names of the file of [group] that nothing needs, or null when the
 *         server cannot say -- a module it does not know, or one not yet typechecked, where an
 *         import looks superfluous only because what needs it has not been checked yet.
 */
fun getUnusedImports(group: ArendGroup): List<ImportUsageData.Part>? {
    val file = group.containingFile as? ArendFile ?: return null
    val module = file.moduleLocation ?: return null
    return file.project.service<ArendServerService>().server.getNamespaceCommandUsage(module)?.unusedParts
}

fun removeUnusedImports(group: ArendGroup) {
    val file = group.containingFile as? ArendFile ?: return
    val findings = getUnusedImports(group) ?: return
    removeFindings(file, findings.filter {
        val element = it.data() as? PsiElement ?: return@filter false
        PsiTreeUtil.isAncestor(group, element, false)
    })
}

fun removeFindings(file: ArendFile, findings: List<ImportUsageData.Part>) {
    for (finding in findings) {
        when (val element = finding.data()) {
            is ArendNsId -> sameIn(file, element, ArendNsId::class.java)?.let { removeEntry(it) }
            is ArendStatCmd -> sameIn(file, element, ArendStatCmd::class.java)?.let { removeCommand(it) }
        }
    }
}

/**
 * The findings describe the file the server holds, which need not be the one being edited -- the
 * IDE reparses as it is typed in. Matching by text range picks the same element out of [file],
 * and matching the text as well rejects an unrelated one that an edit has moved into that range.
 */
private fun <T : PsiElement> sameIn(file: ArendFile, element: T, clazz: Class<T>): T? {
    if (element.containingFile == file) return element
    val range = element.textRange
    return PsiTreeUtil.findElementOfClassAtRange(file, range.startOffset, range.endOffset, clazz)?.takeIf { it.text == element.text }
}

private fun removeEntry(nsId: ArendNsId) {
    val using = nsId.parent as? ArendNsUsing
    // a \using list only adds renamings to what the command brings in anyway, so once its last
    // name goes it adds nothing: the command stays, as \import X rather than \import X \using ()
    if (using?.usingKw != null && using.nsIdList.singleOrNull() == nsId) {
        (using.prevSibling as? PsiWhiteSpace)?.delete()
        using.delete()
        return
    }
    val nextComma = nsId.findNextSibling()?.takeIf { it.elementType == ArendElementTypes.COMMA }
    val prevComma = nsId.findPrevSibling()?.takeIf { it.elementType == ArendElementTypes.COMMA }
    // an explicit list none of whose names is used is reported as a whole command, so a name
    // always remains here and one of the two commas is the one to drop
    if (nextComma != null) nextComma.delete() else prevComma?.delete()
    nsId.delete()
}

private fun removeCommand(command: ArendStatCmd) {
    val statement = command.parentOfType<ArendStat>() ?: return
    // a \where holding nothing but this command has nothing left to hold
    val singularWhere = statement.parentOfType<ArendWhere>()?.takeIf { it.statList.singleOrNull() == statement }
    statement.delete()
    singularWhere?.delete()
}

private fun sortFileImports(file: ArendFile) {
    val factory = ArendPsiFactory(file.project)
    val imports = file.statements.filter { it.statCmd?.isImport == true }.map {
        val text = it.text
        it.delete()
        factory.createFromText(text)!!.statements[0]
    }.sortedByDescending { it.text }
    imports.forEach { file.addBefore(it, file.firstChild) }
}
