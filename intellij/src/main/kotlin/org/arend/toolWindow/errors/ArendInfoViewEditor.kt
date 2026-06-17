package org.arend.toolWindow.errors

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.ui.jcef.JBCefBrowser
import org.arend.documentation.ArendDocumentationGenerator
import org.arend.documentation.ArendDocumentationGenerator.addLinkHandlerToBrowser
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.UIManager

class ArendInfoViewEditor(project: Project) : ArendMessagesViewEditor(project, null, EditorType.INFO) {
    private val browser = JBCefBrowser()
    private var lastHtml = ""
    private var fontSize = (UIManager.getDefaults().getFont("Label.font")?.size?.times(ArendDocumentationGenerator.COEFFICIENT_HTML_FONT))?.toInt() ?: 15

    private var lastElement: Pair<PsiElement, PsiElement?>? = null

    init {
        browser.component.preferredSize = Dimension(1, 1)
        addLinkHandlerToBrowser(browser)
        addEditorComponent()
    }

    override fun addEditorComponent() {
        component?.add(browser.component, BorderLayout.CENTER)
    }

    fun zoomIn() = changeFontSize(1.0)
    fun zoomOut() = changeFontSize(-1.0)

    private fun updateHtml(html: String) {
        if (lastHtml != html) {
            lastHtml = html
            browser.loadHTML(html)
            browser.component.repaint()
        }
    }

    fun updateHtml(element: Pair<PsiElement, PsiElement?>, showNotification: Boolean) {
        lastElement = element
        val background = component?.background
        val foreground = component?.foreground
        ApplicationManager.getApplication().executeOnPooledThread {
            val html = runReadAction {
                ArendDocumentationGenerator.generateDoc(element.first, element.second, true, fontSize, false, background, foreground, showNotification)
            } ?: return@executeOnPooledThread
            updateHtml(html)
        }
    }

    private fun changeFontSize(shift: Double) {
        fontSize += shift.toInt()
        if (fontSize <= 0) fontSize = 1
        lastElement?.let { updateHtml(it, showNotification = false) }
    }
}
