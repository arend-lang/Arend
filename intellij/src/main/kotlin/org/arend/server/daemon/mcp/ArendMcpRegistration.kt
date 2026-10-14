package org.arend.server.daemon.mcp

import com.intellij.ide.plugins.PluginManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import org.arend.daemon.mcp.DaemonMcpServer
import org.arend.daemon.mcp.McpRegistration
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Registers the `arend-daemons` MCP server with the agents installed when the IDE's daemon starts, as
 * `arend -d` does, so that an agent started anywhere has the running daemons with no CLI installed.
 * The server runs from the plugin's own jars on the IDE's JVM, with Jackson, which the plugin takes
 * from the IDE, from the IDE's jars. A start after an update of the IDE or the plugin registers the
 * new paths; one that finds the agents running the same command leaves them alone. What it wrote
 * is said in a balloon, with how to take it back out.
 */
object ArendMcpRegistration {
    private val LOG = logger<ArendMcpRegistration>()

    /** The Jackson classes [DaemonMcpServer] needs, one from each of its jars. */
    private val JACKSON = listOf(
        "com/fasterxml/jackson/databind/ObjectMapper.class",
        "com/fasterxml/jackson/core/JsonParser.class",
        "com/fasterxml/jackson/annotation/JsonAutoDetect.class"
    )

    /** Registers the server off the caller's thread: an agent's CLI may take seconds to answer. */
    fun register(project: Project) {
        // A test must not rewrite the agents' configs of whoever runs it.
        if (ApplicationManager.getApplication().isUnitTestMode) return
        ApplicationManager.getApplication().executeOnPooledThread { registerNow(project) }
    }

    /** One at a time: a second project's start then finds the agents registered already. */
    @Synchronized
    private fun registerNow(project: Project) {
        val command = command() ?: return
        val result = McpRegistration.register(command)
        if (project.isDisposed) return
        if (result.registered.isNotEmpty()) {
            // A balloon, not the event log alone: the IDE has just written to other programs' configs.
            NotificationGroupManager.getInstance().getNotificationGroup("Arend Daemon")
                .createNotification("Arend daemon", "Registered the ${McpRegistration.SERVER_NAME} MCP server in the user configs of " +
                    result.registered.joinToString(", ") + ", so that agents started from now on can use the Arend daemons. " +
                    "<code>arend --mcp-unregister</code> removes it from them.", NotificationType.INFORMATION)
                .notify(project)
        }
        for (failure in result.failures) {
            NotificationGroupManager.getInstance().getNotificationGroup("Arend Warning Messages")
                .createNotification("Arend daemon", failure.replaceFirstChar { it.uppercase() } + ".", NotificationType.WARNING)
                .notify(project)
        }
    }

    /**
     * [DaemonMcpServer] on the IDE's JVM, from every jar of the plugin -- the directory's `*`, so
     * that a jar renamed by an update is still on it -- and Jackson's; null when the plugin is not
     * installed from jars, as when it runs from its classes.
     */
    private fun command(): List<String>? {
        val lib = PluginManager.getPluginByClass(ArendMcpRegistration::class.java)?.pluginPath?.resolve("lib")
        if (lib == null || !Files.isDirectory(lib)) {
            LOG.info("Not registering the ${McpRegistration.SERVER_NAME} MCP server: the plugin has no lib directory")
            return null
        }
        // The IDE's own, from its boot classpath: the plugin's class loader finds the copy of a
        // plugin it depends on first, which goes when that plugin is updated or removed.
        val jackson = JACKSON.map { PathManager.getResourceRoot(PathManager::class.java, "/$it") }
        if (jackson.any { it == null }) {
            LOG.warn("Not registering the ${McpRegistration.SERVER_NAME} MCP server: Jackson is not found")
            return null
        }
        val classpath = listOf(lib.toString() + File.separator + "*") + jackson.filterNotNull().distinct()
        return McpRegistration.command(Path.of(System.getProperty("java.home")), classpath, DaemonMcpServer::class.java.name)
    }
}
