package org.arend.frontend.cli.daemon;

import org.arend.frontend.ConsoleMain;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

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
 *   <li>Run {@link ConsoleMain#run(String[])} on the stripped args. If it reports failure,
 *       exit 1 (no lock written = parent's wait-for-ready times out and reports).</li>
 *   <li>Write the lock file (signals READY to the parent).</li>
 *   <li>Install a JVM shutdown hook that deletes the lock file.</li>
 *   <li>Block indefinitely; SIGTERM / explicit destroy unblocks via shutdown hook.</li>
 * </ol>
 */
public final class DaemonMain {
  private DaemonMain() {}

  public static void run(String[] args) {
    // Expected layout: args[0] == "--daemon-bootstrap", args[1] == library reference,
    // args[2..] = forwarded normal-CLI args.
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

    LockFile lock = new LockFile(
        ProcessHandle.current().pid(),
        paths.libraryConfig.toString(),
        paths.libraryHash,
        LockFile.PROTOCOL_VERSION_M2,
        System.currentTimeMillis(),
        "" // no socket bound in M2
    );
    try {
      lock.writeAtomic(paths.lockFile);
    } catch (IOException e) {
      System.err.println("[DAEMON] could not write lock file " + paths.lockFile + ": " + e.getMessage());
      System.exit(1);
      return;
    }

    // Shutdown hook: removes lock + socket on graceful exit (SIGTERM, JVM exit, normal end).
    // SIGKILL skips this and leaves a stale lock — the next client's stale-PID check cleans it.
    Path lockPath = paths.lockFile;
    Path socketPath = paths.socketFile;
    final CountDownLatch shutdown = new CountDownLatch(1);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      LockFile.deleteQuietly(lockPath);
      LockFile.deleteQuietly(socketPath);
      shutdown.countDown();
    }, "arend-daemon-shutdown"));

    System.out.println("[DAEMON] READY (PID " + ProcessHandle.current().pid() + ")");
    System.out.flush();

    // Block until the shutdown hook fires. The hook runs on its own thread, but Runtime
    // joins it before exiting, so simply waiting forever here suffices — the JVM teardown
    // path will unblock us via the latch.
    try {
      shutdown.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
