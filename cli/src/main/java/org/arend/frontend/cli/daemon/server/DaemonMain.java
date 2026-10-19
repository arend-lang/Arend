package org.arend.frontend.cli.daemon.server;

import org.arend.daemon.BuildIdentity;
import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.server.DaemonServer;
import org.arend.daemon.server.SocketBinder;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.daemon.DaemonStart;
import org.arend.frontend.library.SourceLibrary;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Child-side daemon entry point, reached through the internal {@code --daemon-bootstrap} flag
 * that {@link DaemonStart} passes when re-exec'ing this JVM. The parent has already pointed this
 * process's stdout and stderr at {@code daemon.log} and stdin at the null device, so everything
 * the pipeline prints lands in the log without any setOut/setErr of our own.
 *
 * <p>Strip the flag and its library reference; bootstrap the warm context from the remaining args;
 * bind the socket; start the {@link DaemonServer}; install the shutdown hook; then publish the
 * lock -- after the hook, so a signal in between cannot leave the lock behind -- and block until
 * the server says to stop.
 */
public final class DaemonMain {
  private DaemonMain() {}

  private static List<String> sharedLibraries(CommandContext ctx) {
    List<String> shared = new ArrayList<>();
    for (String name : ctx.libraryManager.getLibraries()) {
      SourceLibrary library = ctx.libraryManager.getLibrary(name);
      Path file = library == null ? null : library.getLibraryFile();
      if (file == null || !DaemonPaths.isShared(file)) continue;
      try {
        shared.add(file.toRealPath().toString());
      } catch (IOException ignored) {
        // Gone since it was loaded; nothing to route to it either way.
      }
    }
    return shared;
  }

  /** Which of {@code owned} a live daemon has claimed, said for a person; null when none has. */
  public static String claimedMeanwhile(List<DaemonPaths> owned) {
    for (DaemonPaths member : owned) {
      if (LockFile.lookup(member) instanceof LockFile.Read.Found found && found.lock().processAlive()) {
        LockFile other = found.lock();
        return member.libraryConfig + " has been claimed by the daemon (PID " + other.pid() + ")"
            + (other.isHostedByIde() ? " inside " + other.host() : "") + " in the meantime";
      }
    }
    return null;
  }

  /**
   * Runs the daemon until it is told to stop, then exits the JVM. A socket-side {@code shutdown}
   * returns here without going through the JVM-exit path, so the exit, and with it the shutdown
   * hook, has to be triggered explicitly.
   */
  public static void run(String[] args) {
    System.exit(serve(args));
  }

  /** @return the exit code: 1 when the daemon could not start, else 0 once it has stopped. */
  private static int serve(String[] args) {
    // The jar as it is now, before the libraries take their seconds to load: a rebuild meanwhile counts.
    BuildIdentity build = BuildIdentity.ofThisProcess();
    if (args.length < 2) {
      System.err.println("[DAEMON] --daemon-bootstrap requires a library reference");
      return 1;
    }
    String libRef = args[1];
    String[] forwarded = Arrays.copyOfRange(args, 2, args.length);

    DaemonPaths paths = DaemonPaths.resolve(Paths.get(libRef));
    if (paths == null) {
      System.err.println("[DAEMON] could not resolve library reference: " + libRef);
      return 1;
    }

    List<DaemonPaths> libraries = new ArrayList<>();
    ConsoleMain.ParsedArgs parsed = ConsoleMain.parseArgs(forwarded);
    for (String library : parsed.cmdLine() == null ? List.<String>of() : parsed.cmdLine().getArgList()) {
      DaemonPaths member = DaemonPaths.resolve(Paths.get(library));
      if (member == null) {
        System.err.println("[DAEMON] could not resolve library: " + library);
        return 1;
      }
      libraries.add(member);
    }
    List<DaemonPaths> owned = libraries.stream().filter(DaemonPaths::canOwn).toList();
    if (owned.isEmpty() || !owned.getFirst().libraryConfig.equals(paths.libraryConfig)) {
      System.err.println("[DAEMON] the bootstrap argv must list " + paths.libraryConfig + " as its first owned library");
      return 1;
    }

    for (DaemonPaths member : owned) {
      try {
        member.ensureArendDir();
      } catch (IOException e) {
        System.err.println("[DAEMON] could not create " + member.arendDir + ": " + e.getMessage());
        return 1;
      }
    }
    List<String> libraryConfigs = libraries.stream().map(member -> member.libraryConfig.toString()).toList();

    System.out.println("[DAEMON] Initialising daemon for " + String.join(", ", libraryConfigs));
    System.out.println("[DAEMON] PID = " + ProcessHandle.current().pid());
    System.out.println("[DAEMON] forwarded args: " + String.join(" ", forwarded));

    CommandContext warmCtx = new ConsoleMain().runDaemonBootstrap(forwarded);
    if (warmCtx == null) {
      System.err.println("[DAEMON] bootstrap aborted (library load failed or unhandled exception); daemon will not start");
      return 1;
    }

    SocketBinder.Bound bound;
    try {
      bound = SocketBinder.bind(paths.socketFile, paths.libraryHash);
    } catch (IOException e) {
      System.err.println("[DAEMON] could not bind daemon socket: " + e.getMessage());
      return 1;
    }
    System.out.println("[DAEMON] bound socket: " + bound.address());

    CountDownLatch stopped = new CountDownLatch(1);
    String token = LockFile.newToken();
    DaemonServer server = CliDaemonRunner.server(bound, token, warmCtx, libraryConfigs, CliDispatcher::run, stopped::countDown);
    if (build != null) {
      server.retireWhen(() -> !build.isCurrent(), "the arend jar this daemon runs from, " + build.path() + ", has changed"
          + " since it started, so the daemon is stopping. Run the command again: it runs without the daemon, or start"
          + " a new one with `arend -d`.");
    }
    server.start();

    LockFile lock = LockFile.ofThisProcess(paths, libraryConfigs, sharedLibraries(warmCtx), server.startedAt(),
        bound.address(), LockFile.HOST_CLI, token);

    // Registered before the lock is written, not after: a SIGTERM in between would otherwise
    // leave the lock behind, pointing every later client at a daemon that no longer exists.
    // Deleting a file that was never created is a no-op, so the early window costs nothing.
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.close();
      lock.removeTraces();
    }, "arend-daemon-shutdown"));

    // The locks were checked when `-d` was run, and the libraries have taken their seconds to load
    // since: another daemon -- the IDE's, which a start under way does not stop -- may have claimed one.
    String claimed = claimedMeanwhile(owned);
    if (claimed != null) {
      System.err.println("[DAEMON] " + claimed + "; this daemon will not start");
      // The shutdown hook closes the server and removes the socket; the lock it removes is only ever this daemon's.
      return 1;
    }
    try {
      lock.writeEverywhere();
    } catch (IOException e) {
      System.err.println("[DAEMON] could not write the lock: " + e.getMessage());
      return 1;
    }

    // Our own lock has to check out as any client will check it. Looking it up also reads what
    // removing it on the way out takes, while the jar it comes from is still the one we started from.
    if (!(LockFile.lookup(paths) instanceof LockFile.Read.Found)) {
      System.err.println("[DAEMON] the lock just written at " + paths.lockFile + " does not check out against the registry"
          + " in " + DaemonPaths.registryDir() + "; no client will route to this daemon");
    }

    System.out.println("[DAEMON] READY (PID " + ProcessHandle.current().pid() + ")");
    System.out.flush();

    // Until a client's shutdown has let the queue run out; a SIGTERM goes through the JVM
    // shutdown hook instead, which closes the server.
    try {
      stopped.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return 0;
  }
}
