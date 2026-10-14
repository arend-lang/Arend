package org.arend.toolWindow.server

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import org.arend.daemon.server.DaemonServer
import org.arend.daemon.wire.FrameChannel
import org.arend.server.daemon.ArendDaemonService
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer

/**
 * The Daemon tab of the Arend Server tool window: whether the project's server answers as an Arend
 * daemon when the project opens; whether it does now, and what it is doing; which libraries it
 * serves; and the requests it answered last.
 */
class ArendDaemonView(project: Project, parent: Disposable) {
    private val service = project.service<ArendDaemonService>()
    private val startOnOpen = JBRadioButton("Yes")
    private val noStartOnOpen = JBRadioButton("No")
    private val status = JBLabel()
    private val libraries = JBLabel()
    private val agents = JBLabel()
    private val requests = JBLabel()
    private val startButton = JButton("Start")
    private val stopButton = JButton("Stop")
    /** Keeps the "for how long" figures current while the daemon runs. */
    private val ticker = Timer(1000) { show(service.state) }

    val component: JComponent

    init {
        ButtonGroup().apply {
            add(startOnOpen)
            add(noStartOnOpen)
        }
        (if (service.startOnOpen) startOnOpen else noStartOnOpen).isSelected = true
        startOnOpen.addActionListener { service.startOnOpen = true }
        noStartOnOpen.addActionListener { service.startOnOpen = false }

        startButton.addActionListener { inBackground { service.start(true) } }
        stopButton.addActionListener { inBackground { service.stop() } }

        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("Start the daemon when the project opens:", row(startOnOpen, noStartOnOpen))
            .addSeparator()
            .addLabeledComponent("Daemon:", row(startButton, stopButton))
            .addComponent(status)
            .addComponent(libraries)
            .addComponent(agents)
            .addComponent(requests)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        form.border = JBUI.Borders.empty(8)
        component = JPanel(BorderLayout()).apply { add(ScrollPaneFactory.createScrollPane(form, true), BorderLayout.CENTER) }

        show(service.state)
        service.addStateListener(parent, ::show)
        Disposer.register(parent) { ticker.stop() }
    }

    private fun show(state: ArendDaemonService.State?) {
        startButton.isEnabled = state == null
        stopButton.isEnabled = state != null
        if (state != null) ticker.start() else ticker.stop()

        val now = System.currentTimeMillis()
        val facts = LinkedHashMap<String, String>()
        if (state == null) {
            facts["State"] = "not running"
        } else {
            val activity = state.activity
            facts["State"] = if (activity.current == null) activity.state
                else "${activity.state}: ${activity.current}" + (activity.currentSince?.let { " (for ${duration(now - it)})" } ?: "")
            facts["Process"] = "the IDE, PID ${ProcessHandle.current().pid()}"
            facts["Socket"] = state.socket
            facts["Started"] = "${time(state.startedAt)} (${duration(now - state.startedAt)} ago)"
            facts["Queue"] = if (activity.queued.isEmpty()) "empty" else activity.queued.joinToString("; ")
            facts["Requests answered"] = activity.served.toString()
        }
        status.text = XmlStringUtil.wrapInHtml(table(facts.map { (key, value) -> listOf("<b>$key</b>", escape(value)) }))

        val html = StringBuilder()
        fun section(title: String, items: Collection<String>) {
            if (items.isEmpty()) return
            html.append("<p><b>").append(title).append("</b></p><ul>")
            for (item in items) html.append("<li>").append(escape(item)).append("</li>")
            html.append("</ul>")
        }
        if (state != null) {
            section("Serves", state.libraries)
            section("Also loaded from ~/.arend/libs or a zip", state.shared)
            section("Left out", state.skipped.map { (name, why) -> "$name: $why" })
        }
        libraries.setIfChanged(if (html.isEmpty()) "" else XmlStringUtil.wrapInHtml(html))
        // Said before a start too: it is the start that writes to the agents' configs.
        agents.setIfChanged(AGENTS)

        val recent = state?.activity?.recent.orEmpty()
        requests.setIfChanged(if (recent.isEmpty()) "" else XmlStringUtil.wrapInHtml("<p><b>Latest requests</b></p>" +
            table(recent.map { listOf(time(it.startedAt), escape(it.command), outcome(it), duration(it.durationMs)) })))
    }

    private fun outcome(request: DaemonServer.Finished) = when (request.exitCode) {
        0 -> "ok"
        FrameChannel.CANCELLED_EXIT_CODE -> "cancelled"
        else -> "exit ${request.exitCode}"
    }

    /** Sets [text] unless it is there already: the ticker redraws each second, and only the status changes. */
    private fun JBLabel.setIfChanged(text: String) {
        if (this.text != text) this.text = text
    }

    private fun table(rows: List<List<String>>) =
        rows.joinToString("", "<table cellpadding='2'>", "</table>") { cells -> cells.joinToString("", "<tr>", "</tr>") { "<td>$it</td>" } }

    private fun escape(text: String) = XmlStringUtil.escapeString(text)

    private fun time(millis: Long) = TIME.format(Instant.ofEpochMilli(millis))

    private fun duration(millis: Long) = if (millis < 1000) "$millis ms" else StringUtil.formatDuration(millis / 1000 * 1000)

    private fun inBackground(action: () -> Unit) = ApplicationManager.getApplication().executeOnPooledThread(action)

    private fun row(vararg components: JComponent) = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), 0)).apply {
        components.forEach { add(it) }
    }

    private companion object {
        /** In short lines: the tab scrolls sideways rather than wrap a long one. */
        val AGENTS: String = XmlStringUtil.wrapInHtml("Starting the daemon adds the <code>arend-daemons</code> MCP server<br>" +
            "to the user configs of the agents installed, so that they can use it.<br>" +
            "<code>arend --mcp-unregister</code> removes it.")

        /** One for every row: the tab redraws each second while the daemon runs. */
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
    }
}
