package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.client.DaemonRpc;

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
    /** A lock for this library whose process is gone. */
    REMOVE_STALE,
    /** A lock for another library whose process is gone. */
    REMOVE_STALE_MISMATCH,
    /**
     * A lock for another library whose process is <em>running</em>. It is not ours to stop, and
     * removing its lock would strand it: the lock is the only record of where it listens.
     */
    REFUSE_LIVE_MISMATCH,
    /** Our daemon, running: ask it to shut down, then escalate. */
    SIGNAL
  }

  static Verdict inspectLock(LockFile.Read existing, String expectedHash,
                             java.util.function.Predicate<LockFile> alive) {
    if (existing instanceof LockFile.Read.Absent) return Verdict.NOTHING_TO_DO;
    if (existing instanceof LockFile.Read.Unreadable) return Verdict.UNREADABLE_LOCK;
    LockFile lf = ((LockFile.Read.Found) existing).lock();
    boolean live = alive.test(lf);
    if (!lf.libraryHash().equals(expectedHash)) {
      return live ? Verdict.REFUSE_LIVE_MISMATCH : Verdict.REMOVE_STALE_MISMATCH;
    }
    return live ? Verdict.SIGNAL : Verdict.REMOVE_STALE;
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

    LockFile.Read existing = LockFile.read(paths.lockFile);
    Verdict verdict = inspectLock(existing, paths.libraryHash,
        candidate -> ProcessHandle.of(candidate.pid()).isPresent());
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
      case REMOVE_STALE_MISMATCH -> {
        System.err.println("[WARN]  -d stop: lock file at " + paths.lockFile
            + " is for a different library and its process is gone; removing");
        LockFile.deleteQuietly(paths.lockFile);
        return 0;
      }
      case REFUSE_LIVE_MISMATCH -> {
        LockFile other = lockOf(existing);
        System.err.println("[ERROR] -d stop: " + paths.lockFile + " belongs to a running daemon"
            + " (PID " + other.pid() + ") serving " + other.libraryPath() + ", not this library."
            + " Removing its lock would leave it running and unreachable; stop it from its own"
            + " directory instead.");
        return 1;
      }
      case REMOVE_STALE -> {
        System.err.println("[WARN]  -d stop: lock for PID " + lockOf(existing).pid()
            + " is stale (process dead); removing");
        LockFile.deleteQuietly(paths.lockFile);
        return 0;
      }
      case SIGNAL -> { }
    }
    LockFile lf = lockOf(existing);
    Optional<ProcessHandle> ph = ProcessHandle.of(lf.pid());
    if (ph.isEmpty()) {
      LockFile.deleteQuietly(paths.lockFile);
      return 0;
    }

    System.out.println("Stopping Arend daemon for " + paths.libraryConfig + " (PID " + lf.pid() + ")");

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

    boolean gone = ProcessKill.terminateTree(List.of(ph.get()),
        () -> System.err.println("[WARN]  -d stop: PID " + lf.pid() + " did not exit gracefully"
            + " within " + ProcessKill.GRACEFUL_TIMEOUT_SECONDS + "s; escalating to SIGKILL"));
    if (!gone) {
      System.err.println("[ERROR] -d stop: PID " + lf.pid() + " still alive after SIGKILL");
      return 1;
    }
    LockFile.deleteQuietly(paths.lockFile);
    System.out.println("Daemon stopped (PID " + lf.pid() + ")");
    return 0;
  }
}
