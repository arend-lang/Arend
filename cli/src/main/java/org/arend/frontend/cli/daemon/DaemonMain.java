package org.arend.frontend.cli.daemon;

import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.daemon.server.DaemonServer;
import org.arend.frontend.cli.daemon.server.SocketBinder;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * Child-side daemon entry point. Triggered by an internal {@code --daemon-bootstrap} flag
 * the parent ({@link DaemonStart}) passes when re-exec'ing this JVM.
 *
 * <p>The parent has already redirected this process's stdout/stderr to {@code daemon.log}
 * and stdin to {@code /dev/null}, so all println from the pipeline lands in the log without
 * any setOut/setErr trickery.
 *
 * <p>Flow:
 * <ol>
 *   <li>Strip {@code --daemon-bootstrap <libRef>}; the remaining args are a normal CLI
 *       invocation (typically {@code -L <libdir> <libRef> -ai}).</li>
 *   <li>Resolve {@link DaemonPaths} from {@code libRef}; abort if it doesn't resolve.</li>
 *   <li>Run {@link ConsoleMain#runDaemonBootstrap} on the forwarded args. If it reports
 *       failure, exit 1 (no lock written → parent's wait-for-ready times out).</li>
 *   <li>Bind the daemon socket (UDS or TCP), start the {@link DaemonServer}.</li>
 *   <li>Write the lock file with the bound socket address (signals READY to the parent).</li>
 *   <li>Install a JVM shutdown hook that closes the server + deletes lock + socket.</li>
 *   <li>Block on the server's shutdown latch (decremented by the worker's shutdown sentinel
 *       or by {@code SIGTERM} via the hook).</li>
 * </ol>
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

    boolean ok = new ConsoleMain().runDaemonBootstrap(forwarded);
    if (!ok) {
      System.err.println("[DAEMON] initial typecheck failed; daemon will not start");
      System.exit(1);
      return;
    }

    SocketBinder.Bound bound;
    try {
      bound = SocketBinder.bind(paths.socketFile);
    } catch (IOException e) {
      System.err.println("[DAEMON] could not bind daemon socket: " + e.getMessage());
      System.exit(1);
      return;
    }
    System.out.println("[DAEMON] bound socket: " + bound.address());

    DaemonServer server = new DaemonServer(bound);
    server.start();

    LockFile lock = new LockFile(
        ProcessHandle.current().pid(),
        paths.libraryConfig.toString(),
        paths.libraryHash,
        LockFile.PROTOCOL_VERSION_M3,
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

    Path lockPath = paths.lockFile;
    Path socketPath = paths.socketFile;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      server.close();
      LockFile.deleteQuietly(lockPath);
      LockFile.deleteQuietly(socketPath);
    }, "arend-daemon-shutdown"));

    System.out.println("[DAEMON] READY (PID " + ProcessHandle.current().pid() + ")");
    System.out.flush();

    // Block until the worker pulls a shutdown sentinel (socket-side shutdown op) or a
    // SIGTERM trips the JVM shutdown hook. In the signal case the hook calls
    // server.close(), the worker's queue.take() is interrupted, and the JVM exits.
    try {
      server.shutdownLatch().await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    // Trigger the shutdown hook explicitly: socket-side `shutdown` returns control here
    // without going through the JVM-exit path, so we have to call exit ourselves.
    System.exit(0);
  }
}
