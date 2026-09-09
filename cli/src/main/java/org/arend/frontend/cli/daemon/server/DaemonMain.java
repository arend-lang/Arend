package org.arend.frontend.cli.daemon.server;

import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.daemon.DaemonPaths;
import org.arend.frontend.cli.daemon.DaemonStart;
import org.arend.frontend.cli.daemon.LockFile;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

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

    try {
      paths.ensureArendDir();
    } catch (IOException e) {
      System.err.println("[DAEMON] could not create " + paths.arendDir + ": " + e.getMessage());
      System.exit(1);
      return;
    }

    System.out.println("[DAEMON] Initialising daemon for " + paths.libraryConfig);
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

    DaemonServer server = new DaemonServer(bound, warmCtx, paths.libraryConfig.toString());
    server.start();

    // Registered before the lock is written, not after: a SIGTERM in between would otherwise
    // leave the lock behind, pointing every later client at a daemon that no longer exists.
    // Deleting a file that was never created is a no-op, so the early window costs nothing.
    Path lockPath = paths.lockFile;
    Path socketPath = paths.socketFile;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.close();
      LockFile.deleteQuietly(lockPath);
      LockFile.deleteQuietly(socketPath);
    }, "arend-daemon-shutdown"));

    LockFile lock = new LockFile(
        ProcessHandle.current().pid(),
        paths.libraryConfig.toString(),
        paths.libraryHash,
        LockFile.PROTOCOL_VERSION,
        System.currentTimeMillis(),
        bound.address()
    );
    try {
      lock.writeAtomic(paths.lockFile);
    } catch (IOException e) {
      System.err.println("[DAEMON] could not write lock file " + paths.lockFile + ": " + e.getMessage());
      server.close();
      System.exit(1);
      return;
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
