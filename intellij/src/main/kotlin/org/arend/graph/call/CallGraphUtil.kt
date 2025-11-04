package org.arend.graph.call

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.ide.highlighter.JavaHighlightingColors
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.endOffset
import com.intellij.psi.util.startOffset
import com.intellij.util.ui.UIUtil
import com.mxgraph.model.mxCell
import com.mxgraph.swing.mxGraphComponent
import org.arend.core.definition.Definition
import org.arend.core.expr.Expression
import org.arend.term.concrete.Concrete
import org.arend.typechecking.termination.BaseCallGraph
import org.arend.typechecking.termination.CallMatrix
import org.arend.util.ArendBundle
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Style
import org.fife.ui.rsyntaxtextarea.SyntaxScheme
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

const val CALL_GRAPH_FONT_SIZE = 18
const val CALL_GRAPH_LESS_NUMBER = 40
const val CALL_GRAPH_EQUAL_NUMBER = 41
const val CALL_GRAPH_QUESTION_NUMBER = 42
const val CALL_GRAPH_STYLES_SIZE = 43

fun extendSyntaxScheme(scheme: SyntaxScheme?, newSize: Int) {
  try {
    val styles = scheme?.styles ?: emptyArray()
    if (styles.size < newSize) {
      val newStyles = arrayOfNulls<Style>(newSize)
      System.arraycopy(styles, 0, newStyles, 0, styles.size)
      for (i in styles.indices) {
        newStyles[i]?.foreground = EditorColorsManager.getInstance().globalScheme.defaultForeground
      }
      for (i in styles.size..<newSize) {
        newStyles[i] = Style()
      }
      scheme?.styles = newStyles
    }
  } catch (e: Exception) {
    e.printStackTrace()
  }
}

fun getCallMatrix(textInfo: String): RSyntaxTextArea {
  CallGraphTokenMakerFactorySetup.register()
  return RSyntaxTextArea().apply {
    text = textInfo

    background = UIUtil.getPanelBackground()
    highlightCurrentLine = false
    setSyntaxEditingStyle("text/CallGraphCustom")

    extendSyntaxScheme(syntaxScheme, CALL_GRAPH_STYLES_SIZE)
    val scheme = EditorColorsManager.getInstance().globalScheme
    syntaxScheme.setStyle(CALL_GRAPH_LESS_NUMBER, Style(scheme.getAttributes(DefaultLanguageHighlighterColors.STRING).foregroundColor, null))
    syntaxScheme.setStyle(CALL_GRAPH_EQUAL_NUMBER, Style(scheme.getAttributes(JavaHighlightingColors.ANNOTATION_NAME_ATTRIBUTES).foregroundColor, null))
    syntaxScheme.setStyle(CALL_GRAPH_QUESTION_NUMBER, Style(scheme.getAttributes(HighlightInfoType.UNUSED_SYMBOL.attributesKey).foregroundColor, null))

    setFont(Font("Monospaced", Font.PLAIN, CALL_GRAPH_FONT_SIZE))
    alignmentX = Component.CENTER_ALIGNMENT
  }
}

fun addEdgeListener(
  project: Project,
  graphComponent: mxGraphComponent,
  matrixPanel: JPanel,
  cellToInfo: Map<mxCell, MutableSet<Any>>,
  coreToConcrete: Map<Expression, Concrete.Expression>
) {
  graphComponent.graphControl.addMouseListener(object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      val cell = graphComponent.getCellAt(e.x, e.y) as? mxCell
      matrixPanel.removeAll()
      if (cell?.isEdge == true) {
        matrixPanel.add(JLabel("Call matrices:").apply {
          alignmentX = Component.CENTER_ALIGNMENT
        })

        val information = cellToInfo[cell] ?: return
        for (info in information) {
          val rowPanel = JPanel()
          rowPanel.layout = BoxLayout(rowPanel, BoxLayout.Y_AXIS)
          rowPanel.add(getCallMatrix(info.toString().trim()))
          (info as? CallMatrix?)?.let { callMatrix ->
            coreToConcrete[callMatrix.callExpression]?.let { concrete ->
              rowPanel.add(JButton(ArendBundle.message("arend.termination.checker.show.call")).apply {
                alignmentX = Component.CENTER_ALIGNMENT
                addActionListener {
                  val element = concrete.data as? PsiElement ?: return@addActionListener
                  val virtualFile = element.containingFile.virtualFile

                  val fileEditorManager = FileEditorManager.getInstance(project)

                  if (!fileEditorManager.openFiles.contains(virtualFile) || fileEditorManager.selectedTextEditor?.virtualFile != virtualFile) {
                    fileEditorManager.openFile(virtualFile, true)
                  }
                  val editor = fileEditorManager.selectedTextEditor ?: return@addActionListener
                  editor.selectionModel.setSelection(element.startOffset, element.endOffset)
                  editor.caretModel.moveToOffset(element.startOffset)

                  val editorComponent = editor.contentComponent
                  val editorWindow = SwingUtilities.getWindowAncestor(editorComponent) ?: return@addActionListener
                  editorWindow.toFront()
                  editorWindow.requestFocus()
                }
              })
            }
          }
          rowPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0))
          matrixPanel.add(rowPanel)
        }
      } else {
        matrixPanel.add(JLabel(ArendBundle.message("arend.termination.checker.click.message")), BorderLayout.CENTER)
      }
      matrixPanel.updateUI()
    }
  })
}

class CallGraphComponentStrongConnectivity(private val graph: BaseCallGraph<Definition>) {

  private fun markVertexes(definition: Definition, counter: Int, markedVertexes: MutableMap<Definition, Int>): Int {
    var newCounter = counter + 1
    markedVertexes[definition] = newCounter
    for ((otherDefinition, _) in (graph.graph.get(definition) ?: return newCounter)) {
      if (!markedVertexes.containsKey(otherDefinition)) {
        newCounter++
        newCounter = markVertexes(otherDefinition, newCounter, markedVertexes)
      }
    }
    newCounter++
    markedVertexes[definition] = newCounter
    return newCounter
  }

  private fun invertGraph(newGraph: MutableMap<Definition, HashSet<Definition>>) {
    for ((definition, edges) in graph.graph) {
      for (otherDefinition in edges.keys) {
        newGraph.getOrPut(otherDefinition) { HashSet() }.add(definition)
      }
    }
  }

  fun getStronglyConnectedComponents(): MutableSet<Set<Definition>> {
    var counter = 0
    val markedVertexes = mutableMapOf<Definition, Int>()
    for (definition in graph.graph.keys) {
      if (!markedVertexes.containsKey(definition)) {
        counter = markVertexes(definition, counter, markedVertexes)
        counter++
      }
    }
    val invertedGraph = mutableMapOf<Definition, HashSet<Definition>>()
    invertGraph(invertedGraph)

    val components = mutableSetOf<Set<Definition>>()
    val marked = mutableSetOf<Definition>()
    val invertedVertexes = markedVertexes.entries.sortedBy { -it.value }.map { it.key }
    for (definition in invertedVertexes) {
      if (!marked.contains(definition)) {
        val component = mutableSetOf<Definition>()
        val stack = mutableListOf<Definition>()
        stack.add(definition)
        while (stack.isNotEmpty()) {
          val current = stack.removeLast()
          component.add(current)
          marked.add(current)

          invertedGraph[current]?.filter { !marked.contains(it) }?.let {
            stack.addAll(it)
          }
        }
        components.add(component)
      }
    }
    return components
  }
}
