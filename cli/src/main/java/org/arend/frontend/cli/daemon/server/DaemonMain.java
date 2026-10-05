package org.arend.frontend.cli.daemon.server;

import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.daemon.DaemonPaths;
import org.arend.frontend.cli.daemon.DaemonStart;
import org.arend.frontend.cli.daemon.LockFile;
import org.arend.frontend.library.SourceLibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

  public static void run(String[] args) {
    if (args.length < 2) {
      System.err.println("[DAEMON] --daemon-bootstrap requires a library reference");
      System.exit(1);
      return;
    }
    String libRef = args[1];
    String[] forwarded = Arrays.copyOfRange(args, 2, args.length);

    DaemonPaths paths = DaemonPaths.resolve(Paths.get(libRef));
    if (paths == null) {
      System.err.println("[DAEMON] could not resolve library reference: " + libRef);
      System.exit(1);
      return;
    }

    List<DaemonPaths> libraries = new ArrayList<>();
    ConsoleMain.ParsedArgs parsed = ConsoleMain.parseArgs(forwarded);
    for (String library : parsed.cmdLine() == null ? List.<String>of() : parsed.cmdLine().getArgList()) {
      DaemonPaths member = DaemonPaths.resolve(Paths.get(library));
      if (member == null) {
        System.err.println("[DAEMON] could not resolve library: " + library);
        System.exit(1);
        return;
      }
      libraries.add(member);
    }
    List<DaemonPaths> owned = libraries.stream().filter(DaemonPaths::canOwn).toList();
    if (owned.isEmpty() || !owned.getFirst().libraryConfig.equals(paths.libraryConfig)) {
      System.err.println("[DAEMON] the bootstrap argv must list " + paths.libraryConfig + " as its first owned library");
      System.exit(1);
      return;
    }

    for (DaemonPaths member : owned) {
      try {
        member.ensureArendDir();
      } catch (IOException e) {
        System.err.println("[DAEMON] could not create " + member.arendDir + ": " + e.getMessage());
        System.exit(1);
        return;
      }
    }
    List<String> libraryConfigs = libraries.stream().map(member -> member.libraryConfig.toString()).toList();

    System.out.println("[DAEMON] Initialising daemon for " + String.join(", ", libraryConfigs));
    System.out.println("[DAEMON] PID = " + ProcessHandle.current().pid());
    System.out.println("[DAEMON] forwarded args: " + String.join(" ", forwarded));

    CommandContext warmCtx = new ConsoleMain().runDaemonBootstrap(forwarded);
    if (warmCtx == null) {
      System.err.println("[DAEMON] bootstrap aborted (library load failed or unhandled exception); daemon will not start");
      System.exit(1);
      return;
    }

    SocketBinder.Bound bound;
    try {
      bound = SocketBinder.bind(paths.socketFile, paths.libraryHash);
    } catch (IOException e) {
      System.err.println("[DAEMON] could not bind daemon socket: " + e.getMessage());
      System.exit(1);
      return;
    }
    System.out.println("[DAEMON] bound socket: " + bound.address());

    DaemonServer server = new DaemonServer(bound, warmCtx, libraryConfigs);
    server.start();

    LockFile lock = new LockFile(
        ProcessHandle.current().pid(),
        paths.libraryConfig.toString(),
        paths.libraryHash,
        libraryConfigs,
        sharedLibraries(warmCtx),
        LockFile.PROTOCOL_VERSION,
        System.currentTimeMillis(),
        bound.address()
    );

    // Registered before the lock is written, not after: a SIGTERM in between would otherwise
    // leave the lock behind, pointing every later client at a daemon that no longer exists.
    // Deleting a file that was never created is a no-op, so the early window costs nothing.
    Path socketPath = paths.socketFile;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.close();
      lock.deleteAllCopies();
      LockFile.deleteQuietly(socketPath);
    }, "arend-daemon-shutdown"));

    // The first library's own lock last: a client that finds it can count on every other copy.
    List<Path> copies = new ArrayList<>();
    copies.add(lock.registryFile());
    for (DaemonPaths member : owned.reversed()) copies.add(member.lockFile);
    for (Path copy : copies) {
      try {
        Files.createDirectories(copy.getParent());
        lock.writeAtomic(copy);
      } catch (IOException e) {
        System.err.println("[DAEMON] could not write lock file " + copy + ": " + e.getMessage());
        System.exit(1);
        return;
      }
    }

    System.out.println("[DAEMON] READY (PID " + ProcessHandle.current().pid() + ")");
    System.out.flush();

    // Until the worker pulls a shutdown sentinel or a SIGTERM trips the JVM shutdown hook, which
    // closes the server and interrupts the worker.
    try {
      server.shutdownLatch().await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // A socket-side `shutdown` returns control here without going through the JVM-exit path, so
    // the hook has to be triggered explicitly.
    System.exit(0);
  }
}
