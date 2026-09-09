package org.arend.frontend.cli.daemon.client;

import org.arend.frontend.cli.daemon.DaemonPaths;
import org.arend.frontend.cli.daemon.LockFile;
import org.arend.frontend.cli.daemon.server.SocketBinder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

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

  /** How long a Ctrl-C waits for the daemon to acknowledge the cancellation before giving up. */
  private static final int CANCEL_ACK_TIMEOUT_SECONDS = 5;

  private DaemonRpc() {}

  /**
   * Resolve, validate, connect, invoke. {@code op} is one of {@code "ping" / "status" /
   * "shutdown" / "refresh"}; each frame is pretty-printed for the user.
   *
   * @param syntheticSrcDir  non-null → resolve via {@link DaemonPaths#resolveSynthetic(Path)}.
   */
  public static int run(String libRef, List<Path> libDirsForward, String op, Path syntheticSrcDir) {
    DaemonPaths paths = syntheticSrcDir != null
        ? DaemonPaths.resolveSynthetic(syntheticSrcDir)
        : DaemonPaths.resolveByName(libRef, libDirsForward);
    if (paths == null) {
      System.err.println("[ERROR] " + op + ": could not resolve library reference '" + libRef + "'");
      return 1;
    }
    return run(paths, op);
  }

  /**
   * As {@link #run(String, List, String, Path)}, for a library the caller has already resolved.
   *
   * <p>A caller that resolved the library to reach a decision must send the op to <em>that</em>
   * library, not to whatever a second resolution finds. Resolving again is not free and not
   * guaranteed to agree: it re-walks the libDirs, prints the shadowing warning a second time,
   * and reads the lock again -- so a symlinked config retargeted in between, or a lock rewritten
   * in between, sends the shutdown to one daemon and the SIGTERM fallback to another.
   */
  public static int run(DaemonPaths paths, String op) {
    LockFile lf = reachableDaemon(paths, true);
    if (lf == null) return NO_DAEMON;

    SocketBinder.Address addr;
    try {
      addr = SocketBinder.parseAddress(lf.socketPath());
    } catch (IllegalArgumentException e) {
      System.err.println("[ERROR] invalid socket address in lock: " + e.getMessage());
      return 1;
    }

    try (DaemonClient client = DaemonClient.connect(addr)) {
      return invokeWithCancelHook(client, op, Map.of(), DaemonRpc::printFrame);
    } catch (IOException e) {
      System.err.println("[ERROR] " + op + ": cannot reach daemon at " + lf.socketPath() + ": " + e.getMessage());
      return 1;
    }
  }

  /**
   * Runs one request with a shutdown hook that tells the daemon to abandon it.
   *
   * <p>Ctrl-C kills the client, not the daemon: without this the typecheck keeps running on the
   * other side, holding the worker against the next request. The hook waits briefly for the
   * cancel to be acknowledged, because the JVM tears down its streams and sockets underneath a
   * shutdown hook and a fire-and-forget write can be discarded before it reaches the socket --
   * which turns "cancelled" into "still running" with nothing said.
   */
  private static int invokeWithCancelHook(DaemonClient client, String op,
                                          Map<String, Object> extra,
                                          Consumer<Map<String, Object>> onFrame) throws IOException {
    String id = UUID.randomUUID().toString();
    // settled means "stop waiting", not "the cancel was accepted" -- accepted carries that, and
    // stays null when the read loop ended without an ack at all. Keeping them apart is the
    // difference between reporting what the daemon said and reporting that it said nothing.
    CountDownLatch settled = new CountDownLatch(1);
    AtomicReference<Boolean> accepted = new AtomicReference<>(null);
    Thread hook = new Thread(() -> {
      try {
        System.err.println("[arend] interrupted; cancelling the daemon task (id=" + id + ")");
        System.err.flush();
        client.sendCancel(id);
        // The reader is the main thread, which trips this when the daemon answers the cancel.
        if (!settled.await(CANCEL_ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          System.err.println("[arend] the daemon did not acknowledge the cancellation in time;"
              + " it may still be working. `arend --daemon-ping` reports its state.");
          return;
        }
        Boolean verdict = accepted.get();
        if (verdict == null) {
          System.err.println("[arend] the command finished before the cancellation reached the daemon.");
        } else if (!verdict) {
          System.err.println("[arend] the daemon had no such task to cancel; it had probably"
              + " just finished. `arend --daemon-ping` reports its state.");
        }
      } catch (IOException e) {
        System.err.println("[arend] could not reach the daemon to cancel: " + e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
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
      return client.invoke(id, op, extra, onFrame, acked -> {
        accepted.set(acked);
        settled.countDown();
      });
    } finally {
      // Releases a hook that is waiting on a request which is already over -- there is nothing
      // left to acknowledge. It deliberately leaves `accepted` null: an IOException out of
      // invoke, or an ordinary completion, is not an acknowledgement, and the old code counting
      // this same latch down was the whole reason an accepted cancel was reported as a timeout.
      settled.countDown();
      if (registered) {
        try {
          Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
          // Shutdown already under way; the hook is running and owns the wait.
        }
      }
    }
  }

  /**
   * Client-side auto-routing entry: if a daemon serves the library this command is about, connect
   * to it and send a {@code cli} op carrying the original CLI args. Returns the daemon's exit
   * code on success, or empty if the command should run in this process instead.
   *
   * <p>Which library that is, and when routing is declined because a positional would not mean
   * the same thing on the far side, is {@link #routeTarget}. Connection failures, malformed lock
   * files, hash mismatch, or a dead PID all return empty without consulting the daemon. A
   * dead-PID lock file is removed in passing so the next start-daemon invocation is clean.
   */
  public static OptionalInt tryRouteCli(String[] origArgs, List<String> positional, List<Path> libDirs) {
    DaemonPaths paths = routeTarget(positional, libDirs);
    if (paths == null) return OptionalInt.empty();

    LockFile lf = reachableDaemon(paths, false);
    if (lf == null) return OptionalInt.empty();

    SocketBinder.Address addr;
    try {
      addr = SocketBinder.parseAddress(lf.socketPath());
    } catch (IllegalArgumentException e) {
      return OptionalInt.empty();
    }

    try (DaemonClient client = DaemonClient.connect(addr)) {
      // The cwd travels with the argv: a relative path in it belongs to the directory the user
      // typed the command in, and the daemon's own is whatever shell started it, fixed for its
      // lifetime. Without this the same command means different things with and without a daemon.
      Map<String, Object> payload = Map.of(
          "args", Arrays.asList(origArgs),
          "cwd", Paths.get(".").toAbsolutePath().normalize().toString());
      int rc = invokeWithCancelHook(client, "cli", payload, DaemonRpc::printFrame);
      return OptionalInt.of(rc);
    } catch (IOException e) {
      System.err.println("[WARN] daemon at " + lf.socketPath() + " is unreachable (" + e.getMessage()
          + "); running locally");
      return OptionalInt.empty();
    }
  }

  /**
   * The daemon serving {@code paths}, or null if there is none this client should talk to.
   *
   * <p>{@code explain} says whether the reasons are the user's business: an explicit
   * {@code --daemon-*} command wants to know why nothing happened, while the automatic routing
   * in {@link #tryRouteCli} is expected to fall through to a local run in silence -- except for
   * a version mismatch, which is actionable whichever way we got here.
   */
  private static LockFile reachableDaemon(DaemonPaths paths, boolean explain) {
    switch (LockFile.read(paths.lockFile)) {
      case LockFile.Read.Absent ignored -> {
        return null;
      }
      case LockFile.Read.Unreadable unreadable -> {
        // Not the same as "no daemon": something is there. Removing it would be how a second
        // daemon gets started on top of a live one, so say so and leave it alone.
        //
        // Only when asked, though. A lock is unreadable for transient reasons too -- writeAtomic
        // falls back to a non-atomic move on filesystems that cannot rename atomically, and a
        // half-written file read in that window is Unreadable -- and telling every ordinary
        // command to run --daemon-stop is wrong whenever no daemon exists at all.
        if (explain) {
          System.err.println("[WARN] lock file " + paths.lockFile + " cannot be read ("
              + unreadable.reason() + "); not routing to a daemon. Run `arend --daemon-stop` if"
              + " no daemon is running.");
        }
        return null;
      }
      case LockFile.Read.Found found -> {
        LockFile lf = found.lock();
        if (!lf.libraryHash().equals(paths.libraryHash)) {
          if (explain) {
            System.err.println("[WARN]  lock at " + paths.lockFile + " is for a different library; ignoring");
          }
          return null;
        }
        if (ProcessHandle.of(lf.pid()).isEmpty()) {
          LockFile.deleteQuietly(paths.lockFile);
          return null;
        }
        if (lf.socketPath() == null || lf.socketPath().isEmpty()) {
          if (explain) {
            System.err.println("[ERROR] daemon at PID " + lf.pid() + " has no socket bound; restart it");
          }
          return null;
        }
        // The version is written into the lock, echoed in every status frame, and was compared
        // by nobody: a daemon from an older build got frames it cannot interpret and answered
        // "unknown op", or mis-parsed a length prefix outright. Always reported, because the
        // user has to act on it -- silently running locally would hide a stale daemon forever.
        if (lf.protocolVersion() != LockFile.PROTOCOL_VERSION) {
          System.err.println("[WARN] the daemon for this library speaks protocol version "
              + lf.protocolVersion() + ", this build speaks " + LockFile.PROTOCOL_VERSION
              + "; it is out of date. Run `arend --daemon-stop` and start it again.");
          return null;
        }
        return lf;
      }
    }
  }

  /**
   * The daemon a routed command should be sent to, or null to run it locally.
   *
   * <p>The daemon is the one for the first positional that names a library; with no such
   * positional it is the daemon for the current directory -- an {@code arend} with no target
   * means the library here -- or a synthetic daemon already started here, whose lock file is the
   * only marker it has.
   *
   * <p>Every positional then has to be something that daemon can actually be given, and it is
   * not enough that one of them found it. Two cases return null:
   *
   * <ul>
   *   <li>A positional naming a <em>different</em> library. Locally the run would load both; a
   *       daemon holds one.</li>
   *   <li>A positional that exists on disk and is not this library --
   *       {@code arend ../pkgs/foo.zip} from inside a library with a daemon.
   *       {@code CliSetup.populateRequestedTargets} skips a positional that exists as a path, on
   *       the grounds that it names the library already loaded, so the daemon would check
   *       <em>its own</em> library, find nothing wrong, and exit 0 -- while the same command run
   *       locally loads the zip and checks that. Silently answering a different question is worse
   *       than being slow, so this one runs locally.</li>
   * </ul>
   *
   * <p>A module name routes fine: it does not exist as a path, and the daemon resolves it against
   * the library it holds exactly as a local run would.
   */
  private static DaemonPaths routeTarget(List<String> positional, List<Path> libDirs) {
    List<DaemonPaths> named = new ArrayList<>(positional.size());
    DaemonPaths chosen = null;
    for (String pos : positional) {
      DaemonPaths d = DaemonPaths.resolveByName(pos, libDirs, false);
      named.add(d);
      if (d != null && chosen == null) chosen = d;
    }

    if (chosen == null) {
      chosen = DaemonPaths.resolve(Paths.get("."));
      if (chosen == null) {
        // No arend.yaml in cwd, but a synthetic daemon may have been started here:
        // check for the lock file under <cwd>/.arend/.
        DaemonPaths synthetic = DaemonPaths.resolveSynthetic(Paths.get("."));
        if (!Files.exists(synthetic.lockFile)) return null;
        chosen = synthetic;
      }
    }

    for (int i = 0; i < positional.size(); i++) {
      DaemonPaths d = named.get(i);
      if (d != null) {
        if (!d.libraryHash.equals(chosen.libraryHash)) return null;
      } else if (Files.exists(Paths.get(positional.get(i)))) {
        return null;
      }
    }
    return chosen;
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
