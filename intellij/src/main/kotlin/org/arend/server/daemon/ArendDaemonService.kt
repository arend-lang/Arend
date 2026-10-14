package org.arend.server.daemon

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.util.Alarm
import org.arend.daemon.DaemonPaths
import org.arend.daemon.LockFile
import org.arend.daemon.server.DaemonServer
import org.arend.daemon.server.SocketBinder
import org.arend.server.ArendServerListener
import org.arend.server.ArendServerService
import org.arend.server.daemon.mcp.ArendMcpRegistration
import org.arend.settings.ArendProjectSettings
import org.arend.util.FileUtils
import org.arend.util.findLibrary
import java.io.IOException
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lets the project's server answer as an Arend daemon, so that `arend` and agents reach the warm
 * server the IDE already holds instead of starting one of their own.
 *
 * The daemon is the IDE itself: its lock names the IDE's PID with [LockFile.HOST_INTELLIJ], which is
 * what keeps `arend --daemon-stop` from signalling it. It serves every library of the project it can
 * claim -- one no other daemon serves -- and follows the project as libraries come and go: a library
 * another daemon already serves is left out with a warning, and one that goes away is released.
 * Nothing starts when, at the start, another daemon serves one of the project's libraries.
 */
@Service(Service.Level.PROJECT)
class ArendDaemonService(private val project: Project) : Disposable {
    /**
     * What the Daemon tab shows of a running daemon; replaced whole on every change. Why a start
     * failed goes to a notification.
     */
    data class State(
        val socket: String,
        val libraries: List<String>,
        val shared: List<String>,
        /** Libraries of the project left out, to why. */
        val skipped: Map<String, String>,
        val startedAt: Long,
        val activity: DaemonServer.Activity
    )

    private class Running(val server: DaemonServer, val home: DaemonPaths, val socket: String) {
        /** The canonical arend.yaml or zip of every library served, to its name. */
        @Volatile var served: Map<String, String> = emptyMap()
        @Volatile var lock: LockFile? = null

        /**
         * Removes the traces when the IDE goes without closing its projects, as it does on SIGTERM;
         * a client would find a dead PID and clean up after it, but only once it looked.
         */
        val shutdownHook = Thread({ lock?.removeTraces() }, "arend-ide-daemon-cleanup")
    }

    @Volatile private var running: Running? = null
    /** Null while the daemon is not running. */
    @Volatile var state: State? = null
        private set
    private val stateListeners = CopyOnWriteArrayList<(State?) -> Unit>()
    private val librariesAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    /** Libraries already warned about being someone else's, so that a library update does not warn again. */
    private val warnedAbout = HashSet<String>()

    init {
        project.service<ArendServerService>().server.addListener(object : ArendServerListener {
            override fun onLibraryUpdated(libraryName: String) = librariesChanged()

            override fun onLibraryRemoved(libraryName: String) = librariesChanged()
        })
    }

    var startOnOpen: Boolean
        get() = project.service<ArendProjectSettings>().data.startDaemon
        set(value) {
            project.service<ArendProjectSettings>().data.startDaemon = value
        }

    fun addStateListener(parent: Disposable, listener: (State?) -> Unit) {
        stateListeners.add(listener)
        Disposer.register(parent) { stateListeners.remove(listener) }
    }

    /** Starts the daemon if the project asks for it on opening; called once its libraries are registered. */
    fun startIfEnabled() {
        if (startOnOpen) start(false)
    }

    /**
     * Starts the daemon on the project's libraries, unless another daemon serves one of them; then it
     * warns instead. [explicit] is a start the user asked for, which says why when there is nothing to serve.
     */
    @Synchronized
    fun start(explicit: Boolean): Boolean {
        if (running != null) return true
        val libraries = projectLibraries()
        val owned = libraries.keys.filter { it.canOwn() }
        if (owned.isEmpty()) {
            if (explicit) warn("No library of the project can hold a daemon: one needs an arend.yaml outside ${DaemonPaths.sharedLibrariesRoot()}.")
            return false
        }

        val taken = owned.mapNotNull { paths -> owner(paths)?.let { paths to it } }
        if (taken.isNotEmpty()) {
            warn("Cannot start the Arend daemon: " + taken.joinToString("; ") { (paths, owner) ->
                "${libraries[paths]} is held by $owner"
            } + ". Stop that daemon first.")
            return false
        }

        val home = owned.first()
        val daemon: Running
        try {
            // The socket's: the other libraries get theirs with the lock, in update.
            home.ensureArendDir()
            val bound = SocketBinder.bind(home.socketFile, home.libraryHash)
            val server = DaemonServer(bound, { running?.lock?.libraries() ?: emptyList() }, emptyMap(), LockFile.HOST_INTELLIJ,
                IdeDaemonCommands(project) { running?.served ?: emptyMap() }, ::stop, ::describe, ::activityChanged)
            daemon = Running(server, home, bound.address())
            running = daemon
            server.start()
            Runtime.getRuntime().addShutdownHook(daemon.shutdownHook)
        } catch (e: IOException) {
            // Only creating the directory and binding the socket throw it, before anything runs.
            warn("Cannot start the Arend daemon: ${e.message}")
            return false
        }
        update(daemon, libraries)
        ArendMcpRegistration.register(project)
        return true
    }

    /** Stops the daemon and removes every trace of it: its locks and its socket. */
    @Synchronized
    fun stop() {
        val daemon = running ?: return
        running = null
        daemon.server.close()
        try {
            Runtime.getRuntime().removeShutdownHook(daemon.shutdownHook)
        } catch (_: IllegalStateException) {
            // The JVM is going down already, and the hook does the same.
        }
        daemon.lock?.removeTraces()
        warnedAbout.clear()
        setState(null)
    }

    override fun dispose() = stop()

    /** A request as the `arend` command it amounts to, with the libraries by name rather than path. */
    private fun describe(args: List<String>?): String {
        if (args == null) return "refresh"
        val served = running?.served ?: emptyMap()
        return args.joinToString(" ", prefix = "arend ") { served[it] ?: it }
    }

    @Synchronized
    private fun activityChanged() {
        val daemon = running ?: return
        state?.let { setState(it.copy(activity = daemon.server.activity())) }
    }

    private fun librariesChanged() {
        if (running == null) return
        librariesAlarm.cancelAllRequests()
        librariesAlarm.addRequest({ refreshLibraries() }, 500)
    }

    @Synchronized
    private fun refreshLibraries() {
        val daemon = running ?: return
        val libraries = projectLibraries()
        if (daemon.home !in libraries.keys) {
            // The socket lives in the home library's .arend/: without it, the daemon starts over.
            stop()
            start(false)
            return
        }
        update(daemon, libraries)
    }

    /**
     * Claims what of [libraries] the daemon can, and rewrites every copy of its lock to say so: in
     * the registry first, the home library last, as `arend -d` does, so that a client finding the
     * home lock finds the rest too. Owned libraries no longer served lose their copy.
     */
    private fun update(daemon: Running, libraries: Map<DaemonPaths, String>) {
        val skipped = LinkedHashMap<String, String>()
        val owned = ArrayList<DaemonPaths>()
        val shared = ArrayList<DaemonPaths>()
        for (paths in libraries.keys) {
            if (!paths.canOwn()) {
                shared.add(paths)
                continue
            }
            val owner = owner(paths)
            if (owner == null) {
                owned.add(paths)
                continue
            }
            val name = libraries.getValue(paths)
            skipped[name] = "held by $owner"
            if (warnedAbout.add(paths.libraryConfig.toString())) {
                warn("The Arend daemon of the project does not serve $name: it is held by $owner.")
            }
        }
        owned.sortBy { if (it == daemon.home) 0 else 1 }

        val lock = LockFile.ofThisProcess(daemon.home, owned.map { it.libraryConfig.toString() },
            shared.map { it.libraryConfig.toString() }, daemon.server.startedAt(), daemon.socket, LockFile.HOST_INTELLIJ)
        val previous = daemon.lock
        daemon.served = (owned + shared).associate { it.libraryConfig.toString() to libraries.getValue(it) }
        daemon.lock = lock
        try {
            owned.forEach { it.ensureArendDir() }
            lock.writeEverywhere()
        } catch (e: IOException) {
            warn("The Arend daemon could not write its lock: ${e.message}")
        }
        if (previous != null) {
            (previous.lockFiles() - lock.lockFiles().toSet()).forEach(lock::deleteIfOurs)
        }
        setState(State(daemon.socket, lock.libraries(), lock.shared(), skipped, daemon.server.startedAt(), daemon.server.activity()))
    }

    /**
     * What holds [paths]' lock other than this daemon, said for a person: a live daemon, or a lock
     * that cannot be read and so might be one. Null when the library is free; a dead daemon's lock
     * is removed on the way.
     */
    private fun owner(paths: DaemonPaths): String? {
        val lock = when (val read = LockFile.read(paths.lockFile)) {
            is LockFile.Read.Unreadable -> return "a lock that cannot be read (${read.reason()}) at ${paths.lockFile}"
            is LockFile.Read.Found -> read.lock()
            else -> return null
        }
        if (lock.pid() == ProcessHandle.current().pid() && lock.libraryHash() == running?.home?.libraryHash) return null
        if (!lock.processAlive()) {
            LockFile.deleteQuietly(paths.lockFile)
            return null
        }
        return "the daemon (PID ${lock.pid()})" + if (lock.isHostedByIde) " inside ${lock.host()}" else ""
    }

    /** Every library the project's server holds that lives in a file, to its name. */
    private fun projectLibraries(): Map<DaemonPaths, String> {
        val server = project.service<ArendServerService>().server
        val files = runReadAction {
            server.libraries.mapNotNull { name ->
                // The server keeps its own copy of a library, so the file it came from is the project's to say.
                val root = project.findLibrary(name)?.root ?: return@mapNotNull null
                val file = (if (root.fileSystem is JarFileSystem) JarFileSystem.getInstance().getVirtualFileForJar(root)
                    else root.findChild(FileUtils.LIBRARY_CONFIG_FILE)) ?: return@mapNotNull null
                file.path to name
            }
        }
        // Outside the read action: resolving a path reads the disk.
        val result = LinkedHashMap<DaemonPaths, String>()
        for ((path, name) in files) DaemonPaths.resolve(Paths.get(path))?.let { result[it] = name }
        return result
    }

    private fun setState(newState: State?) {
        state = newState
        ApplicationManager.getApplication().invokeLater({ stateListeners.forEach { it(newState) } }, project.disposed)
    }

    private fun warn(message: String) {
        NotificationGroupManager.getInstance().getNotificationGroup("Arend Warning Messages")
            .createNotification("Arend daemon", message, NotificationType.WARNING)
            .notify(project)
    }
}
