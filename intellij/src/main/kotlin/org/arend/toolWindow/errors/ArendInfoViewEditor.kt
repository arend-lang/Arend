package org.arend.toolWindow.errors

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.ui.jcef.JBCefBrowser
import org.arend.documentation.ArendDocumentationGenerator
import org.arend.documentation.ArendDocumentationGenerator.addLinkHandlerToBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandler
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.UIManager

/**
 * Traces how the info panel follows the caret into comments: the editor events, the comment found at the caret,
 * and what becomes of each rendering. Enable it in Help | Diagnostic Tools | Debug Log Settings with `#org.arend.infoView`.
 */
internal val INFO_VIEW_LOG = Logger.getInstance("#org.arend.infoView")

internal val PsiElement?.traceText: String
    get() = if (this == null) "null" else "$elementType$textRange '${text.take(40).replace("\n", "\\n")}'"

private val LATEX_IMAGE_REGEX = "src=\"file:///([^\"]*)\"".toRegex()

class ArendInfoViewEditor(project: Project) : ArendMessagesViewEditor(project, null, EditorType.INFO) {
    private val browser = JBCefBrowser()
    @Volatile
    private var lastHtml = ""
    private var fontSize = (UIManager.getDefaults().getFont("Label.font")?.size?.times(ArendDocumentationGenerator.COEFFICIENT_HTML_FONT))?.toInt() ?: 15

    private var lastElement: Pair<PsiElement, PsiElement?>? = null
    private val lastRequest = AtomicInteger()

    init {
        browser.component.preferredSize = Dimension(1, 1)
        addLinkHandlerToBrowser(browser)
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) INFO_VIEW_LOG.debug {
                    // The formulas are files that the next rendering deletes, so they may be gone before the page has loaded them
                    val missing = LATEX_IMAGE_REGEX.findAll(lastHtml).map { it.groupValues[1] }.filterNot { File(it).exists() }.toList()
                    "browser: page loaded (status $httpStatusCode)" + if (missing.isEmpty()) "" else ", missing formula images: $missing"
                }
            }

            override fun onLoadError(browser: CefBrowser?, frame: CefFrame?, errorCode: CefLoadHandler.ErrorCode?, errorText: String?, failedUrl: String?) {
                INFO_VIEW_LOG.debug { "browser: failed to load ${failedUrl?.take(100)}: $errorCode $errorText" }
            }
        }, browser.cefBrowser)
        addEditorComponent()
    }

    override fun addEditorComponent() {
        component?.add(browser.component, BorderLayout.CENTER)
    }

    override fun release() {
        super.release()
        Disposer.dispose(browser)
    }

    fun zoomIn() = changeFontSize(1.0)
    fun zoomOut() = changeFontSize(-1.0)

    private fun updateHtml(html: String, request: Int) = synchronized(this) {
        // Renderings run concurrently, so an older one may finish after a newer one has been shown
        val latest = lastRequest.get()
        if (request != latest) {
            INFO_VIEW_LOG.debug { "render #$request: dropped, #$latest is newer" }
        } else if (lastHtml == html) {
            INFO_VIEW_LOG.debug { "render #$request: the page has not changed" }
        } else {
            INFO_VIEW_LOG.debug { "render #$request: loading the page" }
            lastHtml = html
            browser.loadHTML(html)
            browser.component.repaint()
        }
    }

    fun updateHtml(element: Pair<PsiElement, PsiElement?>, showNotification: Boolean) {
        lastElement = element
        val background = component?.background
        val foreground = component?.foreground
        val request = lastRequest.incrementAndGet()
        INFO_VIEW_LOG.debug { "render #$request requested for ${element.first.traceText}, showNotification=$showNotification" }
        ApplicationManager.getApplication().executeOnPooledThread {
            val start = System.currentTimeMillis()
            val html = try {
                runReadAction {
                    if (element.first.isValid) {
                        ArendDocumentationGenerator.generateDoc(element.first, element.second, true, fontSize, false, background, foreground, showNotification)
                    } else {
                        INFO_VIEW_LOG.debug { "render #$request: the element is no longer valid" }
                        null
                    }
                }
            } catch (e: ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                INFO_VIEW_LOG.error("render #$request failed", e)
                null
            } ?: run {
                INFO_VIEW_LOG.debug { "render #$request: nothing to show" }
                return@executeOnPooledThread
            }
            INFO_VIEW_LOG.debug {
                val formulas = LATEX_IMAGE_REGEX.findAll(html).count()
                val incorrect = html.split("Incorrect latex formula").size - 1
                "render #$request: ${html.length} characters, $formulas formulas, $incorrect incorrect, in ${System.currentTimeMillis() - start} ms"
            }
            updateHtml(html, request)
        }
    }

    private fun changeFontSize(shift: Double) {
        fontSize += shift.toInt()
        if (fontSize <= 0) fontSize = 1
        lastElement?.let { updateHtml(it, showNotification = false) }
    }
}
