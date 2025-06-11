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
import org.arend.core.expr.Expression
import org.arend.term.concrete.Concrete
import org.arend.typechecking.termination.CallMatrix
import org.arend.util.ArendBundle
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.Style
import org.fife.ui.rsyntaxtextarea.SyntaxScheme
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
  frame: JFrame,
  graphComponent: mxGraphComponent,
  matrixPanel: JPanel,
  cellToInfo: Map<mxCell, MutableSet<Any>>,
  coreToConcrete: Map<Expression, Concrete.Expression>
) {
  graphComponent.graphControl.addMouseListener(object : MouseAdapter() {
    override fun mouseClicked(e: MouseEvent) {
      val cell = graphComponent.getCellAt(e.x, e.y) as? mxCell? ?: return
      if (cell.isEdge) {
        matrixPanel.removeAll()
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
        matrixPanel.repaint()
        matrixPanel.revalidate()

        frame.repaint()
        frame.revalidate()
        frame.pack()
      }
    }
  })
}
