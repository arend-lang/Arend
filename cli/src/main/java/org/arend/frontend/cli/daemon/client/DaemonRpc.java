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
import java.util.UUID;

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
    return run(libRef, libDirsForward, op, null);
  }

  /** @param syntheticSrcDir  non-null → resolve via {@link DaemonPaths#resolveSynthetic(Path)}. */
  public static int run(String libRef, List<Path> libDirsForward, String op, Path syntheticSrcDir) {
    DaemonPaths paths = syntheticSrcDir != null
        ? DaemonPaths.resolveSynthetic(syntheticSrcDir)
        : DaemonPaths.resolveByName(libRef, libDirsForward);
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
      return invokeWithCancelHook(client, op, Map.of(), DaemonRpc::printFrame);
    } catch (IOException e) {
      System.err.println("[ERROR] " + op + ": cannot reach daemon at " + lf.socketPath + ": " + e.getMessage());
      return 1;
    }
  }

  /**
   * Wrap {@link DaemonClient#invoke} in a JVM shutdown hook that sends a cancel for the
   * in-flight request id when the client process is terminated (Ctrl-C, SIGTERM). On
   * normal completion the hook is removed; if registration races with shutdown (hook
   * adds throw {@link IllegalStateException} once shutdown has started) we skip the
   * hook and proceed without cancel coverage.
   */
  private static int invokeWithCancelHook(DaemonClient client, String op,
                                          Map<String, Object> extra,
                                          java.util.function.Consumer<Map<String, Object>> onFrame)
      throws IOException {
    String id = UUID.randomUUID().toString();
    Thread hook = new Thread(() -> {
      try {
        System.err.println("[arend] interrupted; sending cancel to daemon (taskId=" + id + ")");
        System.err.flush();
        client.sendCancel(id);
      } catch (IOException e) {
        // Daemon may already be gone, or the connection is broken; nothing useful to do.
        System.err.println("[arend] cancel send failed: " + e.getMessage());
      }
    }, "arend-daemon-cancel-" + id);
    boolean registered;
    try {
      Runtime.getRuntime().addShutdownHook(hook);
      registered = true;
    } catch (IllegalStateException e) {
      registered = false;
    }
    try {
      return client.invoke(id, op, extra, onFrame);
    } finally {
      if (registered) {
        try { Runtime.getRuntime().removeShutdownHook(hook); }
        catch (IllegalStateException ignored) {}
      }
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
      int rc = invokeWithCancelHook(client, "cli", Map.of("args", Arrays.asList(origArgs)),
          DaemonRpc::printFrame);
      return OptionalInt.of(rc);
    } catch (IOException e) {
      System.err.println("[WARN] daemon at " + lf.socketPath + " is unreachable (" + e.getMessage()
          + "); running locally");
      return OptionalInt.empty();
    }
  }

  /**
   * First positional that resolves to a library config; else cwd's arend.yaml; else a
   * synthetic daemon rooted at the cwd if one has already been started there
   * (detected by the presence of {@code ./.arend/daemon.lock}).
   */
  private static DaemonPaths findLibrary(List<String> positional, List<Path> libDirs) {
    for (String pos : positional) {
      DaemonPaths d = DaemonPaths.resolveByName(pos, libDirs);
      if (d != null) return d;
    }
    DaemonPaths cwdConfig = DaemonPaths.resolve(Paths.get("."));
    if (cwdConfig != null) return cwdConfig;
    // No arend.yaml in cwd, but a synthetic daemon may have been started here:
    // check for the lock file under <cwd>/.arend/.
    DaemonPaths synthetic = DaemonPaths.resolveSynthetic(Paths.get("."));
    if (Files.exists(synthetic.lockFile)) return synthetic;
    return null;
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
      Object libCfg = frame.get("libraryConfig");
      if (libCfg != null) System.out.println("libraryConfig:   " + libCfg);
      Object locked = frame.get("lockedFlags");
      if (locked instanceof Map<?, ?> m) {
        if (m.isEmpty()) {
          System.out.println("lockedFlags:     (none)");
        } else {
          System.out.println("lockedFlags:");
          for (Map.Entry<?, ?> e : m.entrySet()) {
            System.out.println("  " + e.getKey() + " = " + e.getValue());
          }
        }
      }
    } else if ("cancel-ack".equals(kind)) {
      System.out.println("cancel accepted: " + frame.get("accepted"));
    } else if ("error".equals(kind)) {
      System.err.println("[DAEMON ERROR] " + frame.get("message"));
    } else {
      System.out.println(frame);
    }
  }
}
