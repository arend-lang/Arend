package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.client.DaemonRpc;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Parent-side: stop a running daemon.
 *
 * <p>Sequence:
 * <ol>
 *   <li>Read lock; bail with success if absent (no daemon to stop).</li>
 *   <li>Try socket-side {@code shutdown} via {@link DaemonRpc}. If it succeeds, wait
 *       briefly for the lock file to vanish (proves the JVM exited and the shutdown
 *       hook ran).</li>
 *   <li>Otherwise (or if the RPC times out without the lock disappearing), fall back to
 *       OS-level {@code destroy()} → {@code destroyForcibly()}.</li>
 * </ol>
 */
public final class DaemonStop {
  public static final int GRACEFUL_TIMEOUT_SECONDS = 30;
  public static final int FORCEFUL_TIMEOUT_SECONDS = 5;

  private DaemonStop() {}

  public static int run(String libRef, List<Path> libDirsForward) {
    return run(libRef, libDirsForward, null);
  }

  /** @param syntheticSrcDir  non-null → resolve via {@link DaemonPaths#resolveSynthetic(Path)}. */
  public static int run(String libRef, List<Path> libDirsForward, Path syntheticSrcDir) {
    DaemonPaths paths = syntheticSrcDir != null
        ? DaemonPaths.resolveSynthetic(syntheticSrcDir)
        : DaemonPaths.resolveByName(libRef, libDirsForward);
    if (paths == null) {
      System.err.println("[ERROR] -d stop: could not resolve library reference '" + libRef + "'");
      return 1;
    }

    LockFile.Read existing = LockFile.read(paths.lockFile);
    if (existing instanceof LockFile.Read.Absent) {
      System.out.println("No Arend daemon running for " + paths.libraryConfig);
      return 0;
    }
    if (existing instanceof LockFile.Read.Unreadable unreadable) {
      // A stop that cannot read the lock cannot signal the daemon either. Removing the file
      // would leave it running and unfindable, so say what is wrong and change nothing.
      System.err.println("[ERROR] lock file at " + paths.lockFile + " cannot be read ("
          + unreadable.reason() + "); cannot tell whether a daemon is running.");
      return 1;
    }
    LockFile lf = ((LockFile.Read.Found) existing).lock();

    if (!lf.libraryHash().equals(paths.libraryHash)) {
      System.err.println("[WARN]  -d stop: lock file at " + paths.lockFile
          + " is for a different library; removing without signalling");
      LockFile.deleteQuietly(paths.lockFile);
      return 0;
    }

    Optional<ProcessHandle> ph = ProcessHandle.of(lf.pid());
    if (ph.isEmpty()) {
      System.err.println("[WARN]  -d stop: lock for PID " + lf.pid() + " is stale (process dead); removing");
      LockFile.deleteQuietly(paths.lockFile);
      return 0;
    }

    System.out.println("Stopping Arend daemon for " + paths.libraryConfig + " (PID " + lf.pid() + ")");

    // Try the clean path: socket-side `shutdown`. The daemon replies done, the worker
    // pulls the sentinel, the JVM exits, the shutdown hook removes the lock.
    int rpcRc = DaemonRpc.run(libRef, libDirsForward, "shutdown", syntheticSrcDir);
    if (rpcRc == 0) {
      if (DaemonRpc.waitForLockGone(paths.lockFile, GRACEFUL_TIMEOUT_SECONDS)) {
        System.out.println("Daemon stopped (PID " + lf.pid() + ")");
        return 0;
      }
      System.err.println("[WARN]  -d stop: daemon ack'd shutdown but PID " + lf.pid()
          + " did not exit within " + GRACEFUL_TIMEOUT_SECONDS + "s; falling back to SIGTERM");
    } else if (rpcRc == DaemonRpc.NO_DAEMON) {
      // Stale lock already cleaned up by DaemonRpc.
      return 0;
    } else {
      System.err.println("[WARN]  -d stop: socket-side shutdown failed (rc=" + rpcRc
          + "); falling back to SIGTERM");
    }

    ph.get().destroy();
    if (DaemonRpc.waitForLockGone(paths.lockFile, GRACEFUL_TIMEOUT_SECONDS)) {
      System.out.println("Daemon stopped (PID " + lf.pid() + ")");
      return 0;
    }

    System.err.println("[WARN]  -d stop: PID " + lf.pid() + " did not exit gracefully within "
        + GRACEFUL_TIMEOUT_SECONDS + "s; escalating to SIGKILL");
    ph.get().destroyForcibly();
    DaemonRpc.waitForLockGone(paths.lockFile, FORCEFUL_TIMEOUT_SECONDS);
    LockFile.deleteQuietly(paths.lockFile);

    if (ProcessHandle.of(lf.pid()).isPresent()) {
      System.err.println("[ERROR] -d stop: PID " + lf.pid() + " still alive after SIGKILL");
      return 1;
    }
    System.out.println("Daemon killed (PID " + lf.pid() + ")");
    return 0;
  }
}
