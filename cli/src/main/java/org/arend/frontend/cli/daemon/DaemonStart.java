package org.arend.frontend.cli.daemon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 *       --daemon-bootstrap <libRef> [-L ...] -ai}. Linux uses {@code setsid} (util-linux);
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
   * @param libRef         user-supplied library reference (directory, arend.yaml, or name)
   * @param libDirsForward extra {@code -L} dirs to forward to the child
   * @return exit code (0 success, 1 failure)
   */
  public static int run(String libRef, List<Path> libDirsForward) {
    return run(libRef, libDirsForward, List.of(), null);
  }

  /**
   * @param syntheticSrcDir  non-null → synthetic-library daemon (no arend.yaml on disk);
   *                         daemon state lives in {@code <syntheticSrcDir>/.arend/}.
   * @param extraChildArgs   appended to the child JVM's command line after {@code -L}
   *                         forwards and before {@code -ai}. Used to pass {@code -s/-e/-m}
   *                         when starting a synthetic-library daemon.
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
    Optional<LockFile> existing = LockFile.read(paths.lockFile);
    if (existing.isPresent()) {
      LockFile lf = existing.get();
      if (!lf.libraryHash.equals(paths.libraryHash)) {
        System.err.println("[WARN]  -d: lock file at " + paths.lockFile
            + " is for a different library hash; removing");
        LockFile.deleteQuietly(paths.lockFile);
      } else if (ProcessHandle.of(lf.pid).isPresent()) {
        System.out.println("Arend daemon already running for " + paths.libraryConfig
            + " (PID " + lf.pid + ")");
        return 0;
      } else {
        System.err.println("[WARN]  -d: removing stale lock for dead PID " + lf.pid);
        LockFile.deleteQuietly(paths.lockFile);
      }
    }

    List<String> cmd = buildChildCommand(libRef, paths, libDirsForward, extraChildArgs, syntheticSrcDir);

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
      Optional<LockFile> ready = LockFile.read(paths.lockFile);
      if (ready.isPresent() && ready.get().libraryHash.equals(paths.libraryHash)
          && ProcessHandle.of(ready.get().pid).isPresent()) {
        LockFile rl = ready.get();
        System.out.println("Daemon ready (PID " + rl.pid + ")");
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

    System.err.println("[ERROR] -d: daemon did not become ready within "
        + READY_TIMEOUT_SECONDS + "s; killing PID " + child.pid());
    child.destroy();
    LockFile.deleteQuietly(paths.lockFile);
    return 1;
  }

  private static List<String> buildChildCommand(String libRef, DaemonPaths paths,
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
    if (isMac()) {
      cmd.add("nohup");
    } else if (!isWindows()) {
      cmd.add("setsid");
    }
    cmd.add(javaBin);
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
    // -ai forces signature mirror + symbol index refresh after the typecheck, satisfying
    // requirement #9 (daemon keeps signatures + binary index consistent).
    cmd.add("-ai");
    // Skip the inner positional when libRef is "." (cwd default) or when we're in
    // synthetic mode (the inner pipeline is driven by -s, not by a library name).
    if (syntheticSrcDir == null && !".".equals(libRef)) {
      cmd.add(libRef);                          // positional library for the inner pipeline
    }
    return cmd;
  }

  private static File nullDevice() {
    return new File(isWindows() ? "NUL" : "/dev/null");
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

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }

  private static boolean isMac() {
    String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return osName.startsWith("mac") || osName.contains("darwin");
  }
}
