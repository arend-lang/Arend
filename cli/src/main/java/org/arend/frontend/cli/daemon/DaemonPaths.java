package org.arend.frontend.cli.daemon;

import org.arend.util.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Filesystem layout for one daemon instance, plus the library identity it serves.
 *
 * <p>A daemon is keyed by the canonical absolute path of its {@code arend.yaml} library
 * config file. Every per-daemon file lives under {@code <library>/.arend/}:
 * <ul>
 *   <li>{@code daemon.lock} — atomically-written properties file produced by the child
 *       once initial typecheck completes; absence means "no daemon (or not ready yet)".</li>
 *   <li>{@code daemon.log}  — fresh stdout+stderr capture for the current daemon process.
 *       Truncated at every daemon start; the previous session is preserved as
 *       {@code daemon.log.1} (up to {@code .3}). Per-invocation verbose output lives in
 *       {@code log/} (see {@link org.arend.frontend.cli.ai.InvocationLog}), not here.</li>
 *   <li>{@code daemon.sock} — Unix domain socket, written in M3.</li>
 * </ul>
 *
 * <p>Resolved once at startup and threaded through; the only mutating operation here is
 * {@link #ensureArendDir()}, which is idempotent.
 */
public final class DaemonPaths {
  /** Canonical absolute path of the {@code arend.yaml} this daemon serves. */
  public final Path libraryConfig;
  /** {@code <library>/.arend/} */
  public final Path arendDir;
  public final Path lockFile;
  public final Path logFile;
  public final Path socketFile;
  /** Hex-encoded SHA-256 of {@link #libraryConfig}'s string form, first 16 chars. */
  public final String libraryHash;

  private DaemonPaths(Path libraryConfig, String libraryHash) {
    this.libraryConfig = libraryConfig;
    this.libraryHash = libraryHash;
    Path libRoot = libraryConfig.getParent();
    this.arendDir = libRoot.resolve(".arend");
    this.lockFile = arendDir.resolve("daemon.lock");
    this.logFile = arendDir.resolve("daemon.log");
    this.socketFile = arendDir.resolve("daemon.sock");
  }

  /**
   * Build a {@code DaemonPaths} from a user-supplied library reference. The reference
   * may be a directory containing {@code arend.yaml}, the {@code arend.yaml} file itself,
   * or any path that resolves to one of those after canonicalisation. Returns null if
   * the path doesn't resolve to a valid config file (caller logs).
   */
  public static DaemonPaths resolve(Path libraryRef) {
    try {
      Path probe = libraryRef.toAbsolutePath();
      Path config;
      if (Files.isDirectory(probe)) {
        config = probe.resolve(FileUtils.LIBRARY_CONFIG_FILE);
      } else if (probe.getFileName() != null
          && probe.getFileName().toString().equals(FileUtils.LIBRARY_CONFIG_FILE)) {
        config = probe;
      } else {
        return null;
      }
      if (!Files.isRegularFile(config)) return null;
      Path canonical = config.toRealPath();
      return new DaemonPaths(canonical, computeHash(canonical.toString()));
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Synthetic-library daemon path: no {@code arend.yaml} on disk. The daemon is keyed to
   * {@code sourceDir}'s absolute path; the per-daemon state lives in
   * {@code <sourceDir>/.arend/}. Used by {@code arend -d -s <dir>} so a bare source
   * directory can be served by the daemon without forcing the user to create a stub
   * config file. The {@code libraryConfig} path returned is conceptual ({@code
   * <sourceDir>/.arend-synthetic}) — it is never created on disk; only its parent (the
   * source dir) and the hash of the canonical source path matter.
   */
  public static DaemonPaths resolveSynthetic(Path sourceDir) {
    Path canonical;
    try {
      canonical = sourceDir.toAbsolutePath().normalize();
      if (Files.isDirectory(canonical)) canonical = canonical.toRealPath();
    } catch (IOException e) {
      canonical = sourceDir.toAbsolutePath().normalize();
    }
    Path phantomConfig = canonical.resolve(".arend-synthetic");
    return new DaemonPaths(phantomConfig, computeHash(canonical + "#synthetic"));
  }

  /**
   * Resolve a library reference to a {@link DaemonPaths}, with cwd preference and
   * shadow detection.
   *
   * <p>Search order:
   * <ol>
   *   <li>Cwd's own {@code arend.yaml} — preferred when its containing directory's
   *       name equals {@code libName}. Without this preference, {@code arend
   *       --daemon-stop arend-lib} from inside {@code arend-lib/} would silently
   *       target a like-named system copy in {@code ~/.arend/libs/} because
   *       {@code Paths.get("arend-lib")} resolves to {@code <cwd>/arend-lib}
   *       (which doesn't exist when cwd <em>is</em> {@code arend-lib}).</li>
   *   <li>{@code Paths.get(libName)} — direct path, cwd-anchored or absolute.</li>
   *   <li>Each {@code libDir} in order: {@code <libDir>/<libName>/arend.yaml}.</li>
   * </ol>
   *
   * <p>When the cwd-anchored config wins step 1 AND a like-named config also exists
   * in one of {@code libDirs}, a {@code [WARN] library 'X' has multiple copies}
   * line is emitted to stderr so the choice is visible.
   */
  public static DaemonPaths resolveByName(String libName, java.util.List<Path> libDirs) {
    // (1) cwd's arend.yaml wins when its parent dir name matches libName.
    DaemonPaths cwd = resolve(Paths.get("."));
    if (cwd != null && libName.equals(cwdLibName(cwd.libraryConfig))) {
      warnIfShadowed(libName, cwd.libraryConfig, libDirs);
      return cwd;
    }
    // (2) Direct path (cwd-anchored or absolute).
    DaemonPaths direct = resolve(Paths.get(libName));
    if (direct != null) return direct;
    // (3) Search the configured libDirs.
    for (Path libDir : libDirs) {
      DaemonPaths d = resolve(libDir.resolve(libName));
      if (d != null) return d;
    }
    return null;
  }

  /**
   * Library name as derived by {@link org.arend.frontend.library.FileSourceLibrary#fromConfigFile}:
   * the name of the directory containing {@code arend.yaml}.
   */
  private static String cwdLibName(Path libraryConfig) {
    Path parent = libraryConfig.getParent();
    Path dirName = parent == null ? null : parent.getFileName();
    return dirName == null ? null : dirName.toString();
  }

  private static void warnIfShadowed(String libName, Path chosen, java.util.List<Path> libDirs) {
    for (Path libDir : libDirs) {
      Path other = libDir.resolve(libName).resolve(org.arend.util.FileUtils.LIBRARY_CONFIG_FILE);
      if (!java.nio.file.Files.isRegularFile(other)) continue;
      try {
        if (other.toRealPath().equals(chosen.toRealPath())) continue;
      } catch (IOException ignored) {
        // fall through to the warning — we can still surface the name conflict
      }
      System.err.println("[WARN] library '" + libName + "' has multiple copies: "
          + chosen + " and " + other.toAbsolutePath() + " — using " + chosen + ".");
    }
  }

  /** Idempotently create {@code <library>/.arend/}. */
  public void ensureArendDir() throws IOException {
    if (!Files.isDirectory(arendDir)) {
      Files.createDirectories(arendDir);
    }
  }

  private static String computeHash(String input) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      byte[] digest = sha.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("SHA-256 unavailable", e);
    }
  }
}
