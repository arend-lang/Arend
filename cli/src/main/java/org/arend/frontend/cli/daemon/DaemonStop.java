package org.arend.frontend.cli.daemon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Parent-side: stop a running daemon by reading its lock file's PID and sending SIGTERM.
 * The daemon's JVM shutdown hook deletes the lock file; we poll for that to confirm.
 *
 * <p>In M3 this will be replaced by a socket-side {@code {op: shutdown}} message; M2
 * does it via the OS signal because no socket exists yet.
 */
public final class DaemonStop {
  /** Max time to wait for the daemon to clean up after SIGTERM before escalating. */
  public static final int GRACEFUL_TIMEOUT_SECONDS = 30;
  /** Max time to wait after destroyForcibly() before giving up. */
  public static final int FORCEFUL_TIMEOUT_SECONDS = 5;

  private DaemonStop() {}

  public static int run(String libRef, List<Path> libDirsForward) {
    DaemonPaths paths = DaemonPaths.resolveByName(libRef, libDirsForward);
    if (paths == null) {
      System.err.println("[ERROR] -d stop: could not resolve library reference '" + libRef + "'");
      return 1;
    }

    Optional<LockFile> existing = LockFile.read(paths.lockFile);
    if (existing.isEmpty()) {
      System.out.println("No Arend daemon running for " + paths.libraryConfig);
      return 0;
    }
    LockFile lf = existing.get();

    if (!lf.libraryHash.equals(paths.libraryHash)) {
      System.err.println("[WARN]  -d stop: lock file at " + paths.lockFile
          + " is for a different library; removing without signalling");
      LockFile.deleteQuietly(paths.lockFile);
      return 0;
    }

    Optional<ProcessHandle> ph = ProcessHandle.of(lf.pid);
    if (ph.isEmpty()) {
      System.err.println("[WARN]  -d stop: lock for PID " + lf.pid + " is stale (process dead); removing");
      LockFile.deleteQuietly(paths.lockFile);
      return 0;
    }

    System.out.println("Stopping Arend daemon for " + paths.libraryConfig + " (PID " + lf.pid + ")");
    ph.get().destroy();

    if (waitForLockGone(paths.lockFile, GRACEFUL_TIMEOUT_SECONDS)) {
      System.out.println("Daemon stopped (PID " + lf.pid + ")");
      return 0;
    }

    System.err.println("[WARN]  -d stop: PID " + lf.pid + " did not exit gracefully within "
        + GRACEFUL_TIMEOUT_SECONDS + "s; escalating to SIGKILL");
    ph.get().destroyForcibly();
    waitForLockGone(paths.lockFile, FORCEFUL_TIMEOUT_SECONDS);
    LockFile.deleteQuietly(paths.lockFile);

    if (ProcessHandle.of(lf.pid).isPresent()) {
      System.err.println("[ERROR] -d stop: PID " + lf.pid + " still alive after SIGKILL");
      return 1;
    }
    System.out.println("Daemon killed (PID " + lf.pid + ")");
    return 0;
  }

  private static boolean waitForLockGone(Path lockPath, int timeoutSeconds) {
    long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
    while (System.currentTimeMillis() < deadline) {
      if (!Files.exists(lockPath)) return true;
      try {
        Thread.sleep(150);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return !Files.exists(lockPath);
  }
}
