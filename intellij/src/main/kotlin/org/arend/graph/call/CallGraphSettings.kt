package org.arend.graph.call

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendDefFunction

@Service(Service.Level.PROJECT)
class CallGraphSettings(private val project: Project) {
  val usedDefFunctions = mutableMapOf<ArendFile, MutableSet<ArendDefFunction>>()

  init {
    val connection = project.messageBus.connect()
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val arendFile = PsiManager.getInstance(project).findFile(file) as? ArendFile ?: return
        usedDefFunctions.remove(arendFile)
      }

      override fun selectionChanged(event: FileEditorManagerEvent) {
        val oldArendFile = event.oldFile?.let { PsiManager.getInstance(project).findFile(it) } as? ArendFile ?: return
        val newArendFile = event.newFile?.let { PsiManager.getInstance(project).findFile(it) } as? ArendFile ?: return
        usedDefFunctions.remove(oldArendFile)
        usedDefFunctions.remove(newArendFile)
      }
    })

    val editorFactory = EditorFactory.getInstance()
    editorFactory.eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        val document = event.document
        val virtualFile = FileDocumentManager.getInstance().getFile(document)
        val arendFile = virtualFile?.let { PsiManager.getInstance(project).findFile(it) } as? ArendFile ?: return
        usedDefFunctions.remove(arendFile)
      }
    }, project)
  }
}
