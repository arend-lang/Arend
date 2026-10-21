package org.arend.frontend.cli.daemon;

import org.arend.daemon.BuildIdentity;
import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.Platform;
import org.arend.daemon.StartMarker;
import org.arend.daemon.mcp.McpRegistration;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Parent-side: start a detached daemon JVM for one or more libraries, as
 * {@code arend -d <libRef>...}, loaded into one server in the order given -- the way IntelliJ
 * holds every library of a project. Two daemons never share a library they own; libraries under
 * {@link DaemonPaths#sharedLibrariesRoot}, and zips, are owned by none and any daemon may load them.
 *
 * <p>Resolve {@link DaemonPaths} for each; decide what each existing lock means
 * ({@link #inspectLock}); claim the right to start ({@link StartMarker#reserve}); spawn
 * {@code [setsid|nohup] java <opts> -cp <cp> ConsoleMain --daemon-bootstrap <arend.yaml> [-L ...]
 * <arend.yaml>...} with stdio pointed at daemon.log; then poll for the lock the child writes when it
 * is ready, for up to {@value #READY_TIMEOUT_SECONDS}s. The child only loads and resolves its
 * libraries, typechecking nothing; arend-lib takes some seconds.
 */
public final class DaemonStart {
  /** Max time to wait for the child to write its lock file. */
  public static final int READY_TIMEOUT_SECONDS = (int) (DaemonPaths.START_TIMEOUT_MILLIS / 1000);

  private DaemonStart() {}

  /**
   * @param libRefs        user-supplied library references (directory, arend.yaml, or name), in
   *                       load order; the first owned one's {@code .arend/} holds the socket and
   *                       the log
   * @param libDirsForward extra {@code -L} dirs to forward to the child
   * @param extraChildArgs bootstrap-affecting flags appended after the {@code -L} forwards
   */
  public static int run(List<String> libRefs, List<Path> libDirsForward, List<String> extraChildArgs) {
    List<DaemonPaths> libraries = new ArrayList<>(libRefs.size());
    for (String libRef : libRefs) {
      DaemonPaths paths = DaemonPaths.resolveByName(libRef, libDirsForward);
      if (paths == null) {
        System.err.println("[ERROR] -d: could not resolve library reference '" + libRef + "'");
        return 1;
      }
      if (libraries.stream().anyMatch(other -> other.libraryConfig.equals(paths.libraryConfig))) {
        System.err.println("[ERROR] -d: " + paths.libraryConfig + " is listed more than once");
        return 1;
      }
      // A server holds one library per name, and a dependency names nothing else: the second one
      // would be skipped as already loaded, while the lock went on claiming it.
      DaemonPaths namesake = libraries.stream()
          .filter(other -> other.libraryName().equals(paths.libraryName())).findFirst().orElse(null);
      if (namesake != null) {
        System.err.println("[ERROR] -d: " + namesake.libraryConfig + " and " + paths.libraryConfig + " are both"
            + " named '" + paths.libraryName() + "'; a daemon can hold only one library of a name.");
        return 1;
      }
      libraries.add(paths);
    }
    List<String> requested = libraries.stream().map(paths -> paths.libraryConfig.toString()).toList();
    // Neither a shared library nor a zip takes part in the checks below, nor gets anything of this daemon's.
    List<DaemonPaths> owned = libraries.stream().filter(DaemonPaths::canOwn).toList();
    if (owned.isEmpty()) {
      System.err.println("[ERROR] -d: list at least one library directory outside " + DaemonPaths.sharedLibrariesRoot()
          + ": its .arend/ holds the daemon's socket and log, and no daemon owns a shared library or a zip.");
      return 1;
    }

    for (DaemonPaths paths : owned) {
      try {
        paths.ensureArendDir();
      } catch (IOException e) {
        System.err.println("[ERROR] -d: cannot create " + paths.arendDir + ": " + e.getMessage());
        return 1;
      }
    }

    // Every lock is consulted before anything starts: two daemons must not answer for one library.
    LockFile running = null;
    for (DaemonPaths paths : owned) {
      LockFile.Read existing = LockFile.lookup(paths);
      switch (inspectLock(existing, paths.libraryConfig, requested, LockFile::processAlive)) {
        case UNREADABLE_LOCK -> {
          // Something is there and we cannot tell whether it is live. Starting a second daemon on
          // top of a running one is the one outcome worth refusing outright.
          String reason = ((LockFile.Read.Unreadable) existing).reason();
          System.err.println("[ERROR] -d: lock file at " + paths.lockFile + " cannot be read ("
              + reason + "). Stop any running daemon and remove the file by hand.");
          return 1;
        }
        case ALREADY_RUNNING -> running = lockOf(existing);
        case OVERLAP -> {
          LockFile lf = lockOf(existing);
          System.err.println("[ERROR] -d: " + paths.libraryConfig + " is already served by the daemon (PID "
              + lf.pid() + ") for " + String.join(", ", lf.libraries()) + "; stop it first (`arend --daemon-stop`).");
          return 1;
        }
        case STALE_LOCK -> {
          System.err.println("[WARN]  -d: removing stale lock for dead PID " + lockOf(existing).pid()
              + " at " + paths.lockFile);
          // Every copy: a daemon of several libraries left one in each, and one in the registry.
          lockOf(existing).deleteAllCopies();
        }
        case UNREGISTERED_LOCK -> {
          System.err.println("[WARN]  -d: lock file at " + paths.lockFile + " is not confirmed by the registry in "
              + DaemonPaths.registryDir() + "; removing it");
          LockFile.deleteQuietly(paths.lockFile);
        }
        case STALE_MISMATCH -> {
          System.err.println("[WARN]  -d: lock file at " + paths.lockFile
              + " is for a different library and its process is gone; removing");
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
    }
    if (running != null) {
      System.out.println("Arend daemon already running for " + String.join(", ", requested)
          + " (PID " + running.pid() + ")");
      return 0;
    }

    // A marker in every library: two starts whose sets merely overlap race like two identical ones.
    List<Path> reserved = new ArrayList<>(owned.size());
    try {
      for (DaemonPaths paths : owned) {
        if (!StartMarker.reserve(paths.startingMarker)) {
          System.err.println("[ERROR] -d: another daemon start for " + paths.libraryConfig
              + " is already in progress (" + paths.startingMarker + "). Wait for it to finish, or"
              + " remove that file if no start is running.");
          return 1;
        }
        reserved.add(paths.startingMarker);
      }
      return startChild(libraries, requested, libDirsForward, extraChildArgs, reserved);
    } finally {
      reserved.forEach(marker -> StartMarker.release(marker, ProcessHandle.current()));
    }
  }

  /**
   * {@code arend --mcp-init [DIR]}: registers the {@code arend-daemons} MCP server in the configs of the
   * project in {@code projectDir} of every agent installed, saying which files it wrote -- as
   * {@code arend --mcp}, so only when the {@code arend} on this PATH, which agents and daemons started
   * from this shell see too, serves this build's daemons.
   */
  public static int initMcp(Path projectDir) {
    McpRegistration.ArendCheck arend = McpRegistration.checkArend(System.getenv("PATH"));
    if (!arend.usable()) {
      System.err.println("[ERROR] --mcp-init: " + McpRegistration.arendAdvice(arend) + " Nothing was registered.");
      return 1;
    }
    McpRegistration.Result result = McpRegistration.initProject(projectDir, McpRegistration.PORTABLE);
    result.failures().forEach(failure -> System.err.println("[WARN]  --mcp-init: " + failure));
    if (result.done().isEmpty()) {
      if (result.failures().isEmpty()) System.out.println("No agent that reads a project's own MCP config is installed.");
    } else {
      System.out.println("Registered the " + McpRegistration.SERVER_NAME + " MCP server for the project in " + projectDir
          + ", for agents started there from now on:");
      result.done().forEach(file -> System.out.println("  " + file));
      System.out.println("`arend --mcp-unregister` takes it back out.");
    }
    // After the files: what they say would be lost above them, and it is what the user has to decide on.
    result.warnings().forEach(warning -> System.err.println("[WARN]  --mcp-init: " + warning));
    result.notes().forEach(note -> System.out.println("[INFO] " + note));
    return result.failures().isEmpty() ? 0 : 1;
  }

  /**
   * {@code arend --mcp-unregister [DIR]}: takes the server out of the configs of the project in
   * {@code projectDir}, and nowhere else, printing each entry it removed so that it can be put back.
   */
  public static int unregisterMcp(Path projectDir) {
    McpRegistration.Result result = McpRegistration.unregisterProject(projectDir);
    result.failures().forEach(failure -> System.err.println("[WARN]  --mcp-unregister: " + failure));
    if (result.done().isEmpty()) {
      if (result.failures().isEmpty()) System.out.println("No agent's config of the project in " + projectDir + " has the "
          + McpRegistration.SERVER_NAME + " MCP server.");
    } else {
      result.done().forEach(file -> System.out.println("Removed the " + McpRegistration.SERVER_NAME + " MCP server from " + file));
    }
    result.warnings().forEach(warning -> System.err.println("[WARN]  --mcp-unregister: " + warning));
    return result.failures().isEmpty() ? 0 : 1;
  }

  private static int startChild(List<DaemonPaths> libraries, List<String> requested, List<Path> libDirsForward,
                                List<String> extraChildArgs, List<Path> markers) {
    DaemonPaths paths = home(libraries);
    // This process's own classpath: a daemon whose jar is rebuilt or replaced under it retires (BuildIdentity).
    // A single jar is run from a copy, which the rebuild does not touch, so that the daemon can still
    // read its classes to say it is retiring -- and, on Windows, so that its open jar does not keep
    // the jar itself from being replaced or deleted.
    String classpath = System.getProperty("java.class.path");
    List<String> jvmProperties = new ArrayList<>();
    BuildIdentity build = BuildIdentity.ofThisProcess();
    if (build != null) {
      try {
        classpath = runnableCopy(build, jarCopiesDir()).toString();
        jvmProperties.add("-D" + BuildIdentity.ORIGINAL_JAR_PROPERTY + "=" + build.path());
      } catch (IOException e) {
        System.err.println("[WARN]  -d: could not copy " + build.path() + " for the daemon to run from (" + e.getMessage()
            + "); it runs from the jar itself, and a rebuild of it breaks the daemon");
      }
    }
    List<String> cmd = buildChildCommand(libraries, libDirsForward, extraChildArgs, classpath, jvmProperties);
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
    // The child is detached: killed, this process leaves it loading, and the start under way with it.
    markers.forEach(marker -> StartMarker.add(marker, child.toHandle()));

    System.out.println("Starting Arend daemon for " + String.join(", ", requested));
    System.out.println("  log: " + paths.logFile);
    System.out.println("  waiting for the libraries to be resolved...");

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
    boolean gone = ProcessKill.terminateTree(child.toHandle(),
        () -> System.err.println("[WARN]  -d: PID " + child.pid() + " did not exit within "
            + ProcessKill.GRACEFUL_TIMEOUT_SECONDS + "s; escalating to SIGKILL"));
    if (gone) {
      // The child writes its registry entry before any other copy of its lock, so a child that
      // left a copy anywhere is found here.
      LockFile.registered().stream().filter(lf -> lf.pid() == child.pid()).forEach(LockFile::deleteAllCopies);
    } else {
      // The lock stays: it is the only thing that can find this process again.
      System.err.println("[ERROR] -d: PID " + child.pid() + " is still alive; leaving "
          + paths.lockFile + " in place so it can still be found. Run `arend --daemon-stop`"
          + " for this library.");
    }
    return 1;
  }

  /**
   * Whether the child has published a usable lock; says so, and warns about a bare TCP port. The
   * first library's lock is the child's last, so it is there only once all of them are.
   */
  private static boolean reportIfReady(DaemonPaths paths) {
    if (!(LockFile.lookup(paths) instanceof LockFile.Read.Found ready) || !ready.lock().processAlive()) return false;
    LockFile lock = ready.lock();
    System.out.println("Daemon ready (PID " + lock.pid() + ")");
    // SocketBinder already warns, but it warns in the child, whose output is pointed at
    // daemon.log before it runs -- so the one person who could act on it cannot see it.
    String address = lock.socketPath();
    if (address != null && address.startsWith("tcp:")) {
      System.err.println("[WARN] this daemon is listening on " + address.substring(4)
          + ", which any local process can connect to; only the daemon's token keeps it from running"
          + " commands against the warm library or shutting it down. See " + paths.logFile
          + " for why no Unix socket could be bound.");
    }
    return true;
  }

  /** What an existing lock file means for a start attempt. */
  enum Verdict {
    /** No lock, or nothing that stops us. */
    NO_DAEMON,
    /** A lock we cannot parse; we do not know what is running. */
    UNREADABLE_LOCK,
    /**
     * A library's copy no registry entry confirms. It names no daemon a client could reach -- only
     * the registry's entry has the token -- so it stops nothing, and it may be anybody's plant.
     */
    UNREGISTERED_LOCK,
    /** A healthy daemon already serves exactly the libraries asked for. */
    ALREADY_RUNNING,
    /**
     * A healthy daemon serves this library among a different set. Two daemons must not answer for
     * the same library, so the start is refused -- and the lock kept, since it is that daemon's.
     */
    OVERLAP,
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
  static Verdict inspectLock(LockFile.Read existing, Path libraryConfig, Collection<String> requested,
                             java.util.function.Predicate<LockFile> alive) {
    if (existing instanceof LockFile.Read.Unreadable) return Verdict.UNREADABLE_LOCK;
    if (existing instanceof LockFile.Read.Unregistered) return Verdict.UNREGISTERED_LOCK;
    if (!(existing instanceof LockFile.Read.Found found)) return Verdict.NO_DAEMON;
    LockFile lf = found.lock();
    boolean live = alive.test(lf);
    if (!lf.serves(libraryConfig)) {
      return live ? Verdict.LIVE_MISMATCH : Verdict.STALE_MISMATCH;
    }
    if (!live) return Verdict.STALE_LOCK;
    return new HashSet<>(lf.libraries()).equals(new HashSet<>(requested)) ? Verdict.ALREADY_RUNNING : Verdict.OVERLAP;
  }

  private static LockFile lockOf(LockFile.Read read) {
    return ((LockFile.Read.Found) read).lock();
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

  /** The library whose {@code .arend/} holds the daemon's socket and log: the first one it owns. */
  static DaemonPaths home(List<DaemonPaths> libraries) {
    return libraries.stream().filter(DaemonPaths::canOwn).findFirst().orElse(libraries.getFirst());
  }

  static List<String> buildChildCommand(List<DaemonPaths> libraries, List<Path> libDirsForward,
                                        List<String> extraChildArgs, String classpath) {
    return buildChildCommand(libraries, libDirsForward, extraChildArgs, classpath, List.of());
  }

  /** As above, with {@code jvmProperties} -- {@code -Dname=value} -- set in the child after the ones forwarded. */
  static List<String> buildChildCommand(List<DaemonPaths> libraries, List<Path> libDirsForward,
                                        List<String> extraChildArgs, String classpath, List<String> jvmProperties) {
    DaemonPaths paths = home(libraries);
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
    cmd.addAll(jvmProperties);
    cmd.add("-cp");
    cmd.add(classpath);
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
    // In load order: a library may depend on one listed before it.
    for (DaemonPaths library : libraries) cmd.add(library.libraryConfig.toString());
    return cmd;
  }

  /** Where daemons' copies of the arend jar are kept, one per build: see {@link #runnableCopy}. */
  static Path jarCopiesDir() {
    return DaemonPaths.configDir().resolve("daemon-jars");
  }

  /**
   * How long a copy no daemon may be running from is kept anyway: one just made, or just picked
   * again, may belong to a daemon whose JVM has not opened it yet.
   */
  private static final long COPY_GRACE_MILLIS = 10 * 60 * 1000;

  /**
   * A copy of the jar of {@code build} in {@code dir} for a daemon to run from: the one there is, if
   * this build has one, else a new one. The others are removed on the way, but for one a daemon still
   * runs from -- Windows refuses to delete it, and a Unix JVM keeps the file it has open -- or one that
   * is younger than {@link #COPY_GRACE_MILLIS}.
   */
  static Path runnableCopy(BuildIdentity build, Path dir) throws IOException {
    Files.createDirectories(dir);
    Path copy = dir.resolve(copyName(build));
    try (var copies = Files.newDirectoryStream(dir, "*.jar")) {
      long cutoff = System.currentTimeMillis() - COPY_GRACE_MILLIS;
      for (Path other : copies) {
        try {
          if (!other.equals(copy) && Files.getLastModifiedTime(other).toMillis() < cutoff) Files.delete(other);
        } catch (IOException ignored) {
          // In use, most likely: left for a later start.
        }
      }
    }
    if (Files.isRegularFile(copy) && Files.size(copy) == build.size()) {
      // Picked again: young again, so that no other start removes it before this daemon has it open.
      Files.setLastModifiedTime(copy, FileTime.fromMillis(System.currentTimeMillis()));
      return copy;
    }
    Path tmp = Files.createTempFile(dir, copy.getFileName() + ".", ".tmp");
    try {
      Files.copy(Path.of(build.path()), tmp, StandardCopyOption.REPLACE_EXISTING);
      // A rebuild between this process starting and the copy is a copy of another build.
      if (!build.isCurrent()) throw new IOException(build.path() + " has changed since this process started");
      try {
        Files.move(tmp, copy, StandardCopyOption.ATOMIC_MOVE);
      } catch (IOException e) {
        // Another start of this build made it first; on Windows, one a daemon runs from cannot be replaced.
        if (!Files.isRegularFile(copy) || Files.size(copy) != build.size()) throw e;
      }
    } finally {
      Files.deleteIfExists(tmp);
    }
    return copy;
  }

  /** The name of the copy of {@code build}'s jar: one for every jar as it was, so that two builds never share one. */
  private static String copyName(BuildIdentity build) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(build.toString().getBytes(StandardCharsets.UTF_8));
      return "arend-" + HexFormat.of().formatHex(digest, 0, 8) + ".jar";
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
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
