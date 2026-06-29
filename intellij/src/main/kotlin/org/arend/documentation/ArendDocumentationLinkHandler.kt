package org.arend.documentation

import com.intellij.util.ui.JBUI
import com.intellij.platform.backend.documentation.DocumentationLinkHandler
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.LinkResolveResult

class ArendDocumentationLinkHandler : DocumentationLinkHandler {
    override fun resolveLink(target: DocumentationTarget, url: String): LinkResolveResult? {
        if (target !is ArendDocumentationTarget) return null
        val element = target.element
        
        if (url.contains(ACTION_PREFIX)) {
            ArendDocumentationGenerator.showInCefBrowser(url.substringAfter(ACTION_PREFIX), getHtmlRgbFormat(JBUI.CurrentTheme.Link.Foreground.ENABLED.rgb), getHtmlRgbFormat(JBUI.CurrentTheme.Link.Foreground.HOVERED.rgb))
            return null
        }
        
        val resolved = ArendDocumentationGenerator.getDocumentationElementForLink(url, element)
            ?: return null
        
        return LinkResolveResult.resolvedTarget(ArendDocumentationTarget(resolved, element))
    }
}
