package org.arend.graph

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import org.arend.ext.module.ModuleLocation
import org.arend.hierarchy.clazz.ArendClassHierarchyBrowser.Companion.findClassNodesAndEdges
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendDefClass
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.fullName
import org.arend.psi.ext.fullNameText
import org.arend.psi.stubs.index.ArendDefinitionIndex
import java.io.File

fun generateArendLibClassGraph(
  psiProject: Project,
  pathToArendLibInArendSite: String,
  classes: List<String>,
  versionArendLib: String
) {
  DumbService.getInstance(psiProject).runWhenSmart {
    runReadAction {
      val baseClasses = mutableSetOf<ArendDefClass>()
      val stubIndex = StubIndex.getInstance()
      val path = if (classes.isEmpty()) {
        println("Generate a hierarchical graph for all classes")
        stubIndex.getAllKeys(ArendDefinitionIndex.KEY, psiProject)
          .forEach { k ->
            stubIndex.processElements(ArendDefinitionIndex.KEY, k, psiProject, GlobalSearchScope.allScope(psiProject), PsiReferable::class.java) { referable ->
              if (referable is ArendDefClass && referable.superClasses.isEmpty() && (referable.containingFile as? ArendFile)?.moduleLocation?.locationKind != ModuleLocation.LocationKind.TEST) {
                baseClasses.add(referable)
              }
              true
            }
          }
        "Classes-$versionArendLib.svg"
      } else {
        for (className in classes) {
          println("Generate a hierarchical graph for $className")
          val foundClases = StubIndex.getElements(ArendDefinitionIndex.KEY, className, psiProject, GlobalSearchScope.allScope(psiProject), PsiReferable::class.java).filterIsInstance<ArendDefClass>()
          if (foundClases.isEmpty()) {
            println("Class $className not found")
          } else if (foundClases.size > 1) {
            println("More than one class with name $className found")
            for (defClass in foundClases) {
              if ((defClass.containingFile as? ArendFile)?.moduleLocation?.locationKind != ModuleLocation.LocationKind.TEST) {
                baseClasses.add(defClass)
              } else {
                println("Class $className is a test class. It can't be used in the graph generation.")
              }
            }
          } else {
            val defClass = foundClases.first()
            if ((defClass.containingFile as? ArendFile)?.moduleLocation?.locationKind != ModuleLocation.LocationKind.TEST) {
              baseClasses.add(defClass)
            } else {
              println("Class $className is a test class. It can't be used in the graph generation.")
            }
          }
        }
        "Classes-${baseClasses.joinToString("-") { it.fullNameText }}-$versionArendLib.svg"
      }
      val indexFile = File(pathToArendLibInArendSite + File.separator + "index.md")
      indexFile.appendText("[Class graph](/assets/images/$path)\n")

      val usedNodes = mutableSetOf<ArendDefClass>()
      val edges = mutableSetOf<GraphEdge>()
      for (baseClass in baseClasses) {
        findClassNodesAndEdges(psiProject, baseClass, false, usedNodes, edges)
      }
      val graphSimulator = psiProject.service<GraphSimulator>()
      val file = File("$pathToArendLibInArendSite/../assets/images/$path")
      val svgImage = graphSimulator.getSvg(graphSimulator.getGraphComponent(usedNodes.map { GraphNode(it.fullName.toString(), it.fullNameText) }.toSet(), edges, true).first)
      file.writeText(svgImage)

      Runtime.getRuntime().halt(0)
    }
  }
}
