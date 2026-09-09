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
 * Parent-side: start a detached daemon JVM for a single library. The user invokes this
 * via {@code arend -d <libRef>}.
 *
 * <p>Sequence:
 * <ol>
 *   <li>Resolve {@link DaemonPaths}. Validate single-library scope.</li>
 *   <li>Stale-PID check on existing lock file. If a healthy daemon already serves this
 *       library, print info and return success without spawning.</li>
 *   <li>Build child command line: {@code [setsid|nohup] <javaBin> -cp <cp> ConsoleMain
 *       --daemon-bootstrap <arend.yaml> [-L ...] <arend.yaml>}, naming the library by the
 *       resolved config path both times and never by the reference the user typed -- see
 *       {@link #buildChildCommand}. Linux uses {@code setsid} (util-linux);
 *       macOS uses {@code nohup} (POSIX) since {@code setsid} is not on the default PATH;
 *       Windows uses no wrapper.</li>
 *   <li>Redirect stdin←/dev/null, stdout+stderr→append daemon.log. Start.</li>
 *   <li>Poll for daemon.lock to appear, timeout {@value #READY_TIMEOUT_SECONDS}s. Cold
 *       arend-lib bootstrap is ~2 minutes; we leave generous headroom.</li>
 * </ol>
 */
public final class DaemonStart {
  /** Max time to wait for the child to write its lock file. */
  public static final int READY_TIMEOUT_SECONDS = 600;

  private DaemonStart() {}

  /**
   * @param libRef           user-supplied library reference (directory, arend.yaml, or name)
   * @param libDirsForward   extra {@code -L} dirs to forward to the child
   * @param syntheticSrcDir  non-null → synthetic-library daemon (no arend.yaml on disk);
   *                         daemon state lives in {@code <syntheticSrcDir>/.arend/}.
   * @param extraChildArgs   appended to the child JVM's command line after the {@code -L}
   *                         forwards. Used to pass {@code -s/-e/-m} when starting a
   *                         synthetic-library daemon.
   */
  public static int run(String libRef, List<Path> libDirsForward,
                        List<String> extraChildArgs, Path syntheticSrcDir) {
    DaemonPaths paths = syntheticSrcDir != null
        ? DaemonPaths.resolveSynthetic(syntheticSrcDir)
        : DaemonPaths.resolveByName(libRef, libDirsForward);
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

    // Stale-PID / existing-daemon check.
    LockFile.Read existing = LockFile.read(paths.lockFile);
    Verdict verdict = inspectLock(existing, paths.libraryHash, DaemonStart::isAlive);
    switch (verdict) {
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
      return startChild(paths, libDirsForward, extraChildArgs, syntheticSrcDir);
    } finally {
      LockFile.deleteQuietly(paths.startingMarker);
    }
  }

  private static int startChild(DaemonPaths paths, List<Path> libDirsForward,
                                List<String> extraChildArgs, Path syntheticSrcDir) {
    List<String> cmd = buildChildCommand(paths, libDirsForward, extraChildArgs, syntheticSrcDir);

    // Rotate the previous session's daemon log out of the way (.log → .log.1 → .log.2, max 3
    // generations). The fresh process gets a clean file via Redirect.to (truncate), replacing
    // the historical append-only growth pattern.
    rotateDaemonLog(paths.logFile, 3);

    ProcessBuilder pb = new ProcessBuilder(cmd);
    File logFile = paths.logFile.toFile();
    pb.redirectOutput(ProcessBuilder.Redirect.to(logFile));
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
      Optional<LockFile> ready = LockFile.readIfValid(paths.lockFile);
      if (ready.isPresent() && ready.get().libraryHash().equals(paths.libraryHash)
          && ProcessHandle.of(ready.get().pid()).isPresent()) {
        LockFile rl = ready.get();
        System.out.println("Daemon ready (PID " + rl.pid() + ")");
        warnIfUnauthenticated(rl, paths);
        return 0;
      }
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

    // The lock can appear in the instant between the last poll and the deadline, and then the
    // child is a healthy daemon that was merely slow. Look once more before treating the
    // timeout as a failure: killing here would turn a slow start into a lost one.
    Optional<LockFile> late = LockFile.readIfValid(paths.lockFile);
    if (late.isPresent() && late.get().libraryHash().equals(paths.libraryHash)
        && ProcessHandle.of(late.get().pid()).isPresent()) {
      System.out.println("Daemon ready (PID " + late.get().pid() + ")");
      warnIfUnauthenticated(late.get(), paths);
      return 0;
    }

    System.err.println("[ERROR] -d: daemon did not become ready within "
        + READY_TIMEOUT_SECONDS + "s; killing PID " + child.pid());
    if (stopUnreadyChild(child)) {
      LockFile.deleteQuietly(paths.lockFile);
    } else {
      // The lock stays. It is the only thing that can find this process again: deleting it
      // while the JVM is alive is exactly how a daemon becomes unreachable -- --daemon-ping and
      // --daemon-stop would both report "no daemon" against a socket that is still bound, and
      // the next -d cannot bind that name either because something is answering on it.
      System.err.println("[ERROR] -d: PID " + child.pid() + " is still alive; leaving "
          + paths.lockFile + " in place so it can still be found. Run `arend --daemon-stop`"
          + " for this library.");
    }
    return 1;
  }

  /**
   * Says so when the daemon that just came up is reachable without any access control.
   *
   * <p>{@link org.arend.frontend.cli.daemon.server.SocketBinder} already warns, but it warns in
   * the child, whose stdout and stderr are pointed at daemon.log before it runs -- so the one
   * person who could act on it is the one who cannot see it. The address is in the lock file,
   * which is how the parent knows, and this is the process still attached to the user's terminal.
   */
  private static void warnIfUnauthenticated(LockFile lock, DaemonPaths paths) {
    String address = lock.socketPath();
    if (address == null || !address.startsWith("tcp:")) return;
    System.err.println("[WARN] this daemon is listening on " + address.substring(4)
        + " with no access control: any local process can run commands against the warm library"
        + " or shut it down. See " + paths.logFile + " for why no Unix socket could be bound.");
  }

  /**
   * Kills a child that never became ready, reporting whether it is actually gone.
   *
   * <p>Escalates the way {@link DaemonStop} does -- SIGTERM, wait, SIGKILL, wait. A single
   * {@code destroy()} and an immediate return is not a kill: the JVM runs a shutdown hook, and
   * mid-typecheck that is not instant, so the caller would delete the lock of a process still
   * on its way out -- or still running.
   *
   * <p>Descendants are signalled too, and collected before the first signal because destroying
   * the parent is what loses them. On Linux the spawned process is {@code setsid}, which forks
   * when its caller is already a process-group leader, so {@code child} can be the wrapper with
   * the JVM underneath it; signalling only the wrapper leaves the daemon running.
   */
  private static boolean stopUnreadyChild(Process child) {
    List<ProcessHandle> tree = new ArrayList<>(child.descendants().toList());
    tree.add(child.toHandle());
    tree.forEach(ProcessHandle::destroy);
    if (awaitExit(tree, DaemonStop.GRACEFUL_TIMEOUT_SECONDS)) return true;
    System.err.println("[WARN]  -d: PID " + child.pid() + " did not exit within "
        + DaemonStop.GRACEFUL_TIMEOUT_SECONDS + "s; escalating to SIGKILL");
    tree.forEach(ProcessHandle::destroyForcibly);
    return awaitExit(tree, DaemonStop.FORCEFUL_TIMEOUT_SECONDS);
  }

  /** Whether every handle in {@code tree} is gone within {@code timeoutSeconds}. */
  private static boolean awaitExit(List<ProcessHandle> tree, int timeoutSeconds) {
    long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
    while (System.currentTimeMillis() < deadline) {
      if (tree.stream().noneMatch(ProcessHandle::isAlive)) return true;
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    return tree.stream().noneMatch(ProcessHandle::isAlive);
  }

  /**
   * What an existing lock file means for a start attempt. Separated from {@link #run} because
   * {@code run} spawns a JVM and cannot be exercised: every one of these branches decides
   * whether a possibly-running daemon keeps its lock, and they were reachable only by starting
   * daemons by hand.
   */
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
   * Liveness is the PID and nothing else.
   *
   * <p>A socket probe would also catch a recycled PID, but it can fail for reasons that have
   * nothing to do with the daemon being gone -- and every caller that concludes "dead" here goes
   * on to remove the lock, which is how a running daemon gets stranded. A false "still running"
   * costs the user one {@code --daemon-stop}; a false "dead" costs them a process they can no
   * longer reach.
   */
  private static boolean isAlive(LockFile lf) {
    return ProcessHandle.of(lf.pid()).isPresent();
  }

  /**
   * Claims the right to start a daemon for this library, atomically.
   *
   * <p>{@code CREATE_NEW} is the whole mechanism: between reading the lock and the child writing
   * one there is a window of however long a cold typecheck takes, and two calls in that window
   * both see no daemon and both spawn one. The loser cannot bind the socket and quietly becomes
   * an unreachable JVM holding a second copy of the library.
   *
   * <p>A marker older than a start could possibly take is treated as debris from a parent that
   * was killed, and reclaimed -- otherwise one interrupted start would block every later one.
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
      // below reports that properly. Refusing here would only hide it behind a worse message.
      return true;
    }
  }

  /**
   * Whether a JVM option this process was given should be handed to the child.
   *
   * <p>Everything except the ones that cannot exist twice. Two JVMs cannot hold the same fixed
   * port and cannot own the same fixed file, so an option naming either kills the daemon during
   * VM init -- before it can report anything. All the parent sees then is "daemon exited before
   * becoming ready (exit code 1)", which names neither the option nor the resource, so the
   * failure has to be prevented rather than diagnosed.
   *
   * <p>Heap and stack sizing is deliberately absent: the daemon does the work this process would
   * have done, so it needs the same {@code -Xmx} and the same {@code -Xss}. A child assembled
   * from just the classpath silently gets the defaults and then fails on libraries the direct
   * call handles.
   *
   * <p>A deny-list cannot be complete -- a {@code -D} naming a port for some other library is
   * indistinguishable from any other system property -- so this covers the options the JDK
   * itself defines that way.
   */
  static boolean isForwardableJvmOption(String option) {
    // An agent is attached to one specific JVM; a jdwp transport binds a debugger port.
    if (option.startsWith("-agentlib:") || option.startsWith("-javaagent:")
        || option.startsWith("-agentpath:") || option.startsWith("-Xrunjdwp")) return false;
    // The management agent: a fixed JMX/RMI port and everything that configures one.
    if (option.startsWith("-Dcom.sun.management.")) return false;
    // A flight recording pinned to a filename, and the repository/settings paths beside it.
    if (option.startsWith("-XX:StartFlightRecording")
        || option.startsWith("-XX:FlightRecorderOptions")) return false;
    // Unified logging and the legacy GC log, when either names a file rather than a stream.
    if (option.startsWith("-Xlog:") && option.contains("file=")) return false;
    if (option.startsWith("-Xloggc:")) return false;
    // Dumps and reports written to a path the user fixed.
    if (option.startsWith("-XX:HeapDumpPath=")
        || option.startsWith("-XX:ErrorFile=")
        || option.startsWith("-XX:LogFile=")) return false;
    return true;
  }

  static List<String> buildChildCommand(DaemonPaths paths,
                                                List<Path> libDirsForward,
                                                List<String> extraChildArgs,
                                                Path syntheticSrcDir) {
    String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    String cp = System.getProperty("java.class.path");

    List<String> cmd = new ArrayList<>();

    // Detach the child from the parent's controlling terminal so closing the parent
    // shell doesn't SIGHUP the daemon. JDK has no native equivalent.
    //   * Linux: setsid (util-linux) starts a new session — strongest guarantee.
    //   * macOS: no setsid on default PATH; use nohup (POSIX-standard, always present)
    //     which ignores SIGHUP in the child. Sufficient since stdio is already redirected.
    //   * Windows: nothing — detached behaviour comes from the redirected stdio.
    if (Platform.isMac()) {
      cmd.add("nohup");
    } else if (!Platform.isWindows()) {
      cmd.add("setsid");
    }
    cmd.add(javaBin);
    // The daemon does exactly the work this process would have done, so it needs the same heap
    // and the same stack: typechecking is deeply recursive, and the installed launcher supplies
    // both on the command line. A child assembled from just the classpath silently gets the
    // defaults, and then fails on libraries the direct call handles.
    for (String option : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
      if (isForwardableJvmOption(option)) cmd.add(option);
    }
    cmd.add("-cp");
    cmd.add(cp);
    cmd.add("org.arend.frontend.ConsoleMain");
    cmd.add(syntheticSrcDir != null ? "--daemon-bootstrap-synthetic" : "--daemon-bootstrap");
    cmd.add(syntheticSrcDir != null ? syntheticSrcDir.toString() : paths.libraryConfig.toString());
    for (Path libDir : libDirsForward) {
      cmd.add("-L");
      cmd.add(libDir.toString());
    }
    cmd.addAll(extraChildArgs);                 // bootstrap-affecting flags forwarded by ConsoleMain
    // The canonical config path, and not the reference the user typed. The parent resolved the
    // library cwd-first (DaemonPaths.resolveByName) and keyed the lock on what it found; a bare
    // name reaching the child is resolved libDirs-first (CliSetup.findLibrary). A name that
    // matches both a library in the current directory and one under a -L directory therefore
    // warms the libDir copy behind a lock advertising the cwd one, and every command routed
    // afterwards is answered from source the user is not looking at. findLibrary's own
    // shadowing warning does not save it: this process's output goes to daemon.log.
    // An absolute arend.yaml is the one form both resolvers agree on.
    //
    // Synthetic mode has no library to name -- its pipeline is driven by -s.
    if (syntheticSrcDir == null) {
      cmd.add(paths.libraryConfig.toString());  // positional library for the inner pipeline
    }
    return cmd;
  }

  private static File nullDevice() {
    return new File(Platform.isWindows() ? "NUL" : "/dev/null");
  }

  /**
   * Rotate {@code daemon.log} → {@code daemon.log.1} → {@code daemon.log.2} … keeping
   * at most {@code keep} historical generations (so the highest-numbered file is dropped).
   * Best-effort: filesystem errors are reported and proceed without rotation.
   */
  private static void rotateDaemonLog(Path logFile, int keep) {
    if (!Files.exists(logFile)) return;
    try {
      // Drop the oldest generation, then shift each one down.
      Path oldest = logFile.resolveSibling(logFile.getFileName() + "." + keep);
      Files.deleteIfExists(oldest);
      for (int i = keep - 1; i >= 1; i--) {
        Path src = logFile.resolveSibling(logFile.getFileName() + "." + i);
        Path dst = logFile.resolveSibling(logFile.getFileName() + "." + (i + 1));
        if (Files.exists(src)) Files.move(src, dst, StandardCopyOption.REPLACE_EXISTING);
      }
      Files.move(logFile, logFile.resolveSibling(logFile.getFileName() + ".1"),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      System.err.println("[WARN]  -d: could not rotate " + logFile + ": " + e.getMessage());
    }
  }


}
