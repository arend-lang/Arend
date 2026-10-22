package org.arend.frontend.cli.daemon;

import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.client.DaemonRpc;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Parent-side: stop a running daemon. Ask it over the socket and wait for its lock to vanish,
 * which is what proves the JVM exited and its shutdown hook ran; failing that, signal the process
 * through {@link ProcessKill}.
 */
public final class DaemonStop {
  private DaemonStop() {}

  /**
   * What an existing lock file means for a stop attempt. Separated from {@link #run} so the
   * branches that decide whether a live daemon keeps its lock can be exercised without killing a
   * process to find out.
   */
  enum Verdict {
    /** No lock: nothing to stop. */
    NOTHING_TO_DO,
    /** A lock we cannot parse; we can neither signal it nor safely remove it. */
    UNREADABLE_LOCK,
    /**
     * A library's copy no registry entry confirms: whatever process it names is only its word, and
     * is never signalled on it. The copy is removed; a daemon still running behind it is the user's
     * to stop by hand.
     */
    REMOVE_UNREGISTERED,
    /** A lock for this library whose process is gone. */
    REMOVE_STALE,
    /** Our daemon, running: ask it to shut down, then escalate. */
    SIGNAL
  }

  /** @param existing what {@link LockFile#lookup} found: a lock it found serves the library. */
  static Verdict inspectLock(LockFile.Read existing, java.util.function.Predicate<LockFile> alive) {
    if (existing instanceof LockFile.Read.Absent) return Verdict.NOTHING_TO_DO;
    if (existing instanceof LockFile.Read.Unreadable) return Verdict.UNREADABLE_LOCK;
    if (existing instanceof LockFile.Read.Unregistered) return Verdict.REMOVE_UNREGISTERED;
    return alive.test(((LockFile.Read.Found) existing).lock()) ? Verdict.SIGNAL : Verdict.REMOVE_STALE;
  }

  private static LockFile lockOf(LockFile.Read read) {
    return ((LockFile.Read.Found) read).lock();
  }

  public static int run(String libRef, List<Path> libDirsForward) {
    DaemonPaths paths = DaemonPaths.resolveByName(libRef, libDirsForward);
    if (paths == null) {
      System.err.println("[ERROR] -d stop: could not resolve library reference '" + libRef + "'");
      return 1;
    }

    if (!paths.canOwn()) {
      // Any number of daemons may have loaded it, and stopping whichever was found first would
      // stop some other project's daemon. The one to stop is named by a library it owns.
      System.err.println("[ERROR] --daemon-stop: " + paths.libraryConfig + (paths.isShared()
          ? " is under " + DaemonPaths.sharedLibrariesRoot() + " and may be loaded by several daemons."
          : " is a zip library, which no daemon owns.") + " Stop the one you mean from a library it owns.");
      return 1;
    }

    LockFile.Read existing = LockFile.lookup(paths);
    Verdict verdict = inspectLock(existing, LockFile::processAlive);
    switch (verdict) {
      case NOTHING_TO_DO -> {
        System.out.println("No Arend daemon running for " + paths.libraryConfig);
        return 0;
      }
      case UNREADABLE_LOCK -> {
        // A stop that cannot read the lock cannot signal the daemon either. Removing the file
        // would leave it running and unfindable, so say what is wrong and change nothing.
        String reason = ((LockFile.Read.Unreadable) existing).reason();
        System.err.println("[ERROR] lock file at " + paths.lockFile + " cannot be read ("
            + reason + "); cannot tell whether a daemon is running.");
        return 1;
      }
      case REMOVE_UNREGISTERED -> {
        long pid = ((LockFile.Read.Unregistered) existing).copy().pid();
        System.err.println("[WARN]  -d stop: lock file at " + paths.lockFile + " is not confirmed by the registry in "
            + DaemonPaths.registryDir() + "; removing it. Its PID " + pid + " is not signalled on its word: if a"
            + " daemon still runs there, stop that process yourself.");
        LockFile.deleteQuietly(paths.lockFile);
        return 0;
      }
      case REMOVE_STALE -> {
        System.err.println("[WARN]  -d stop: lock for PID " + lockOf(existing).pid()
            + " is stale (process dead); removing");
        // Every copy: a daemon of several libraries left one in each, and one in the registry.
        lockOf(existing).deleteAllCopies();
        return 0;
      }
      case SIGNAL -> { }
    }
    LockFile lf = lockOf(existing);
    // The process the lock names, not merely one with its PID: that would be signalled below.
    Optional<ProcessHandle> ph = lf.process();
    if (ph.isEmpty()) {
      lf.deleteAllCopies();
      return 0;
    }

    // The daemon as a whole, whichever of its libraries named it: they share one process.
    System.out.println("Stopping Arend daemon for " + String.join(", ", lf.libraries()) + " (PID " + lf.pid() + ")");

    // The clean path: socket-side `shutdown`. The daemon replies done, the worker pulls the
    // sentinel, the JVM exits, the shutdown hook removes the lock. Sent to the library this
    // method already resolved, not the reference it was handed: resolving a second time re-walks
    // the libDirs, warns again, and can land on a different daemon than the verdict came from.
    //
    // Only a clean ack followed by the lock disappearing counts as stopped. Anything else --
    // DaemonRpc.NO_DAEMON included, which is what a protocol-version mismatch returns -- means
    // the daemon is still there. Reporting those as success is how a stale daemon becomes
    // unstoppable: its own warning tells the user to run --daemon-stop, and that command would
    // then do nothing.
    int rpcRc = DaemonRpc.run(paths, "shutdown");
    if (rpcRc == 0 && DaemonRpc.waitForLockGone(paths.lockFile, ProcessKill.GRACEFUL_TIMEOUT_SECONDS)) {
      System.out.println("Daemon stopped (PID " + lf.pid() + ")");
      return 0;
    }
    System.err.println(rpcRc == 0
        ? "[WARN]  -d stop: daemon ack'd shutdown but PID " + lf.pid() + " did not exit within "
            + ProcessKill.GRACEFUL_TIMEOUT_SECONDS + "s; falling back to SIGTERM"
        : "[WARN]  -d stop: socket-side shutdown did not go through (rc=" + rpcRc
            + "); falling back to SIGTERM");

    boolean gone = ProcessKill.terminateTree(ph.get(),
        () -> System.err.println("[WARN]  -d stop: PID " + lf.pid() + " did not exit gracefully"
            + " within " + ProcessKill.GRACEFUL_TIMEOUT_SECONDS + "s; escalating to SIGKILL"));
    if (!gone) {
      System.err.println("[ERROR] -d stop: PID " + lf.pid() + " still alive after SIGKILL");
      return 1;
    }
    lf.deleteAllCopies();
    System.out.println("Daemon stopped (PID " + lf.pid() + ")");
    return 0;
  }
}
