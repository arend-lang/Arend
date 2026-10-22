package org.arend.frontend.cli.daemon.server;

import org.arend.daemon.server.DaemonServer;
import org.arend.daemon.server.SocketBinder;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.daemon.DaemonPaths;
import org.arend.frontend.cli.daemon.DaemonStart;
import org.arend.daemon.LockFile;
import org.arend.daemon.StartMarker;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Child-side daemon entry point, reached through the internal {@code --daemon-bootstrap} flag
 * that {@link DaemonStart} passes when re-exec'ing this JVM. The parent has already pointed this
 * process's stdout and stderr at {@code daemon.log} and stdin at the null device, so everything
 * the pipeline prints lands in the log without any setOut/setErr of our own.
 *
 * <p>Strip the flag; bootstrap the warm context from the remaining args;
 * bind the socket; start the {@link DaemonServer}; install the shutdown hook; then publish the
 * lock -- after the hook, so a signal in between cannot leave the lock behind -- and block until
 * the server says to stop.
 */
public final class DaemonMain {
  private DaemonMain() {}

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
    String[] forwarded = Arrays.copyOfRange(args, 1, args.length);

    ConsoleMain.ParsedArgs parsed = ConsoleMain.parseArgs(forwarded);
    List<String> positional = parsed.cmdLine() == null ? List.of() : parsed.cmdLine().getArgList();
    DaemonPaths paths = positional.size() == 1 ? DaemonPaths.resolve(Paths.get(positional.getFirst())) : null;
    if (paths == null) {
      System.err.println("[DAEMON] the bootstrap argv lists no library a daemon can own");
      return 1;
    }

    try {
      paths.ensureArendDir();
    } catch (IOException e) {
      System.err.println("[DAEMON] could not create " + paths.arendDir + ": " + e.getMessage());
      return 1;
    }

    System.out.println("[DAEMON] Initialising daemon for " + paths.libraryConfig);
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
    DaemonServer server = CliDaemonRunner.server(bound, warmCtx, paths.libraryConfig.toString(), CliDispatcher::run, stopped::countDown);
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

    LockFile lock = LockFile.ofThisProcess(paths, System.currentTimeMillis(), bound.address());
    try {
      lock.writeAtomic(paths.lockFile);
    } catch (IOException e) {
      System.err.println("[DAEMON] could not write lock file " + paths.lockFile + ": " + e.getMessage());
      return 1;
    }
    // The lock claims the library now. A start whose `arend -d` was killed leaves nobody else to remove the marker.
    StartMarker.release(paths.startingMarker, ProcessHandle.current());

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
