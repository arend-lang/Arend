package org.arend.graph

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.util.minimumHeight
import com.intellij.ui.util.minimumWidth
import com.mxgraph.layout.hierarchical.mxHierarchicalLayout
import com.mxgraph.model.mxCell
import com.mxgraph.swing.mxGraphComponent
import com.mxgraph.util.mxConstants
import com.mxgraph.view.mxGraph
import org.arend.codeInsight.ArendLineMarkerProvider.Companion.DOCUMENTATION_URL
import org.arend.core.expr.Expression
import org.arend.graph.call.CALL_GRAPH_FONT_SIZE
import org.arend.graph.call.addEdgeListener
import org.arend.term.concrete.Concrete
import org.arend.util.ArendBundle
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Image
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import javax.imageio.ImageIO
import javax.swing.*

data class GraphNode(val id: String)

data class GraphEdge(val from: String, val to: String, val info: Any? = null)

@Service(Service.Level.PROJECT)
class GraphSimulator(val project: Project) {
    private val fileChooser = JFileChooser()

    private fun getImage(graphComponent: mxGraphComponent): BufferedImage {
      val viewport: JViewport = graphComponent.viewport
      val view = viewport.view
      val image = BufferedImage(graphComponent.minimumWidth + PADDING_GRAPH, graphComponent.minimumHeight, BufferedImage.TYPE_INT_RGB)
      val g2d = image.createGraphics()
      g2d.translate(PADDING_GRAPH.toDouble(), 0.0)

      view.paint(g2d)
      g2d.dispose()
      return image
    }

    private fun getGraphComponent(vertices: Set<GraphNode>, edges: Set<GraphEdge> ): Pair<mxGraphComponent, MutableMap<mxCell, MutableSet<Any>>> {
      val graph = mxGraph().apply {
        isCellsMovable = false
        isCellsEditable = false
        isCellsResizable = false
        isCellsDisconnectable = false
        isCellsLocked = true
        stylesheet.defaultVertexStyle[mxConstants.STYLE_FONTSIZE] = CALL_GRAPH_FONT_SIZE
      }

      val cellToInfo = mutableMapOf<mxCell, MutableSet<Any>>()

      val parent = graph.defaultParent
      graph.model.beginUpdate()
      try {
        val vertexToCell = mutableMapOf<String, mxCell>()
        for (vertex in vertices) {
          val graphVertex = graph.insertVertex(parent, null, vertex.id, 0.0, 0.0, 1.0, 1.0) as? mxCell? ?: continue
          graph.updateCellSize(graphVertex)

          vertexToCell[vertex.id] = graphVertex
        }

        val usedEdges = mutableSetOf<Pair<String, String>>()
        val edgeToCell = mutableMapOf<Pair<String, String>, mxCell>()
        for (edge in edges) {
          if (usedEdges.contains(edge.from to edge.to)) {
            val graphEdge = edgeToCell[edge.from to edge.to] ?: continue
            edge.info?.let { cellToInfo.getOrPut(graphEdge) { mutableSetOf() }.add(it) }
            continue
          }
          val fromCell = vertexToCell[edge.from] ?: continue
          val toCell = vertexToCell[edge.to] ?: continue

          val graphEdge = graph.insertEdge(parent, null, null, fromCell, toCell) as? mxCell? ?: continue
          graph.updateCellSize(graphEdge)
          graph.setCellStyle(mxConstants.STYLE_ROUNDED + "=1;", arrayOf(graphEdge))

          usedEdges.add(edge.from to edge.to)
          edgeToCell[edge.from to edge.to] = graphEdge
          edge.info?.let { cellToInfo.getOrPut(graphEdge) { mutableSetOf() }.add(it) }
        }
      } finally {
        graph.model.endUpdate()
      }

      val graphComponent = mxGraphComponent(graph).apply {
        mxHierarchicalLayout(graph).execute(parent)
        setViewportBorder(BorderFactory.createEmptyBorder(PADDING_GRAPH, PADDING_GRAPH, 0, 0))
        minimumSize = preferredSize
      }
      return graphComponent to cellToInfo
    }

    fun displayOrthogonal(
        graphName: String,
        vertices: Set<GraphNode>,
        edges: Set<GraphEdge>,
        newEdges: Set<GraphEdge> = emptySet(),
        coreToConcrete: Map<Expression, Concrete.Expression> = emptyMap(),
        frameType: FrameType = FrameType.SIMPLE
    ) {
      val (graphComponent, cellToInfo) = getGraphComponent(vertices, edges)
      val (newGraphComponent, newCellToInfo) = getGraphComponent(vertices, newEdges)

      val matrixPanel = JPanel(BorderLayout())
      val centerPanel = when (frameType) {
        FrameType.SIMPLE -> graphComponent
        FrameType.CALL_GRAPH -> {
          matrixPanel.layout = BoxLayout(matrixPanel, BoxLayout.Y_AXIS)
          matrixPanel.add(JLabel(ArendBundle.message("arend.termination.checker.click.message")), BorderLayout.CENTER)

          val wrapperPanel = JPanel(FlowLayout(FlowLayout.CENTER))
          wrapperPanel.add(matrixPanel)

          val leftPanel = JPanel(BorderLayout())
          val beforeComposition = JLabel("Before Completion:").apply {
            setHorizontalAlignment(SwingConstants.CENTER)
          }
          val afterComposition = JLabel("After Completion:").apply {
            setHorizontalAlignment(SwingConstants.CENTER)
          }
          leftPanel.add(beforeComposition, BorderLayout.NORTH)
          leftPanel.add(graphComponent, BorderLayout.CENTER)

          val switcher = JRadioButton("Before/After Completion")
          switcher.addActionListener {
            if (switcher.isSelected) {
              leftPanel.remove(beforeComposition)
              leftPanel.add(afterComposition, BorderLayout.NORTH)
              leftPanel.remove(graphComponent)
              leftPanel.add(newGraphComponent, BorderLayout.CENTER)
            } else {
              leftPanel.remove(afterComposition)
              leftPanel.add(beforeComposition, BorderLayout.NORTH)
              leftPanel.remove(newGraphComponent)
              leftPanel.add(graphComponent, BorderLayout.CENTER)
            }
            leftPanel.updateUI()
          }
          leftPanel.add(JPanel(BorderLayout()).apply {
            add(switcher, BorderLayout.NORTH)
            add(JSeparator(SwingConstants.HORIZONTAL))
            add(JButton("Open the documentation page").apply {
              addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) {
                  BrowserUtil.browse(DOCUMENTATION_URL)
                }
              })
            }, BorderLayout.SOUTH)
          }, BorderLayout.SOUTH)

          val mainPanel = JPanel(BorderLayout())
          mainPanel.add(leftPanel, BorderLayout.WEST)
          mainPanel.add(wrapperPanel, BorderLayout.CENTER)
          JScrollPane(mainPanel)
        }
      }

      val southPanel = JPanel(BorderLayout())
      val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT))
      val copyButton = JButton("Copy Image").apply {
        addActionListener {
          val transferableImage = TransferableImage(getImage(graphComponent))
          Toolkit.getDefaultToolkit().systemClipboard.setContents(transferableImage, null)
        }
      }
      val downloadButton = JButton("Download Image").apply {
        addActionListener {
          val format = "png"
          fileChooser.dialogTitle = "Specify a file to save"
          fileChooser.selectedFile = File("${graphName.replace(".", "_")}.${format}")

          val userSelection = fileChooser.showSaveDialog(null)
          if (userSelection == JFileChooser.APPROVE_OPTION) {
            val destinationFilePath = fileChooser.selectedFile.absolutePath
            try {
              val file = File(destinationFilePath)
              ImageIO.write(getImage(graphComponent), format, file)
            } catch (_: IOException) {
              Messages.showErrorDialog("Failed to save a graph image", "Error")
            }
          }
        }
      }
      buttonsPanel.add(copyButton)
      buttonsPanel.add(downloadButton)

      southPanel.add(buttonsPanel, BorderLayout.EAST)
      southPanel.border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

      when (frameType) {
        FrameType.SIMPLE -> {
          val dialogWrapper = object : DialogWrapper(project, true) {
            init {
              title = graphName
              init()
            }

            override fun createCenterPanel(): JComponent? {
              return JPanel(BorderLayout()).apply {
                add(centerPanel, BorderLayout.CENTER)
              }
            }

            override fun createSouthPanel(): JComponent? {
              return JPanel(BorderLayout()).apply {
                add(southPanel, BorderLayout.SOUTH)
              }
            }
          }
          dialogWrapper.show()
        }
        FrameType.CALL_GRAPH -> {
          val frame = JFrame(graphName).apply {
            defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
            layout = BorderLayout()
            setExtendedState(JFrame.MAXIMIZED_BOTH)
          }
          frame.add(centerPanel, BorderLayout.CENTER)
          frame.add(southPanel, BorderLayout.SOUTH)

          addEdgeListener(project, graphComponent, matrixPanel, cellToInfo, coreToConcrete)
          addEdgeListener(project, newGraphComponent, matrixPanel, newCellToInfo, coreToConcrete)

          frame.pack()
          frame.isVisible = true
        }
      }
    }

    companion object {
      const val PADDING_GRAPH = 5

      enum class FrameType {
        SIMPLE,
        CALL_GRAPH
      }

      class TransferableImage(private val image: Image) : Transferable {
          override fun getTransferDataFlavors(): Array<DataFlavor> {
              return arrayOf(DataFlavor.imageFlavor)
          }

          override fun isDataFlavorSupported(flavor: DataFlavor): Boolean {
              return flavor.equals(DataFlavor.imageFlavor)
          }

          override fun getTransferData(flavor: DataFlavor): Any {
              if (flavor.equals(DataFlavor.imageFlavor)) {
                  return image
              }
              throw UnsupportedFlavorException(flavor)
          }
      }
    }
}
