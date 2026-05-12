package org.arend.frontend.cli.daemon.client;

import org.arend.frontend.cli.daemon.DaemonPaths;
import org.arend.frontend.cli.daemon.LockFile;
import org.arend.frontend.cli.daemon.server.SocketBinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Thin "find a daemon and send one op" wrapper used by the client CLI flags
 * ({@code --daemon-ping}, {@code --daemon-status}, {@code --daemon-stop}).
 *
 * <p>Resolves the library and reads its lock file; if the lock + PID + hash all check
 * out, opens a {@link DaemonClient}, fires the op, prints any non-{@code done} frames,
 * returns the daemon's exit code. Returns {@link #NO_DAEMON} if no lock or the lock
 * points at a dead/wrong process; the caller decides how to react (e.g. {@code stop}
 * treats this as success, {@code ping} as "no daemon to talk to").
 */
public final class DaemonRpc {
  public static final int NO_DAEMON = -1;

  private DaemonRpc() {}

  /**
   * Convenience: resolve, validate, connect, invoke. {@code op} is one of
   * {@code "ping" / "status" / "shutdown"}. Pretty-prints each frame for the user.
   */
  public static int run(String libRef, List<Path> libDirsForward, String op) {
    DaemonPaths paths = DaemonPaths.resolveByName(libRef, libDirsForward);
    if (paths == null) {
      System.err.println("[ERROR] " + op + ": could not resolve library reference '" + libRef + "'");
      return 1;
    }
    Optional<LockFile> lockOpt = LockFile.read(paths.lockFile);
    if (lockOpt.isEmpty()) return NO_DAEMON;
    LockFile lf = lockOpt.get();
    if (!lf.libraryHash.equals(paths.libraryHash)) {
      System.err.println("[WARN]  lock at " + paths.lockFile + " is for a different library; ignoring");
      return NO_DAEMON;
    }
    if (ProcessHandle.of(lf.pid).isEmpty()) {
      LockFile.deleteQuietly(paths.lockFile);
      return NO_DAEMON;
    }
    if (lf.socketPath == null || lf.socketPath.isEmpty()) {
      System.err.println("[ERROR] daemon at PID " + lf.pid + " has no socket bound (old daemon? restart it)");
      return 1;
    }

    SocketBinder.Address addr;
    try {
      addr = SocketBinder.parseAddress(lf.socketPath);
    } catch (IllegalArgumentException e) {
      System.err.println("[ERROR] invalid socket address in lock: " + e.getMessage());
      return 1;
    }

    try (DaemonClient client = DaemonClient.connect(addr)) {
      return client.invoke(op, Map.of(), DaemonRpc::printFrame);
    } catch (IOException e) {
      System.err.println("[ERROR] " + op + ": cannot reach daemon at " + lf.socketPath + ": " + e.getMessage());
      return 1;
    }
  }

  /**
   * Client-side auto-routing entry: if a daemon serves the library implied by
   * {@code positional} (or, failing that, by the cwd), connect to it and send a
   * {@code cli} op carrying the original CLI args. Returns the daemon's exit code on
   * success, or empty if no daemon is reachable (caller falls back to in-process
   * execution).
   *
   * <p>Connection failures, malformed lock files, hash mismatch, or a dead PID all
   * return empty without consulting the daemon. A dead-PID lock file is removed in
   * passing so the next start-daemon invocation is clean.
   */
  public static OptionalInt tryRouteCli(String[] origArgs, List<String> positional, List<Path> libDirs) {
    DaemonPaths paths = findLibrary(positional, libDirs);
    if (paths == null) return OptionalInt.empty();

    Optional<LockFile> lockOpt = LockFile.read(paths.lockFile);
    if (lockOpt.isEmpty()) return OptionalInt.empty();
    LockFile lf = lockOpt.get();

    if (!lf.libraryHash.equals(paths.libraryHash)) return OptionalInt.empty();
    if (ProcessHandle.of(lf.pid).isEmpty()) {
      LockFile.deleteQuietly(paths.lockFile);
      return OptionalInt.empty();
    }
    if (lf.socketPath == null || lf.socketPath.isEmpty()) return OptionalInt.empty();

    SocketBinder.Address addr;
    try {
      addr = SocketBinder.parseAddress(lf.socketPath);
    } catch (IllegalArgumentException e) {
      return OptionalInt.empty();
    }

    try (DaemonClient client = DaemonClient.connect(addr)) {
      int rc = client.invoke("cli", Map.of("args", Arrays.asList(origArgs)), DaemonRpc::printFrame);
      return OptionalInt.of(rc);
    } catch (IOException e) {
      System.err.println("[WARN] daemon at " + lf.socketPath + " is unreachable (" + e.getMessage()
          + "); running locally");
      return OptionalInt.empty();
    }
  }

  /** First positional that resolves to a library config; else cwd's arend.yaml. */
  private static DaemonPaths findLibrary(List<String> positional, List<Path> libDirs) {
    for (String pos : positional) {
      DaemonPaths d = DaemonPaths.resolveByName(pos, libDirs);
      if (d != null) return d;
    }
    return DaemonPaths.resolve(Paths.get("."));
  }

  /** Wait for the lock file to vanish (signals daemon JVM exited and hook ran). */
  public static boolean waitForLockGone(Path lockPath, int timeoutSeconds) {
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

  /** Pretty-print one non-done frame for stdout. */
  private static void printFrame(Map<String, Object> frame) {
    Object kind = frame.get("kind");
    if ("stdout".equals(kind)) {
      System.out.print(String.valueOf(frame.getOrDefault("data", "")));
      System.out.flush();
    } else if ("stderr".equals(kind)) {
      System.err.print(String.valueOf(frame.getOrDefault("data", "")));
      System.err.flush();
    } else if ("state".equals(kind)) {
      System.out.println("state: " + frame.get("state"));
    } else if ("status".equals(kind)) {
      System.out.println("state:           " + frame.get("state"));
      System.out.println("queueDepth:      " + frame.get("queueDepth"));
      System.out.println("uptimeMs:        " + frame.get("uptimeMs"));
      System.out.println("currentTaskId:   " + frame.get("currentTaskId"));
      System.out.println("protocolVersion: " + frame.get("protocolVersion"));
    } else if ("cancel-ack".equals(kind)) {
      System.out.println("cancel accepted: " + frame.get("accepted"));
    } else if ("error".equals(kind)) {
      System.err.println("[DAEMON ERROR] " + frame.get("message"));
    } else {
      System.out.println(frame);
    }
  }
}
