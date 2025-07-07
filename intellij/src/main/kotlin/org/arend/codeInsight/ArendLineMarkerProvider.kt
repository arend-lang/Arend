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
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.awt.RelativePoint
import org.arend.ArendIcons.MUTUAL_RECURSIVE
import org.arend.core.definition.Definition
import org.arend.core.definition.FunctionDefinition
import org.arend.core.elimtree.ElimBody
import org.arend.core.elimtree.ElimClause
import org.arend.core.expr.Expression
import org.arend.core.expr.FunCallExpression
import org.arend.core.pattern.ExpressionPattern
import org.arend.core.pattern.Pattern
import org.arend.core.subst.ExprSubstitution
import org.arend.error.DummyErrorReporter
import org.arend.graph.GraphEdge
import org.arend.graph.GraphNode
import org.arend.graph.GraphSimulator
import org.arend.graph.GraphSimulator.Companion.FrameType
import org.arend.graph.call.getCallMatrix
import org.arend.naming.reference.TCDefReferable
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.search.ClassDescendantsSearch
import org.arend.server.ArendServer
import org.arend.server.ArendServerService
import org.arend.server.ProgressReporter
import org.arend.server.impl.DefinitionData
import org.arend.term.concrete.Concrete
import org.arend.typechecking.computation.UnstoppableCancellationIndicator
import org.arend.typechecking.error.TerminationCheckError
import org.arend.typechecking.patternmatching.ExtElimClause
import org.arend.typechecking.subexpr.CorrespondedSubDefVisitor
import org.arend.typechecking.termination.BaseCallMatrix
import org.arend.typechecking.termination.DefinitionCallGraph
import org.arend.util.ArendBundle
import java.awt.MouseInfo
import java.awt.event.MouseEvent
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JScrollPane

class ArendLineMarkerProvider : LineMarkerProviderDescriptor() {
  private val url = "https://www.cse.chalmers.se/~abela/foetus.pdf"

  val previousDefinitions = ConcurrentHashMap<ArendFile, Set<DefinitionData>>()

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

  private fun getAllConcreteSubAppExpression(expression: Concrete.Expression): List<Concrete.AppExpression> {
    return when (expression) {
      is Concrete.AppExpression -> {
        val result = mutableListOf<Concrete.AppExpression>()
        for (argument in expression.arguments) {
          result.addAll(getAllConcreteSubAppExpression(argument.expression))
        }
        result + expression
      }
      else -> emptyList()
    }
  }

  private fun addCoreWithConcrete(concrete: Concrete.Definition, typechecked: Definition, coreToConcrete: MutableMap<Expression, Concrete.AppExpression>) {
    val subConcreteExpressions = (concrete as? Concrete.FunctionDefinition?)?.body?.clauses?.flatMap { it.expression?.let { expr -> getAllConcreteSubAppExpression(expr) } ?: emptyList() } ?: emptyList()
    val subExpressions = mutableListOf<Pair<Expression, Concrete.AppExpression>>()
    for (subExpr in subConcreteExpressions) {
      val subDefVisitor = CorrespondedSubDefVisitor(subExpr)
      val subPair = concrete.accept(subDefVisitor, typechecked)
      val subCore = subPair?.proj1 as? FunCallExpression? ?: continue
      subExpressions.add(Pair(subCore, subExpr))
    }
    coreToConcrete.putAll(subExpressions)
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

  private fun recursiveLineMarkers(file: ArendFile): List<LineMarkerInfo<PsiElement>> {
    val project = file.project

    val server = project.service<ArendServerService>().server
    val moduleLocation = file.moduleLocation ?: return listOf()

    val concreteDefs = mutableMapOf<FunctionDefinition, Concrete.Definition>()
    val defsClauses = mutableMapOf<FunctionDefinition, List<ElimClause<ExpressionPattern>>>()
    val otherDefs = mutableSetOf<ArendDefFunction>()
    val defsToPsiElement = mutableMapOf<FunctionDefinition, ArendDefFunction>()
    val coreToConcrete = mutableMapOf<Expression, Concrete.AppExpression>()

    var definitions = server.getResolvedDefinitions(moduleLocation).toSet()
    if (definitions.isNotEmpty()) {
      previousDefinitions.put(file, definitions)
    } else {
      definitions = previousDefinitions[file] ?: run {
        return emptyList()
      }
    }
    for (definition in definitions) {
      ProgressManager.checkCanceled()
      val concrete = definition.definition as? Concrete.Definition ?: continue
      val defReferable = concrete.data
      val typechecked = defReferable.typechecked as? FunctionDefinition? ?: continue
      val defFunction = defReferable.data as? ArendDefFunction? ?: continue

      val oldTypechecked = getFunctionDefinition(server, file, typechecked, defReferable) ?: typechecked
      addCoreWithConcrete(concrete, oldTypechecked, coreToConcrete)

      val clauses = getClauses(server, file, typechecked, defReferable)

      if (clauses.isEmpty()) {
        continue
      }

      concreteDefs.putIfAbsent(typechecked, concrete)
      defsClauses.putIfAbsent(typechecked, clauses)
      defsToPsiElement[typechecked] = defFunction

      otherDefs.addAll(PsiTreeUtil.findChildrenOfType(defFunction, ArendRefIdentifier::class.java)
        .map { it.resolve }.filterIsInstance<ArendDefFunction>().filter { it.containingFile != file })
    }

    addOtherDefinitions(project, otherDefs, concreteDefs, defsClauses, defsToPsiElement, coreToConcrete)

    val graph = getCallGraph(concreteDefs, defsClauses)
    val terminationResult = graph.checkTermination()
    val newGraph = terminationResult.proj2
    val vertices = graph.graph.map { Pair(it.key, defsToPsiElement[it.key]) }.toMap()
    val result = mutableListOf<LineMarkerInfo<PsiElement>>()
    for (entryVertex in graph.graph.entries) {
      val (vertex, otherVertices) = entryVertex
      val element = defsToPsiElement[vertex] ?: continue
      if (element.containingFile != file) continue
      var isMutualRecursive = false
      for ((otherVertex, _) in otherVertices) {
        if (otherVertex == vertex) continue
        if (graph.graph[otherVertex]?.contains(vertex) == true) {
          isMutualRecursive = true
          break
        }
      }
      if (isMutualRecursive) {
        element.defIdentifier?.id?.let {
          result.add(LineMarkerInfo(it, it.textRange, MUTUAL_RECURSIVE,
          { "Show the call graph" }, { _, _ -> mutualRecursiveCall(project, graph, newGraph, entryVertex, newGraph.entries.firstOrNull { entryVertex.key == it.key }, vertices, coreToConcrete) },
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
    return result
  }

  private fun getFunctionDefinition(server: ArendServer, file: ArendFile, functionDefinition: FunctionDefinition, defReferable: TCDefReferable): FunctionDefinition? {
    return if (functionDefinition.actualBody != null) {
      functionDefinition
    } else {
      server.errorMap[file.moduleLocation]?.filterIsInstance<TerminationCheckError>()?.find { it.definition == defReferable && it.functionDefinition.actualBody != null }?.functionDefinition
    }
  }

  private fun getClauses(server: ArendServer, file: ArendFile, functionDefinition: FunctionDefinition, defReferable: TCDefReferable): List<ElimClause<ExpressionPattern>> {
    return (((getFunctionDefinition(server, file, functionDefinition, defReferable)?.actualBody as? ElimBody?)?.clauses)
      ?: emptyList()).map { ExtElimClause(Pattern.toExpressionPatterns(it.patterns, functionDefinition.parameters), it.expression, ExprSubstitution()) }
  }

  private fun addOtherDefinitions(
    project: Project,
    definitions: Set<ArendDefFunction>,
    concreteDefs: MutableMap<FunctionDefinition, Concrete.Definition>,
    defsClauses: MutableMap<FunctionDefinition, List<ElimClause<ExpressionPattern>>>,
    defsToPsiElement: MutableMap<FunctionDefinition, ArendDefFunction>,
    coreToConcrete: MutableMap<Expression, Concrete.AppExpression>
  ) {
    val server = project.service<ArendServerService>().server
    val files = definitions.mapNotNull { it.containingFile as? ArendFile? }
    server.getCheckerFor(files.mapNotNull { it.moduleLocation }.filter { !server.modules.contains(it) }).typecheck(null, DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty())

    for (definition in definitions) {
      val file = definition.containingFile as? ArendFile? ?: continue
      val concrete = file.moduleLocation?.let { server.getResolvedDefinitions(it) }?.map { it.definition }
        ?.find { it.data.data == definition } as? Concrete.Definition ?: continue
      val defReferable = concrete.data
      val typechecked = defReferable.typechecked as? FunctionDefinition? ?: continue

      val oldTypechecked = getFunctionDefinition(server, file, typechecked, defReferable) ?: typechecked
      addCoreWithConcrete(concrete, oldTypechecked, coreToConcrete)

      val clauses = getClauses(server, file, typechecked, defReferable)
      concreteDefs.putIfAbsent(typechecked, concrete)
      defsClauses.putIfAbsent(typechecked, clauses)
      defsToPsiElement.putIfAbsent(typechecked, definition)
    }
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
    return result.toString().trim()
  }

  private fun selfRecursiveCall(vertex: Map.Entry<Definition, HashMap<Definition, HashSet<BaseCallMatrix<Definition>>>>) {
    val title = ArendBundle.message("arend.termination.checker.recursive")
    val info = ArendBundle.message("arend.termination.checker.info")
    val matrices = ArendBundle.message("arend.termination.checker.show.matrices")
    val balloon = JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<String>(title, listOf(info, matrices)) {
      override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
        if (selectedValue == info) {
          BrowserUtil.browse(url)
        } else if (selectedValue == matrices) {
          doFinalStep {
            JBPopupFactory.getInstance()
              .createBalloonBuilder(JScrollPane(getCallMatrix(toTextOfCallMatrix(vertex))))
              .setHideOnClickOutside(true)
              .createBalloon()
              .show(RelativePoint.fromScreen(MouseInfo.getPointerInfo().location), Balloon.Position.atRight)
          }
        }
        return FINAL_CHOICE
      }
    })
    balloon.show(RelativePoint.fromScreen(MouseInfo.getPointerInfo().location))
  }

  private fun getVertices(
    vertex: Map.Entry<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>
  ): Set<GraphNode> {
    return (mutableListOf(vertex.key) + vertex.value.keys).mapNotNull { (it.referable.data as? ArendDefFunction?)?.fullNameText }.map { GraphNode(it) }.toSet()
  }

  private fun getNameDefinition(vertex: Definition): String? {
      return (vertex.referable.data as? ArendDefFunction?)?.fullNameText
  }

  private fun getEdges(
    vertex: Map.Entry<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>,
    graph: Map<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>,
    vertices: Map<Definition, ArendDefFunction?>
  ): Set<GraphEdge> {
    val edges = mutableListOf<GraphEdge>()
    for ((otherVertex, edgesToOtherVertex) in vertex.value) {
      for (edge in edgesToOtherVertex) {
        val from = getNameDefinition(vertex.key) ?: continue
        val to = getNameDefinition(otherVertex) ?: continue
        edges.add(GraphEdge(from, to, edge))
      }
    }
    for (otherVertex in vertex.value.keys) {
      val otherEdges = graph[otherVertex] ?: continue
      for ((newVertex, edgesFromOtherVertex) in otherEdges) {
        if (vertices.contains(newVertex)) {
          for (edge in edgesFromOtherVertex) {
            val from = getNameDefinition(otherVertex) ?: continue
            val to = getNameDefinition(newVertex) ?: continue
            edges.add(GraphEdge(from, to, edge))
          }
        }
      }
    }
    return edges.toSet()
  }

  private fun mutualRecursiveCall(
    project: Project,
    graph: DefinitionCallGraph,
    compositeGraph: Map<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>,
    vertex: Map.Entry<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>,
    newVertex: Map.Entry<Definition, Map<Definition, Set<BaseCallMatrix<Definition>>>>?,
    vertices: Map<Definition, ArendDefFunction?>,
    coreToConcrete: Map<Expression, Concrete.Expression>
  ) {
    val title = ArendBundle.message("arend.termination.checker.recursive")
    val info = ArendBundle.message("arend.termination.checker.info")
    val callGraph = ArendBundle.message("arend.termination.checker.show.graph")
    val balloon = JBPopupFactory.getInstance().createListPopup(object : BaseListPopupStep<String>(title, listOf(info, callGraph)) {
      override fun onChosen(selectedValue: String?, finalChoice: Boolean): PopupStep<*>? {
        if (selectedValue == info) {
          BrowserUtil.browse(url)
        } else if (selectedValue == callGraph) {
          doFinalStep {
            project.service<GraphSimulator>().displayOrthogonal(
              "Graph_Call_From_${vertex.key.name}",
              getVertices(vertex),
              getEdges(vertex, graph.graph, vertices),
              newVertex?.let { getVertices(it) } ?: emptySet(),
              newVertex?.let { getEdges(it, compositeGraph, vertices) } ?: emptySet(),
              coreToConcrete,
              FrameType.CALL_GRAPH
            )
          }
        }
        return FINAL_CHOICE
      }
    })
    balloon.show(RelativePoint.fromScreen(MouseInfo.getPointerInfo().location))
  }

  private fun getCallGraph(
    definitions: Map<FunctionDefinition, Concrete.Definition>,
    clauses: Map<FunctionDefinition, List<ElimClause<ExpressionPattern>>>
  ): DefinitionCallGraph {
    val definitionCallGraph = DefinitionCallGraph()
    for ((key, _) in definitions) {
      val functionClauses = clauses[key]
      definitionCallGraph.add(key, functionClauses ?: emptyList(), definitions.keys)
    }
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
  }
}
