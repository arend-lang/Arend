package org.arend.daemon.client;

import org.arend.daemon.BuildIdentity;
import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.wire.SocketAddress;

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
 * ({@code --daemon-ping}, {@code --daemon-status}, {@code --daemon-refresh},
 * {@code --daemon-stop}).
 *
 * <p>Resolves the library and reads its lock file; if the lock serves that library, its PID is
 * alive and it speaks this build's protocol, opens a {@link DaemonClient}, fires the op, prints
 * any non-{@code done} frames, returns the daemon's exit code. Returns {@link #NO_DAEMON} when
 * there is no such daemon; the caller decides how to react ({@code ping} reports "no daemon to
 * talk to", {@code stop} does not take it for success and goes on to the process).
 *
 * <p>It is also where an ordinary command finds the daemon to route to ({@link #tryRouteCli}), and
 * where the MCP server finds the daemons it calls ({@link #daemonFor}, {@link #liveDaemons}).
 */
public final class DaemonRpc {
  public static final int NO_DAEMON = -1;

  /** How long a Ctrl-C waits for the daemon to acknowledge the cancellation before giving up. */
  private static final int CANCEL_ACK_TIMEOUT_SECONDS = 5;

  private DaemonRpc() {}

  /**
   * Resolve, validate, connect, invoke. {@code op} is one of {@code "ping" / "status" /
   * "shutdown" / "refresh"}; each frame is pretty-printed for the user.
   */
  public static int run(String libRef, List<Path> libDirsForward, String op) {
    DaemonPaths paths = DaemonPaths.resolveByName(libRef, libDirsForward);
    if (paths == null) {
      System.err.println("[ERROR] " + op + ": could not resolve library reference '" + libRef + "'");
      return 1;
    }
    return run(paths, op);
  }

  /**
   * As {@link #run(String, List, String)}, for a library the caller has already resolved -- a
   * caller that resolved it to reach a decision must send the op to <em>that</em> library, since
   * a second resolution re-walks the libDirs, warns again, and can land somewhere else.
   */
  public static int run(DaemonPaths paths, String op) {
    LockFile lf = paths.canOwn() ? reachableDaemon(paths, true) : null;
    // A library no daemon owns is answered for by the one that loaded it -- but stopping it through
    // that library would stop some other project's daemon.
    if (lf == null && !"shutdown".equals(op) && !hasOwner(paths)) lf = firstLoading(List.of(paths), true);
    if (lf == null) return NO_DAEMON;

    SocketAddress addr;
    try {
      addr = SocketAddress.parse(lf.socketPath());
    } catch (IllegalArgumentException e) {
      System.err.println("[ERROR] invalid socket address in lock: " + e.getMessage());
      return 1;
    }

    try (DaemonClient client = DaemonClient.connect(addr, lf.token())) {
      return invokeWithCancelHook(client, op, Map.of(), DaemonRpc::printFrame);
    } catch (IOException e) {
      System.err.println("[ERROR] " + op + ": cannot reach daemon at " + lf.socketPath() + ": " + e.getMessage());
      return 1;
    }
  }

  /**
   * Runs one request with a shutdown hook that tells the daemon to abandon it. Ctrl-C kills the
   * client, not the daemon, so without this the typecheck keeps running and holds the worker
   * against the next request. The hook waits for the ack because the JVM tears down sockets
   * underneath a shutdown hook, and a fire-and-forget write can be discarded before it lands.
   */
  private static int invokeWithCancelHook(DaemonClient client, String op,
                                          Map<String, Object> extra,
                                          Consumer<Map<String, Object>> onFrame) throws IOException {
    String id = UUID.randomUUID().toString();
    // settled means "stop waiting"; accepted carries what the daemon said, and stays null when
    // the read loop ended without an ack at all. Keeping them apart is the difference between
    // reporting what the daemon said and reporting that it said nothing.
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
      // Releases a hook waiting on a request that is already over. Deliberately leaves
      // `accepted` null: an ordinary completion, or an IOException out of invoke, is not an
      // acknowledgement.
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
   * Client-side auto-routing: if a daemon serves the library this command is about, send it a
   * {@code cli} op carrying the original argv and return its exit code; empty means run in this
   * process instead. Which libraries the command is about is {@link #routeTarget}, and which
   * daemon has them {@link #daemonFor}; connection failures, malformed locks, a library the daemon
   * lacks or a dead PID all return empty.
   */
  public static OptionalInt tryRouteCli(String[] origArgs, List<String> positional, List<Path> libDirs) {
    List<DaemonPaths> libraries = routeTarget(positional, libDirs);
    if (libraries == null) return OptionalInt.empty();

    // Naming no library, from a directory that is none: the daemon of the project there, if one has it.
    LockFile lf = libraries.isEmpty() ? projectDaemon(Paths.get("."), true) : daemonFor(libraries);
    if (lf == null) return OptionalInt.empty();
    String otherBuild = otherBuild(lf, BuildIdentity.ofThisProcess());
    if (otherBuild != null) {
      System.err.println("[WARN] the daemon (PID " + lf.pid() + ") runs " + otherBuild + "; running this command here."
          + " Restart the daemon to have it run this build: `arend --daemon-stop`, then `arend -d`.");
      return OptionalInt.empty();
    }

    SocketAddress addr;
    try {
      addr = SocketAddress.parse(lf.socketPath());
    } catch (IllegalArgumentException e) {
      return OptionalInt.empty();
    }

    try (DaemonClient client = DaemonClient.connect(addr, lf.token())) {
      // The cwd travels with the argv: a relative path belongs to the directory the user typed
      // the command in, while the daemon's own is whatever shell started it, fixed for its
      // lifetime.
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
   * How the build the daemon of {@code lock} runs differs from {@code ours}, for a person; null when
   * it is the same, or when either is not known -- a daemon in the IDE, a client run from class
   * directories. A daemon of another build answers as a version the user no longer has.
   */
  public static String otherBuild(LockFile lock, BuildIdentity ours) {
    BuildIdentity theirs = lock.build();
    if (theirs == null || ours == null || theirs.equals(ours)) return null;
    return theirs.path().equals(ours.path())
        ? "an earlier build of " + theirs.path() + ", which has changed since it started"
        : "arend from " + theirs.path() + ", and this command from " + ours.path();
  }

  /**
   * The daemon serving {@code paths}, or null if there is none this client should talk to.
   * {@code explain} says whether the reasons are the user's business: an explicit
   * {@code --daemon-*} command wants to know why nothing happened, while the automatic routing in
   * {@link #tryRouteCli} falls through to a local run in silence -- except for a version
   * mismatch, which is actionable whichever way we got here.
   */
  private static LockFile reachableDaemon(DaemonPaths paths, boolean explain) {
    switch (LockFile.lookup(paths)) {
      case LockFile.Read.Absent ignored -> {
        return null;
      }
      case LockFile.Read.Unregistered unregistered -> {
        // Its address and its process are only its own word, and anybody may have written it.
        if (explain) {
          System.err.println("[WARN] lock file " + paths.lockFile + " is not confirmed by the registry in "
              + DaemonPaths.registryDir() + "; not routing to it.");
        }
        return null;
      }
      case LockFile.Read.Unreadable unreadable -> {
        // Not the same as "no daemon": something is there, and removing it is how a second daemon
        // gets started on top of a live one. Reported only when asked, because a lock is also
        // unreadable for transient reasons -- a non-atomic writeAtomic fallback read mid-write --
        // and telling every ordinary command to run --daemon-stop is wrong when no daemon exists.
        if (explain) {
          System.err.println("[WARN] lock file " + paths.lockFile + " cannot be read ("
              + unreadable.reason() + "); not routing to a daemon. Run `arend --daemon-stop` if"
              + " no daemon is running.");
        }
        return null;
      }
      case LockFile.Read.Found found -> {
        // The registry's entry for a daemon that serves the library: lookup checked that.
        LockFile lf = found.lock();
        if (!lf.processAlive()) {
          lf.deleteAllCopies();
          return null;
        }
        if (lf.socketPath() == null || lf.socketPath().isEmpty()) {
          if (explain) {
            System.err.println("[ERROR] daemon at PID " + lf.pid() + " has no socket bound; restart it");
          }
          return null;
        }
        // Always reported, because the user has to act on it: a daemon from an older build gets
        // frames it cannot interpret, and silently running locally would hide it forever.
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
   * The daemon that has every one of {@code libraries} loaded, or null: locally the run would load
   * them all, and a daemon lacking one would answer about the others only. The daemon owning one of
   * them, found through its lock, if one does; else -- when they are shared libraries, zips, or
   * dependencies a daemon was not given -- the longest-running one that loaded them all
   * ({@link #firstLoading}).
   */
  public static LockFile daemonFor(List<DaemonPaths> libraries) {
    LockFile lf = null;
    boolean owned = false;
    for (DaemonPaths paths : libraries) {
      if (!paths.canOwn()) continue;
      lf = reachableDaemon(paths, false);
      // A daemon of its own that is no daemon to route to -- of another protocol, say -- is not
      // stood in for by one that merely loaded it.
      if (lf != null || hasOwner(paths)) {
        owned = true;
        break;
      }
    }
    if (!owned) lf = firstLoading(libraries, false);
    if (lf == null) return null;
    for (DaemonPaths paths : libraries) {
      if (!lf.hasLoaded(paths.libraryConfig)) return null;
    }
    return lf;
  }

  /**
   * Whether a daemon of its own holds {@code paths}, reachable or not: its lock is there and the
   * registry confirms it -- or it cannot be read, and then nobody can tell. A dead daemon's has been
   * removed by the time this is asked.
   */
  private static boolean hasOwner(DaemonPaths paths) {
    return switch (LockFile.lookup(paths)) {
      case LockFile.Read.Absent ignored -> false;
      case LockFile.Read.Unregistered ignored -> false;
      case LockFile.Read.Found ignored -> true;
      case LockFile.Read.Unreadable ignored -> true;
    };
  }

  /** Whether a client of this build can talk to the daemon of {@code lock}: it has bound a socket, and speaks this build's protocol. */
  public static boolean compatible(LockFile lock) {
    return lock.socketPath() != null && !lock.socketPath().isEmpty() && lock.protocolVersion() == LockFile.PROTOCOL_VERSION;
  }

  /** Every live daemon in {@link DaemonPaths#registryDir}, oldest first; a dead one's entry is removed. */
  public static List<LockFile> liveDaemons() {
    List<LockFile> live = new ArrayList<>();
    for (LockFile lf : LockFile.registered()) {
      if (lf.processAlive()) {
        live.add(lf);
      } else {
        lf.deleteAllCopies();
      }
    }
    return live;
  }

  /**
   * The longest-running live daemon that has every one of {@code libraries} loaded, from
   * {@link DaemonPaths#registryDir}: the deterministic answer for a shared library, which any
   * number of daemons may hold. A dead daemon's entry is removed on the way.
   */
  private static LockFile firstLoading(List<DaemonPaths> libraries, boolean explain) {
    for (LockFile lf : liveDaemons()) {
      if (!libraries.stream().allMatch(paths -> lf.hasLoaded(paths.libraryConfig))) continue;
      if (!compatible(lf)) {
        if (explain && lf.protocolVersion() != LockFile.PROTOCOL_VERSION) {
          System.err.println("[WARN] the daemon PID " + lf.pid() + " speaks protocol version "
              + lf.protocolVersion() + ", this build speaks " + LockFile.PROTOCOL_VERSION + "; skipping it");
        }
        continue;
      }
      return lf;
    }
    return null;
  }

  /**
   * The libraries a routed command is about: every positional that names one, or, with no such
   * positional, the library of the current directory -- or none, when it is not one, and then the
   * project there decides ({@link #projectDaemon}). Null to run it locally.
   *
   * <p>Every positional then has to be something a daemon can actually be given, and it is not
   * enough that one of them found it. Two cases decline:
   *
   * <ul>
   *   <li>A positional naming a library no one daemon has together with the rest. Locally the run
   *       would load it; a daemon holds only its own set. Checked by {@link #daemonFor}, against
   *       the locks.</li>
   *   <li>A positional that exists on disk and is not this library --
   *       {@code arend ../pkgs/foo.zip} from inside a library with a daemon. The daemon holds no
   *       such library, and would fail the command, while the same command run locally loads the
   *       zip and checks it.</li>
   * </ul>
   *
   * <p>A module name routes fine: it does not exist as a path, and the daemon resolves it against
   * the library it holds exactly as a local run would.
   */
  private static List<DaemonPaths> routeTarget(List<String> positional, List<Path> libDirs) {
    List<DaemonPaths> named = new ArrayList<>(positional.size());
    for (String pos : positional) {
      DaemonPaths d = DaemonPaths.resolveByName(pos, libDirs, false);
      if (d != null) {
        named.add(d);
      } else if (Files.exists(Paths.get(pos))) {
        return null;
      }
    }
    if (!named.isEmpty()) return named;
    DaemonPaths here = DaemonPaths.resolve(Paths.get("."));
    return here == null ? List.of() : List.of(here);
  }

  /**
   * The live daemon holding the project in {@code dir} -- an IntelliJ project's directory, or the
   * deepest one holding the libraries {@code arend -d} was given -- or null when none does, or when
   * several do, which {@code explain} says. Only {@code dir} itself counts: nothing is searched for
   * above it.
   */
  public static LockFile projectDaemon(Path dir, boolean explain) {
    String canonical;
    try {
      canonical = dir.toRealPath().toString();
    } catch (IOException e) {
      return null;
    }
    List<LockFile> holding = liveDaemons().stream().filter(lf -> lf.projectDir().equals(canonical) && compatible(lf)).toList();
    if (holding.size() > 1 && explain) {
      System.err.println("[WARN] the daemons " + holding.stream().map(lf -> "PID " + lf.pid()).toList() + " all hold the project in "
          + canonical + "; name a library to say which one this command is for.");
    }
    return holding.size() == 1 ? holding.getFirst() : null;
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
      System.out.println("pid:             " + frame.get("pid"));
      if (frame.get("host") != null) System.out.println("host:            " + frame.get("host"));
      System.out.println("state:           " + frame.get("state"));
      System.out.println("queueDepth:      " + frame.get("queueDepth"));
      System.out.println("uptimeMs:        " + frame.get("uptimeMs"));
      System.out.println("currentTaskId:   " + frame.get("currentTaskId"));
      System.out.println("protocolVersion: " + frame.get("protocolVersion"));
      if (frame.get("libraries") instanceof List<?> libraries) {
        System.out.println("libraries:");
        for (Object library : libraries) System.out.println("  " + library);
      }
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
