package org.arend.hierarchy.clazz

import com.intellij.find.findUsages.DefaultFindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.HierarchyTreeStructure
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageInfo
import com.intellij.util.CommonProcessors
import org.arend.hierarchy.ArendHierarchyNodeDescriptor
import org.arend.psi.ArendDefClass
import org.arend.psi.ArendLongName

class ArendSubClassTreeStructure(project: Project, baseNode: PsiElement) :
        HierarchyTreeStructure(project, ArendHierarchyNodeDescriptor(project, null, baseNode, true)) {
    override fun buildChildren(descriptor: HierarchyNodeDescriptor): Array<Any> {
        val classElement = descriptor.psiElement as? ArendDefClass ?: return emptyArray()
        val subClasses = getSubclasses(classElement)
        val result = ArrayList<ArendHierarchyNodeDescriptor>()

        subClasses.mapTo(result) { ArendHierarchyNodeDescriptor(myProject, descriptor, it, false) }
        return result.toArray()
    }

    private fun getSubclasses(clazz: ArendDefClass): Set<PsiElement> {
        val finder = DefaultFindUsagesHandlerFactory().createFindUsagesHandler(clazz, false)
        val processor = CommonProcessors.CollectProcessor<UsageInfo>()
        val options = FindUsagesOptions(myProject)
        val subClasses = HashSet<PsiElement>()
        options.isUsages = true
        options.isSearchForTextOccurrences = false
        //if (clazz != null) {
        finder?.processElementUsages(clazz, processor, options)
        //}
        for (usage in processor.results) {
            if (usage.element?.parent is ArendLongName && usage.element?.parent?.parent is ArendDefClass) {
                val parentLongName = usage.element?.parent as ArendLongName
                if (parentLongName.refIdentifierList.last().text == clazz.name) {
                    val subclass = usage.element?.parent?.parent as ArendDefClass
                    //if (subclass.name != classElement.name) {
                    subClasses.add(subclass)
                    //}
                }
            }
        }
        return subClasses
    }
}