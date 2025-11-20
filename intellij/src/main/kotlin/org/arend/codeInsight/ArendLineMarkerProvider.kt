package org.arend.codeInsight

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.codeInsight.daemon.impl.LineMarkerNavigator
import com.intellij.codeInsight.daemon.impl.MarkerType
import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.naturalSorted
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import org.arend.ArendIcons.MUTUAL_RECURSIVE
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.elimtree.ElimBody
import org.arend.core.elimtree.ElimClause
import org.arend.core.expr.CaseExpression
import org.arend.core.expr.Expression
import org.arend.core.expr.FunCallExpression
import org.arend.core.pattern.ExpressionPattern
import org.arend.core.pattern.Pattern
import org.arend.core.subst.ExprSubstitution
import org.arend.graph.GraphEdge
import org.arend.graph.GraphNode
import org.arend.graph.GraphSimulator
import org.arend.graph.GraphSimulator.Companion.FrameType
import org.arend.graph.call.CallGraphComponentStrongConnectivity
import org.arend.graph.call.getCallMatrix
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.search.ClassDescendantsSearch
import org.arend.server.ArendServerService
import org.arend.term.concrete.Concrete
import org.arend.term.concrete.SearchConcreteVisitor
import org.arend.typechecking.error.TerminationCheckError
import org.arend.typechecking.patternmatching.ExtElimClause
import org.arend.typechecking.subexpr.CorrespondedSubDefVisitor
import org.arend.typechecking.termination.BaseCallGraph
import org.arend.typechecking.termination.BaseCallMatrix
import org.arend.typechecking.termination.CollectCallVisitor
import java.awt.BorderLayout
import java.awt.MouseInfo
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import kotlin.collections.set

class ArendLineMarkerProvider : LineMarkerProviderDescriptor() {
  override fun getName() = "Arend line markers"

  override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

  override fun collectSlowLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    if (elements.isEmpty()) return
    val file = elements.firstOrNull()?.containingFile as? ArendFile
    subclassesLineMarkers(elements, result)
    file?.let { recursiveLineMarkers(it, result, elements) }
  }

  private fun subclassesLineMarkers(elements: List<PsiElement>, result: MutableCollection<in LineMarkerInfo<*>>) {
    for (element in elements) {
      if (element is ArendDefIdentifier) {
        (element.parent as? ArendDefClass)?.let { clazz ->
          ProgressManager.checkCanceled()
          if (clazz.project.service<ClassDescendantsSearch>().search(clazz).isNotEmpty()) {
            result.add(LineMarkerInfo(element.id, element.textRange, AllIcons.Gutter.OverridenMethod,
              SUPERCLASS_OF.tooltip, SUPERCLASS_OF.navigationHandler,
              GutterIconRenderer.Alignment.RIGHT) { "subclasses" })
          }
        }
      }
    }
  }

  private fun recursiveLineMarkers(file: ArendFile, result: MutableCollection<in LineMarkerInfo<*>>, elements: List<PsiElement>) {
    recursiveLineMarkers(file).forEach {
      it.element?.let { id ->
        ProgressManager.checkCanceled()
        if (elements.contains(id)) {
          result.add(it)
        }
      }
    }
  }

  private fun addCoreWithConcrete(definition: Concrete.Definition, typechecked: FunctionDefinition, coreToConcrete: MutableMap<Expression, Concrete.AppExpression>) {
    val functionDefinition = definition as? Concrete.FunctionDefinition
    val subConcreteExpressions = mutableListOf<Concrete.AppExpression>()
    (when (val term = functionDefinition?.body?.term) {
      is Concrete.CaseExpression -> term.clauses
      else -> functionDefinition?.body?.clauses
    } ?: emptyList()).forEach {
      it.expression?.accept(object : SearchConcreteVisitor<Void, Void>() {
        override fun visitApp(expr: Concrete.AppExpression?, params: Void?): Void? {
          expr?.let { app -> subConcreteExpressions.add(app) }
          return super.visitApp(expr, params)
        }
      }, null)
    }
    val subExpressions = mutableListOf<Pair<Expression, Concrete.AppExpression>>()
    for (subExpr in subConcreteExpressions) {
      val subDefVisitor = CorrespondedSubDefVisitor(subExpr)
      val subPair = definition.accept(subDefVisitor, typechecked)
      val subCore = subPair?.proj1 as? FunCallExpression ?: continue
      subExpressions.add(Pair(subCore, subExpr))
    }
    coreToConcrete.putAll(subExpressions)
  }

  private fun getFunctionDefinition(file: ArendFile, functionDefinition: FunctionDefinition, defReferable: TCDefReferable): FunctionDefinition? {
    val server = file.project.service<ArendServerService>().server
    return if (functionDefinition.actualBody != null) {
      functionDefinition
    } else {
      server.errorMap[file.moduleLocation]?.filterIsInstance<TerminationCheckError>()?.find { it.definition == defReferable && it.functionDefinition.actualBody != null }?.functionDefinition
    }
  }

  private fun updateInformation(
    file: ArendFile,
    definition: Concrete.Definition,
    defsToPsiElement: MutableMap<FunctionDefinition, ArendDefFunction>,
    defsClauses:  MutableMap<FunctionDefinition, List<ElimClause<ExpressionPattern>>>,
    coreToConcrete: MutableMap<Expression, Concrete.AppExpression>,
    functionDefinitions: MutableSet<Pair<FunctionDefinition, FunctionDefinition>>
  ) {
    val defReferable = definition.data
    val typechecked = defReferable.typechecked as? FunctionDefinition? ?: return
    val defFunction = defReferable.data as? ArendDefFunction ?: return
    if (!defFunction.isValid) return

    val oldTypechecked = getFunctionDefinition(file, typechecked, defReferable) ?: typechecked
    addCoreWithConcrete(definition, oldTypechecked, coreToConcrete)

    defsToPsiElement[typechecked] = defFunction
    val clauses = getClauses(file, typechecked, defReferable)
    defsClauses.putIfAbsent(typechecked, clauses)
    if (oldTypechecked.body is CaseExpression && typechecked.body == null) {
      functionDefinitions.add(Pair(oldTypechecked, typechecked))
    } else {
      functionDefinitions.add(Pair(typechecked, typechecked))
    }
  }

  private fun getDefinitionCallGraph(
    file: ArendFile,
    definitions: List<Concrete.Definition>,
    defsToPsiElement: MutableMap<FunctionDefinition, ArendDefFunction>,
    coreToConcrete: MutableMap<Expression, Concrete.AppExpression>,
    functionDefinitions: MutableSet<Pair<FunctionDefinition, FunctionDefinition>>,
    isFileGraph: Boolean = false
  ): BaseCallGraph<Definition>? {
    val project = file.project

    val defsClauses = mutableMapOf<FunctionDefinition, List<ElimClause<ExpressionPattern>>>()
    val otherDefs = mutableSetOf<ArendDefFunction>()

    for (definition in definitions) {
      val defReferable = definition.data
      val defFunction = defReferable.data as? ArendDefFunction ?: continue
      if (!defFunction.isValid) continue

      updateInformation(file, definition, defsToPsiElement, defsClauses, coreToConcrete, functionDefinitions)

      otherDefs.addAll(PsiTreeUtil.findChildrenOfType(defFunction, ArendRefIdentifier::class.java)
        .map { it.resolve }.filterIsInstance<ArendDefFunction>().filter { it.isValid }.filter { it.containingFile != file })
    }
    addOtherDefinitions(project, otherDefs, defsClauses, defsToPsiElement, coreToConcrete, functionDefinitions)

    val typecheckedDefinitions = functionDefinitions.map { it.second }.naturalSorted().toMutableList()
    return if (isFileGraph) {
      if (typecheckedDefinitions.isNotEmpty() && fileToTypechecked[file] != typecheckedDefinitions) {
        fileToTypechecked[file] = typecheckedDefinitions
        fileToGraph[file] = getCallGraph(functionDefinitions, defsClauses)
      }
      fileToGraph[file]
    } else {
      if (typecheckedDefinitions.isNotEmpty() && functionDefinitionsToGraph[typecheckedDefinitions] == null) {
        functionDefinitionsToGraph[typecheckedDefinitions] = getCallGraph(functionDefinitions, defsClauses)
      }
      functionDefinitionsToGraph[typecheckedDefinitions]
    }
  }

  private fun addOtherDefinitions(
    project: Project,
    definitions: Set<ArendDefFunction>,
    defsClauses: MutableMap<FunctionDefinition, List<ElimClause<ExpressionPattern>>>,
    defsToPsiElement: MutableMap<FunctionDefinition, ArendDefFunction>,
    coreToConcrete: MutableMap<Expression, Concrete.AppExpression>,
    functionDefinitions: MutableSet<Pair<FunctionDefinition, FunctionDefinition>>
  ) {
    val server = project.service<ArendServerService>().server
    for (definition in definitions) {
      val file = definition.containingFile as? ArendFile? ?: continue
      val concrete = file.moduleLocation?.let { definition.tcReferable?.let { referable -> server.getResolvedDefinition(referable) } }?.definition() as? Concrete.FunctionDefinition ?: continue

      updateInformation(file, concrete, defsToPsiElement, defsClauses, coreToConcrete, functionDefinitions)
    }
  }

  private fun recursiveLineMarkers(file: ArendFile): List<LineMarkerInfo<PsiElement>> {
    val project = file.project

    val defsToPsiElement = mutableMapOf<FunctionDefinition, ArendDefFunction>()
    val coreToConcrete = mutableMapOf<Expression, Concrete.AppExpression>()
    val functionDefinitions = mutableSetOf<Pair<FunctionDefinition, FunctionDefinition>>()

    val server = project.service<ArendServerService>().server
    val moduleLocation = file.moduleLocation ?: return emptyList()
    val dataDefinitions = server.getResolvedDefinitions(moduleLocation)
    var definitions = dataDefinitions.mapNotNull { it.definition as? Concrete.Definition }
    if (definitions.isNotEmpty()) {
      previousDefinitions.put(file, definitions)
    } else {
      definitions = previousDefinitions[file] ?: return emptyList()
    }

    val graph = getDefinitionCallGraph(file, definitions, defsToPsiElement, coreToConcrete, functionDefinitions, true) ?: return emptyList()
    val strongComponents = CallGraphComponentStrongConnectivity(graph).getStronglyConnectedComponents()

    val result = mutableListOf<LineMarkerInfo<PsiElement>>()
    for (strongComponent in strongComponents) {
      var strongDefinitions = strongComponent.mapNotNull { server.getResolvedDefinition(it.referable)?.definition as? Concrete.Definition }
      val sortedStrongComponent = strongComponent.naturalSorted()
      if (strongDefinitions.isNotEmpty()) {
        previousStrongDefinitions.put(sortedStrongComponent, strongDefinitions)
      } else {
        strongDefinitions = previousStrongDefinitions[sortedStrongComponent] ?: continue
      }
      val componentGraph = getDefinitionCallGraph(
        file, strongDefinitions, mutableMapOf(), mutableMapOf(), mutableSetOf()
      ) ?: continue
      val terminationResult = componentGraph.checkTermination()
      val newGraph = terminationResult.proj2
      updateVertexes(newGraph.graph, functionDefinitions)
      for (vertex in strongComponent) {
        val element = defsToPsiElement[vertex] ?: continue
        if (element.containingFile != file) continue
        val entryVertex = graph.graph.entries.find { it.key == vertex } ?: continue
        if (strongComponent.size > 1) {
          element.defIdentifier?.id?.let {
            result.add(LineMarkerInfo(it, it.textRange, MUTUAL_RECURSIVE,
              { "Show the call graph" },
              { _, _ -> mutualRecursiveCall(
                project,
                entryVertex.key.name,
                strongComponent,
                graph.graph,
                newGraph.graph,
                coreToConcrete
              ) },
              GutterIconRenderer.Alignment.CENTER) { "callGraph" })
          }
        } else if (toTextOfCallMatrix(entryVertex).isNotEmpty()) {
          element.defIdentifier?.id?.let {
            result.add(LineMarkerInfo(it, it.textRange, AllIcons.Gutter.RecursiveMethod,
              { "Show the call matrix" }, { _, _ -> selfRecursiveCall(entryVertex) },
              GutterIconRenderer.Alignment.CENTER) { "callMatrix" })
          }
        }
      }
    }
    return result
  }

  private fun getClauses(file: ArendFile, functionDefinition: FunctionDefinition, defReferable: TCDefReferable): List<ElimClause<ExpressionPattern>> {
    val actualBody = getFunctionDefinition(file, functionDefinition, defReferable)?.actualBody
    return ((actualBody as? ElimBody?)?.clauses ?: emptyList())
      .map { ExtElimClause(Pattern.toExpressionPatterns(it.patterns, functionDefinition.parameters), it.expression, ExprSubstitution()) }
  }

  private fun toTextOfCallMatrix(vertex: Map.Entry<Definition, HashMap<Definition, HashSet<BaseCallMatrix<Definition>>>>): String {
    val result = StringBuilder()
    for ((otherVertex, matrices) in vertex.value.entries) {
      if (otherVertex != vertex.key) continue
      result.append(vertex.key.name).append(" -> ").append(otherVertex.name).append("\n ")
      for (matrix in matrices) {
        result.append(matrix.toString()).append("\n")
      }
    }
    return result.removeSuffix("\n").toString()
  }

  private fun getVertices(component: Set<Definition>): Set<GraphNode> {
    return component.mapNotNull { (it.referable.data as? ArendDefFunction?)?.fullNameText }.map { GraphNode(it) }.toSet()
  }

  private fun getNameDefinition(vertex: Definition): String? {
    return (vertex.referable.data as? ArendDefFunction?)?.fullNameText
  }

  private fun getEdges(
    graph: Map<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>,
    component: Set<Definition>
  ): Set<GraphEdge> {
    val edges = mutableSetOf<GraphEdge>()
    for (vertex in component) {
      for ((otherVertex, edgesToOtherVertex) in (graph[vertex] ?: emptyMap())) {
        if (!component.contains(otherVertex)) continue
        for (edge in edgesToOtherVertex) {
          val from = getNameDefinition(vertex) ?: continue
          val to = getNameDefinition(otherVertex) ?: continue
          edges.add(GraphEdge(from, to, edge))
        }
      }
    }
    return edges
  }

  private fun selfRecursiveCall(vertex: Map.Entry<Definition, HashMap<Definition, HashSet<BaseCallMatrix<Definition>>>>) {
    JBPopupFactory.getInstance()
      .createBalloonBuilder(JPanel(BorderLayout()).apply {
        add(JScrollPane(getCallMatrix(toTextOfCallMatrix(vertex))), BorderLayout.NORTH)
        add(JButton("Open the documentation page").apply {
          addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
              BrowserUtil.browse(DOCUMENTATION_URL)
            }
          })
        }, BorderLayout.SOUTH)
      })
      .setHideOnClickOutside(true)
      .createBalloon()
      .show(RelativePoint.fromScreen(MouseInfo.getPointerInfo().location), Balloon.Position.atRight)
  }

  private fun mutualRecursiveCall(
    project: Project,
    vertexName: String,
    component: Set<Definition>,
    graph: Map<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>,
    compositeGraph: Map<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>,
    coreToConcrete: Map<Expression, Concrete.Expression>
  ) {
    project.service<GraphSimulator>().displayOrthogonal(
      "Graph_Call_From_$vertexName",
      getVertices(component),
      getEdges(graph, component),
      getEdges(compositeGraph, component),
      coreToConcrete,
      FrameType.CALL_GRAPH
    )
  }

  private fun updateVertexes(graph: MutableMap<Definition, HashMap<Definition, HashSet<BaseCallMatrix<Definition>>>>, functionDefinitions: Set<Pair<FunctionDefinition, FunctionDefinition>>) {
    for ((functionDefinition, oldFunctionDefinition) in functionDefinitions) {
      if (functionDefinition != oldFunctionDefinition) {
        val value = graph.remove(functionDefinition) ?: continue
        graph.put(oldFunctionDefinition, value)
      }
    }
  }

  private fun getCallGraph(
    functionDefinitions: Set<Pair<FunctionDefinition, FunctionDefinition>>,
    clauses: Map<FunctionDefinition, List<ElimClause<ExpressionPattern>>>
  ): BaseCallGraph<Definition> {
    val cycle = functionDefinitions.map { it.second }.toSet()
    val definitionCallGraph = BaseCallGraph<Definition>()
    for ((functionDefinition, _) in functionDefinitions) {
      val functionClauses = clauses[functionDefinition]
      definitionCallGraph.add(CollectCallVisitor.collectCalls(functionDefinition, functionClauses ?: emptyList(), cycle))
    }
    updateVertexes(definitionCallGraph.graph, functionDefinitions)
    return definitionCallGraph
  }

  companion object {
    private val SUPERCLASS_OF = MarkerType("SUPERCLASS_OF", { "Is overridden by several subclasses" },
      object : LineMarkerNavigator() {
        override fun browse(e: MouseEvent, element: PsiElement) {
          val clazz = element.parent.parent as? ArendDefClass ?: return
          PsiTargetNavigator(clazz.project.service<ClassDescendantsSearch>().getAllDescendants(clazz).toTypedArray()).navigate(e, "Subclasses of " + clazz.name, element.project)
        }
      })

    const val DOCUMENTATION_URL = "https://arend-lang.github.io/documentation/language-reference/term-checker"

    private val previousDefinitions = ConcurrentHashMap<ArendFile, List<Concrete.Definition>>()
    private val previousStrongDefinitions = ConcurrentHashMap<List<Definition>, List<Concrete.Definition>>()
    private val fileToGraph = ConcurrentHashMap<ArendFile, BaseCallGraph<Definition>>()
    private val fileToTypechecked = ConcurrentHashMap<ArendFile, MutableList<FunctionDefinition>>()
    private val functionDefinitionsToGraph = ConcurrentHashMap<MutableList<FunctionDefinition>, BaseCallGraph<Definition>>()
  }
}
