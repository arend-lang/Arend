package org.arend.documentation

import com.intellij.model.Pointer
import com.intellij.platform.backend.documentation.*
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.ArendIcons
import org.arend.psi.ext.PsiReferable

class ArendDocumentationTarget(
    val element: PsiElement,
    private val originalElement: PsiElement?
) : DocumentationTarget {

    private val smartPointer: SmartPsiElementPointer<PsiElement> =
        SmartPointerManager.getInstance(element.project).createSmartPsiElementPointer(element)

    private val smartOriginalPointer: SmartPsiElementPointer<PsiElement>? =
        originalElement?.let { SmartPointerManager.getInstance(it.project).createSmartPsiElementPointer(it) }

    override fun createPointer(): Pointer<out DocumentationTarget> {
        val elementPtr = smartPointer
        val originalElementPtr = smartOriginalPointer
        return Pointer {
            val element = elementPtr.element ?: return@Pointer null
            ArendDocumentationTarget(element, originalElementPtr?.element)
        }
    }

    override fun computePresentation(): TargetPresentation {
        val icon = (element as? PsiReferable)?.getIcon(0) ?: ArendIcons.AREND
        return TargetPresentation.builder(element.text)
            .icon(icon)
            .presentation()
    }

    override fun computeDocumentation(): DocumentationResult? {
        val doc = ArendDocumentationGenerator.generateDoc(element, originalElement) ?: return null
        return DocumentationResult.documentation(doc)
    }
}
