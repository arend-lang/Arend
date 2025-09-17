package org.arend.hierarchy.clazz

import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.ide.hierarchy.TypeHierarchyBrowserBase
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.definition.ClassDefinition
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.hierarchy.clazz.ArendClassHierarchyBrowser.Companion.getSuperDefClass
import org.arend.psi.ext.ArendDefClass
import org.arend.settings.ArendProjectSettings
import java.util.*

class ArendSuperClassTreeStructure(project: Project, baseNode: PsiElement, private val browser: ArendClassHierarchyBrowser) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {

    companion object {
        private fun getImplFields(classElement: ArendDefClass): List<PsiElement> {
            return classElement.coClauseElements.mapNotNull { it.longName?.resolve }
        }

        fun getChildren(descriptor: HierarchyNodeDescriptor, project: Project): Array<ArendHierarchyNodeDescriptor> {
            val classElement = descriptor.psiElement as? ArendDefClass ?: return emptyArray()
            val result = ArrayList<ArendHierarchyNodeDescriptor>()
            classElement.superClassList.mapNotNullTo(result) { getSuperDefClass(it)?.let { element -> ArendHierarchyNodeDescriptor(project, descriptor, element, false) } }
            val settings = project.service<ArendProjectSettings>().data
            val classDefinition = classElement.tcReferable?.typechecked as? ClassDefinition ?: return result.toTypedArray()
            if (settings.showImplFields) {
                getImplFields(classElement).mapTo(result) { ArendFieldHNodeDescriptor(project, descriptor, it, isBase = false, isImplemented = true) }
            }
            if (settings.showNonImplFields) {
                if (descriptor.parentDescriptor == null) {
                    classDefinition.notImplementedFields.mapNotNull { it.referable.data as? PsiElement }.mapTo(result) { ArendFieldHNodeDescriptor(project, descriptor, it, isBase = false, isImplemented = false) }
                } else {
                    val implFields = HashSet<PsiElement>()
                    implInAncestors(descriptor, implFields)
                    classElement.internalReferables.mapNotNullTo(result) {
                        if (!implFields.contains(it))
                            ArendFieldHNodeDescriptor(project, descriptor, it, isBase = false, isImplemented = false)
                        else null
                    }
                }
            }
            return result.toTypedArray()
        }

        private fun implInAncestors(descriptor: HierarchyNodeDescriptor, implFields: MutableSet<PsiElement>) {
            val classElement = descriptor.psiElement as? ArendDefClass ?: return
            implFields.addAll(getImplFields(classElement))
            if (descriptor.parentDescriptor != null) {
                implInAncestors(descriptor.parentDescriptor as ArendHierarchyNodeDescriptor, implFields)
            }
        }
    }

    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<out Any> {
        val children = getChildren(descriptor, myProject)
        return browser.buildChildren(children, TypeHierarchyBrowserBase.getSupertypesHierarchyType())
    }
}