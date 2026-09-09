package org.arend.frontend.cli.daemon;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parent-side: start a detached daemon JVM for a single library, as {@code arend -d <libRef>}.
 *
 * <p>Resolve {@link DaemonPaths}; decide what an existing lock means ({@link #inspectLock});
 * claim the right to start ({@link #reserveStart}); spawn
 * {@code [setsid|nohup] java <opts> -cp <cp> ConsoleMain --daemon-bootstrap <arend.yaml> [-L ...]
 * <arend.yaml>} with stdio pointed at daemon.log; then poll for the lock the child writes when it
 * is ready, for up to {@value #READY_TIMEOUT_SECONDS}s (a cold arend-lib bootstrap is ~2 minutes).
 */
public final class DaemonStart {
  /** Max time to wait for the child to write its lock file. */
  public static final int READY_TIMEOUT_SECONDS = 600;

  private DaemonStart() {}

  /**
   * @param libRef         user-supplied library reference (directory, arend.yaml, or name)
   * @param libDirsForward extra {@code -L} dirs to forward to the child
   * @param extraChildArgs bootstrap-affecting flags appended after the {@code -L} forwards
   */
  public static int run(String libRef, List<Path> libDirsForward, List<String> extraChildArgs) {
    DaemonPaths paths = DaemonPaths.resolveByName(libRef, libDirsForward);
    if (paths == null) {
      System.err.println("[ERROR] -d: could not resolve library reference '" + libRef + "'");
      return 1;
    }

    try {
      paths.ensureArendDir();
    } catch (IOException e) {
      System.err.println("[ERROR] -d: cannot create " + paths.arendDir + ": " + e.getMessage());
      return 1;
    }

    LockFile.Read existing = LockFile.read(paths.lockFile);
    switch (inspectLock(existing, paths.libraryHash, DaemonStart::isAlive)) {
      case UNREADABLE_LOCK -> {
        // Something is there and we cannot tell whether it is live. Starting a second daemon on
        // top of a running one is the one outcome worth refusing outright.
        String reason = ((LockFile.Read.Unreadable) existing).reason();
        System.err.println("[ERROR] -d: lock file at " + paths.lockFile + " cannot be read ("
            + reason + "). Stop any running daemon and remove the file by hand.");
        return 1;
      }
      case ALREADY_RUNNING -> {
        System.out.println("Arend daemon already running for " + paths.libraryConfig
            + " (PID " + lockOf(existing).pid() + ")");
        return 0;
      }
      case STALE_LOCK -> {
        System.err.println("[WARN]  -d: removing stale lock for dead PID " + lockOf(existing).pid());
        LockFile.deleteQuietly(paths.lockFile);
      }
      case STALE_MISMATCH -> {
        System.err.println("[WARN]  -d: lock file at " + paths.lockFile
            + " is for a different library hash and its process is gone; removing");
        LockFile.deleteQuietly(paths.lockFile);
      }
      case LIVE_MISMATCH -> {
        LockFile lf = lockOf(existing);
        System.err.println("[ERROR] -d: " + paths.lockFile + " belongs to a running daemon (PID "
            + lf.pid() + ") serving " + lf.libraryPath() + ", which shares this directory."
            + " Removing its lock would leave it running and unreachable. Stop it first"
            + " (`arend --daemon-stop`), or start this one from a different directory.");
        return 1;
      }
      case NO_DAEMON -> { }
    }

    if (!reserveStart(paths.startingMarker, READY_TIMEOUT_SECONDS * 1000L)) {
      System.err.println("[ERROR] -d: another daemon start for " + paths.libraryConfig
          + " is already in progress (" + paths.startingMarker + "). Wait for it to finish, or"
          + " remove that file if no start is running.");
      return 1;
    }
    try {
      return startChild(paths, libDirsForward, extraChildArgs);
    } finally {
      LockFile.deleteQuietly(paths.startingMarker);
    }
  }

  private static int startChild(DaemonPaths paths, List<Path> libDirsForward,
                                List<String> extraChildArgs) {
    List<String> cmd = buildChildCommand(paths, libDirsForward, extraChildArgs);
    rotateDaemonLog(paths.logFile);

    ProcessBuilder pb = new ProcessBuilder(cmd);
    pb.redirectOutput(ProcessBuilder.Redirect.to(paths.logFile.toFile()));
    pb.redirectErrorStream(true); // merge child stderr into stdout (and hence into log)
    pb.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));

    Process child;
    try {
      child = pb.start();
    } catch (IOException e) {
      System.err.println("[ERROR] -d: cannot spawn daemon process: " + e.getMessage());
      System.err.println("        command: " + String.join(" ", cmd));
      return 1;
    }

    System.out.println("Starting Arend daemon for " + paths.libraryConfig);
    System.out.println("  log: " + paths.logFile);
    System.out.println("  waiting for initial typecheck to complete (up to "
        + READY_TIMEOUT_SECONDS + "s)...");

    long deadline = System.currentTimeMillis() + READY_TIMEOUT_SECONDS * 1000L;
    while (System.currentTimeMillis() < deadline) {
      if (reportIfReady(paths)) return 0;
      // Child exit before lock = startup failure; bail out.
      if (!child.isAlive()) {
        System.err.println("[ERROR] -d: daemon exited before becoming ready (exit code "
            + child.exitValue() + ")");
        System.err.println("        see " + paths.logFile + " for details");
        return 1;
      }
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // The lock can appear between the last poll and the deadline, and then the child is a healthy
    // daemon that was merely slow. Killing here would turn a slow start into a lost one.
    if (reportIfReady(paths)) return 0;

    System.err.println("[ERROR] -d: daemon did not become ready within "
        + READY_TIMEOUT_SECONDS + "s; killing PID " + child.pid());
    boolean gone = ProcessKill.terminateTree(List.of(child.toHandle()),
        () -> System.err.println("[WARN]  -d: PID " + child.pid() + " did not exit within "
            + ProcessKill.GRACEFUL_TIMEOUT_SECONDS + "s; escalating to SIGKILL"));
    if (gone) {
      LockFile.deleteQuietly(paths.lockFile);
    } else {
      // The lock stays: it is the only thing that can find this process again.
      System.err.println("[ERROR] -d: PID " + child.pid() + " is still alive; leaving "
          + paths.lockFile + " in place so it can still be found. Run `arend --daemon-stop`"
          + " for this library.");
    }
    return 1;
  }

  /** Whether the child has published a usable lock; says so, and warns about a bare TCP port. */
  private static boolean reportIfReady(DaemonPaths paths) {
    Optional<LockFile> ready = LockFile.readIfValid(paths.lockFile);
    if (ready.isEmpty() || !ready.get().libraryHash().equals(paths.libraryHash)
        || ProcessHandle.of(ready.get().pid()).isEmpty()) {
      return false;
    }
    LockFile lock = ready.get();
    System.out.println("Daemon ready (PID " + lock.pid() + ")");
    // SocketBinder already warns, but it warns in the child, whose output is pointed at
    // daemon.log before it runs -- so the one person who could act on it cannot see it.
    String address = lock.socketPath();
    if (address != null && address.startsWith("tcp:")) {
      System.err.println("[WARN] this daemon is listening on " + address.substring(4)
          + " with no access control: any local process can run commands against the warm library"
          + " or shut it down. See " + paths.logFile + " for why no Unix socket could be bound.");
    }
    return true;
  }

  /** What an existing lock file means for a start attempt. */
  enum Verdict {
    /** No lock, or nothing that stops us. */
    NO_DAEMON,
    /** A lock we cannot parse; we do not know what is running. */
    UNREADABLE_LOCK,
    /** A healthy daemon already serves this library. */
    ALREADY_RUNNING,
    /** A lock for this library whose process is gone. */
    STALE_LOCK,
    /** A lock for a different library whose process is gone. */
    STALE_MISMATCH,
    /**
     * A lock for a different library whose process is <em>running</em>. Its lock is the only
     * record of where it listens, so removing it does not stop the daemon -- it strands it.
     */
    LIVE_MISMATCH
  }

  /**
   * Separated from {@link #run} because {@code run} spawns a JVM and cannot be exercised: every
   * one of these branches decides whether a possibly-running daemon keeps its lock.
   */
  static Verdict inspectLock(LockFile.Read existing, String expectedHash,
                             java.util.function.Predicate<LockFile> alive) {
    if (existing instanceof LockFile.Read.Unreadable) return Verdict.UNREADABLE_LOCK;
    if (!(existing instanceof LockFile.Read.Found found)) return Verdict.NO_DAEMON;
    LockFile lf = found.lock();
    boolean live = alive.test(lf);
    if (!lf.libraryHash().equals(expectedHash)) {
      return live ? Verdict.LIVE_MISMATCH : Verdict.STALE_MISMATCH;
    }
    return live ? Verdict.ALREADY_RUNNING : Verdict.STALE_LOCK;
  }

  private static LockFile lockOf(LockFile.Read read) {
    return ((LockFile.Read.Found) read).lock();
  }

  /**
   * Liveness is the PID and nothing else. A socket probe would also catch a recycled PID, but it
   * can fail for reasons that have nothing to do with the daemon being gone -- and every caller
   * that concludes "dead" goes on to remove the lock. A false "still running" costs the user one
   * {@code --daemon-stop}; a false "dead" costs them a process they can no longer reach.
   */
  private static boolean isAlive(LockFile lf) {
    return ProcessHandle.of(lf.pid()).isPresent();
  }

  /**
   * Claims the right to start a daemon for this library, atomically.
   *
   * <p>{@code CREATE_NEW} is the whole mechanism: between reading the lock and the child writing
   * one there is a window of however long a cold typecheck takes, and two calls in that window
   * both see no daemon and both spawn. The loser cannot bind the socket and quietly becomes an
   * unreachable JVM holding a second copy of the library. A marker older than a start could
   * possibly take is treated as debris from a killed parent and reclaimed.
   *
   * @return true if the caller may proceed.
   */
  static boolean reserveStart(Path marker, long staleAfterMillis) {
    try {
      Files.createFile(marker);
      return true;
    } catch (FileAlreadyExistsException e) {
      try {
        long age = System.currentTimeMillis() - Files.getLastModifiedTime(marker).toMillis();
        if (age < staleAfterMillis) return false;
        Files.deleteIfExists(marker);
        Files.createFile(marker);
        return true;
      } catch (IOException retry) {
        return false;
      }
    } catch (IOException e) {
      // The directory will not take the marker; it will not take the lock either, and the spawn
      // reports that properly. Refusing here would hide it behind a worse message.
      return true;
    }
  }

  /**
   * Whether a JVM option this process was given should be handed to the child: everything except
   * the ones that cannot exist twice. Two JVMs cannot attach the same agent or own the same fixed
   * file, and a child that inherits one dies during VM init -- which the parent can only report as
   * "daemon exited before becoming ready", naming neither the option nor the resource.
   *
   * <p>Heap and stack sizing is deliberately absent: the daemon does the work this process would
   * have done, so it needs the same {@code -Xmx} and {@code -Xss}. A child assembled from just
   * the classpath silently gets the defaults and then fails on libraries the direct call handles.
   */
  static boolean isForwardableJvmOption(String option) {
    // An agent is attached to one specific JVM; a jdwp transport binds a debugger port.
    if (option.startsWith("-agentlib:") || option.startsWith("-javaagent:")
        || option.startsWith("-agentpath:") || option.startsWith("-Xrunjdwp")) return false;
    // Dumps and reports written to a path the user fixed.
    return !option.startsWith("-XX:HeapDumpPath=")
        && !option.startsWith("-XX:ErrorFile=")
        && !option.startsWith("-XX:LogFile=");
  }

  static List<String> buildChildCommand(DaemonPaths paths, List<Path> libDirsForward,
                                        List<String> extraChildArgs) {
    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    List<String> cmd = new ArrayList<>();

    // Detach the child from the parent's controlling terminal so closing the parent shell does
    // not SIGHUP the daemon; the JDK has no native equivalent. Linux has setsid (util-linux);
    // macOS does not on the default PATH, so nohup (POSIX) ignores SIGHUP in the child instead,
    // which is enough since stdio is already redirected. Windows relies on the redirection alone.
    if (Platform.isMac()) {
      cmd.add("nohup");
    } else if (!Platform.isWindows()) {
      cmd.add("setsid");
    }
    cmd.add(javaBin);
    for (String option : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if (isForwardableJvmOption(option)) cmd.add(option);
    }
    cmd.add("-cp");
    cmd.add(System.getProperty("java.class.path"));
    cmd.add("org.arend.frontend.ConsoleMain");
    cmd.add("--daemon-bootstrap");
    cmd.add(paths.libraryConfig.toString());
    for (Path libDir : libDirsForward) {
      cmd.add("-L");
      cmd.add(libDir.toString());
    }
    cmd.addAll(extraChildArgs);
    // The canonical config path, and not the reference the user typed. The parent resolved the
    // library cwd-first (DaemonPaths.resolveByName) and keyed the lock on what it found; a bare
    // name reaching the child is resolved libDirs-first (CliSetup.findLibrary). A name matching
    // both a library here and one under a -L directory would therefore warm the libDir copy
    // behind a lock advertising this one, and every command routed afterwards would be answered
    // from source the user is not looking at -- with findLibrary's own shadowing warning going to
    // daemon.log, where nobody reads it. An absolute arend.yaml is the one form both agree on.
    cmd.add(paths.libraryConfig.toString());
    return cmd;
  }

  private static File nullDevice() {
    return new File(Platform.isWindows() ? "NUL" : "/dev/null");
  }

  /** Keeps the previous session's log as {@code daemon.log.1}; the fresh process truncates. */
  private static void rotateDaemonLog(Path logFile) {
    if (!Files.exists(logFile)) return;
    try {
      Files.move(logFile, logFile.resolveSibling(logFile.getFileName() + ".1"),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      System.err.println("[WARN]  -d: could not rotate " + logFile + ": " + e.getMessage());
    }
  }
}
