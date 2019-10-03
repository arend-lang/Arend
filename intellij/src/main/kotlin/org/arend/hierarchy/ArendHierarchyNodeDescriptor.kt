package org.arend.hierarchy

import com.intellij.icons.AllIcons
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.psi.PsiElement
import com.intellij.ui.RowIcon
import org.arend.naming.reference.FieldReferable
import org.arend.psi.ArendClassImplement
import org.arend.psi.ArendDefClass
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.parentOfType
import org.arend.util.FullName
import javax.swing.Icon

class ArendHierarchyNodeDescriptor(project: Project, parent: HierarchyNodeDescriptor?,
                                   element: PsiElement, isBase: Boolean) : HierarchyNodeDescriptor(project, parent, element, isBase) {

    override fun update(): Boolean {
        val oldText = myHighlightedText

        if (myHighlightedText.ending.appearance.text == "") when (val element = psiElement) {
            is FieldReferable -> {
                val fullName = FullName(element)
                val clazz = (parentDescriptor as? ArendHierarchyNodeDescriptor)?.psiElement as? ArendDefClass
                if (parentDescriptor?.parentDescriptor != null || clazz?.fieldReferables?.contains(element) == true) {
                    myHighlightedText.ending.addText(fullName.longName.lastName.toString())
                } else {
                    myHighlightedText.ending.addText(fullName.longName.toString())
                    myHighlightedText.ending.addText(" (" + fullName.modulePath + ')', getPackageNameAttributes())
                }
            }
            is ArendClassImplement -> {
                val name = element.longName
                val clazz = element.parentOfType<ArendDefClass>()
                val ref = name.refIdentifierList.lastOrNull()?.reference?.resolve()
                if (ref is FieldReferable && clazz != null && !clazz.fieldReferables.contains<Any>(ref)) {
                    val fullName = FullName(ref)
                    myHighlightedText.ending.addText(fullName.longName.toString())
                    myHighlightedText.ending.addText(" (" + fullName.modulePath + ')', getPackageNameAttributes())
                } else {
                    myHighlightedText.ending.addText(name.text)
                }
            }
            is PsiLocatedReferable -> {
                val fullName = FullName(element)
                myHighlightedText.ending.addText(fullName.longName.toString())
                myHighlightedText.ending.addText(" (" + fullName.modulePath + ')', getPackageNameAttributes())
            }
        }

        return !Comparing.equal(myHighlightedText, oldText) || super.update()
    }

    override fun getIcon(element: PsiElement): Icon? {
        val baseIcon = super.getIcon(element)
        return when (element) {
            is ArendClassImplement -> RowIcon(AllIcons.Hierarchy.MethodDefined, baseIcon)
            is FieldReferable -> RowIcon(AllIcons.Hierarchy.MethodNotDefined, baseIcon)
            else -> baseIcon
        }
    }

    companion object {
        fun nodePath(node: ArendHierarchyNodeDescriptor): String {
            val parent = node.parentDescriptor?.let { nodePath(it as ArendHierarchyNodeDescriptor) } ?: ""
            return parent + node.highlightedText.ending.appearance.text
        }
    }

}