package org.arend.documentation

import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.psi.PsiElement
import org.arend.documentation.ArendKeyword.Companion.isArendKeyword
import org.arend.psi.ext.PsiReferable

class ArendDocumentationTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        if (element is PsiReferable || element.isArendKeyword()) {
            return ArendDocumentationTarget(element, originalElement)
        }
        return null
    }
}
