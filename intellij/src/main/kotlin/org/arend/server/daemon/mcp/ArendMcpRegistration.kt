package org.arend.server.daemon.mcp

import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.util.EnvironmentUtil
import org.arend.daemon.mcp.DaemonMcpServer
import org.arend.daemon.mcp.McpRegistration
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Registers the `arend-daemons` MCP server for the project, in the project's own config of every agent
 * installed, as `arend --mcp-init` does -- when the user asks for it on the Daemon tab, and at no other
 * time. The command is `arend --mcp`, when the `arend` on the PATH agents see serves this build's
 * daemons. Otherwise the user is told why, and may choose to register the server from the plugin's own
 * jars on the IDE's JVM instead, with Jackson from the IDE's jars, which only this machine has. What it
 * wrote is said in a balloon, with how to take it back out.
 */
object ArendMcpRegistration {
    private val LOG = logger<ArendMcpRegistration>()

    /** The Jackson classes [DaemonMcpServer] needs, one from each of its jars. */
    private val JACKSON = listOf(
        "com/fasterxml/jackson/databind/ObjectMapper.class",
        "com/fasterxml/jackson/core/JsonParser.class",
        "com/fasterxml/jackson/annotation/JsonAutoDetect.class"
    )

    /** Registers the server for [project], off the caller's thread; nothing happens for a project with no directory. */
    fun registerForProject(project: Project) {
        val dir = project.basePath?.let { Path.of(it) } ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            // The PATH of the user's shell, which agents and daemons are started with: an IDE started
            // from the desktop has a shorter one of its own.
            val arend = McpRegistration.checkArend(EnvironmentUtil.getValue("PATH"))
            if (arend.usable()) {
                register(project, dir, McpRegistration.PORTABLE)
                return@executeOnPooledThread
            }
            val own = pluginCommand()
            ApplicationManager.getApplication().invokeLater({
                if (own == null) {
                    warn(project, McpRegistration.arendAdvice(arend))
                    return@invokeLater
                }
                val choice = Messages.showOkCancelDialog(project,
                    McpRegistration.arendAdvice(arend) + "\n\nOr register this IDE's own copy of the server instead: it runs on " +
                        "this machine only, and stops working when the IDE or the Arend plugin is updated, until it is registered again.",
                    "Register the ${McpRegistration.SERVER_NAME} MCP Server", "Use This IDE's Copy", Messages.getCancelButton(),
                    Messages.getWarningIcon())
                if (choice == Messages.OK) {
                    ApplicationManager.getApplication().executeOnPooledThread { register(project, dir, own) }
                }
            }, project.disposed)
        }
    }

    /** Registers [command] for the project in [dir], and says in a balloon what that did. */
    private fun register(project: Project, dir: Path, command: McpRegistration.Command) {
        val result = McpRegistration.initProject(dir, command)
        if (project.isDisposed) return
        val text = StringBuilder()
        if (result.done.isEmpty()) {
            if (result.failures.isEmpty()) text.append("No agent that reads a project's own MCP config is installed.")
        } else {
            text.append("Registered the ${McpRegistration.SERVER_NAME} MCP server for agents started in this project, in: ")
                .append(result.done.joinToString(", ")).append(". <code>arend --mcp-unregister</code> takes it back out.")
        }
        for (note in result.notes) text.append("<br>").append(note)
        if (text.isNotEmpty()) {
            NotificationGroupManager.getInstance().getNotificationGroup("Arend Daemon")
                .createNotification("Arend daemon", text.toString(), NotificationType.INFORMATION)
                .notify(project)
        }
        for (failure in result.failures) warn(project, failure.replaceFirstChar { it.uppercase() } + ".")
        for (warning in result.warnings) warn(project, warning)
    }

    private fun warn(project: Project, message: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("Arend Warning Messages")
            .createNotification("Arend daemon", message, NotificationType.WARNING)
            .notify(project)
    }

    /**
     * [DaemonMcpServer] on the IDE's JVM, from every jar of the plugin -- the directory's `*`, so that a
     * jar renamed by an update is still on it -- and Jackson's; null when the plugin is not installed
     * from jars, as when it runs from its classes.
     */
    private fun pluginCommand(): McpRegistration.Command? {
        val lib = PluginManager.getPluginByClass(ArendMcpRegistration::class.java)?.pluginPath?.resolve("lib")
        if (lib == null || !Files.isDirectory(lib)) {
            LOG.info("No command for the ${McpRegistration.SERVER_NAME} MCP server: the plugin has no lib directory")
            return null
        }
        // The IDE's own, from its boot classpath: the plugin's class loader finds the copy of a
        // plugin it depends on first, which goes when that plugin is updated or removed.
        val jackson = JACKSON.map { PathManager.getResourceRoot(PathManager::class.java, "/$it") }
        if (jackson.any { it == null }) {
            LOG.warn("No command for the ${McpRegistration.SERVER_NAME} MCP server: Jackson is not found")
            return null
        }
        val classpath = listOf(lib.toString() + File.separator + "*") + jackson.filterNotNull().distinct()
        return McpRegistration.Command(McpRegistration.command(Path.of(System.getProperty("java.home")), classpath, DaemonMcpServer::class.java.name), false)
    }
}
