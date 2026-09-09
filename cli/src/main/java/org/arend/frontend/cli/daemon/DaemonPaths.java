package org.arend.frontend.cli.daemon;

import org.arend.util.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

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
 *       {@code daemon.log.1} (up to {@code .3}).</li>
 *   <li>{@code daemon.sock} — Unix domain socket clients connect to.</li>
 * </ul>
 *
 * <p>A synthetic daemon ({@link #resolveSynthetic}) shares the {@code .arend} directory of the
 * source dir it serves but uses {@code daemon-synthetic.*} for all three, so it and a real
 * daemon for the same directory can be running at once without either one's recovery path
 * mistaking the other's lock for a stale one.
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
  /**
   * {@code <basename>.starting} — created by the parent before it spawns a child, removed once
   * the child is ready or has failed. Two {@code arend -d} calls racing would otherwise both
   * see no lock and both spawn, and only one of them can bind the socket.
   */
  public final Path startingMarker;
  /** Hex-encoded SHA-256 of {@link #libraryConfig}'s string form, first 16 chars. */
  public final String libraryHash;

  /** Basename of the per-daemon files for a daemon keyed to an {@code arend.yaml}. */
  private static final String LIBRARY_BASENAME = "daemon";
  /**
   * Basename for a synthetic daemon. A synthetic daemon and the library at the same path are
   * different daemons with different hashes, but they resolve to the same {@code .arend}
   * directory -- so if they also shared these names, each one's lock would look to the other
   * like "a lock for a different library", which every recovery path here removes.
   */
  private static final String SYNTHETIC_BASENAME = "daemon-synthetic";

  private DaemonPaths(Path libraryConfig, String libraryHash, String basename) {
    this.libraryConfig = libraryConfig;
    this.libraryHash = libraryHash;
    Path libRoot = libraryConfig.getParent();
    if (libRoot == null) {
      throw new IllegalArgumentException("library config has no parent directory: " + libraryConfig);
    }
    this.arendDir = libRoot.resolve(".arend");
    this.lockFile = arendDir.resolve(basename + ".lock");
    this.logFile = arendDir.resolve(basename + ".log");
    this.socketFile = arendDir.resolve(basename + ".sock");
    this.startingMarker = arendDir.resolve(basename + ".starting");
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
      return new DaemonPaths(canonical, computeHash(canonical.toString()), LIBRARY_BASENAME);
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
   * source dir) and the hash of the canonical source path matter. The per-daemon files are
   * named {@code daemon-synthetic.*} so they never collide with a real daemon's.
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
    return new DaemonPaths(phantomConfig, computeHash(canonical + "#synthetic"), SYNTHETIC_BASENAME);
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
  public static DaemonPaths resolveByName(String libName, List<Path> libDirs) {
    return resolveByName(libName, libDirs, true);
  }

  /**
   * As {@link #resolveByName(String, List)}, but {@code warnOnShadow} says whether a
   * shadowed library is worth a line on stderr.
   *
   * <p>It is not, when the caller is only asking a question. The automatic routing probes this
   * for every ordinary command, and a resolution that writes to stderr turns a daemon lookup the
   * user did not ask for into a warning on a command that has nothing to do with daemons -- and
   * a misleading one, since a probe that then declines to route leaves the local run to pick the
   * library by its own rules (libDirs first, {@code CliSetup.findLibrary}) and print its own
   * warning naming the opposite winner. Only a caller that commits to the choice -- {@code -d},
   * {@code --daemon-stop} and the other explicit control flags -- should say anything.
   */
  public static DaemonPaths resolveByName(String libName, List<Path> libDirs,
                                          boolean warnOnShadow) {
    // (1) cwd's arend.yaml wins when its parent dir name matches libName.
    DaemonPaths cwd = resolve(Paths.get("."));
    if (cwd != null && libName.equals(cwdLibName(cwd.libraryConfig))) {
      if (warnOnShadow) warnIfShadowed(libName, cwd.libraryConfig, libDirs);
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

  private static void warnIfShadowed(String libName, Path chosen, List<Path> libDirs) {
    for (Path libDir : libDirs) {
      Path other = libDir.resolve(libName).resolve(FileUtils.LIBRARY_CONFIG_FILE);
      if (!Files.isRegularFile(other)) continue;
      try {
        if (other.toRealPath().equals(chosen.toRealPath())) continue;
      } catch (IOException ignored) {
        // fall through to the warning — we can still surface the name conflict
      }
      System.err.println("[WARN] library '" + libName + "' has multiple copies: "
          + chosen + " and " + other.toAbsolutePath() + " — using " + chosen + ".");
    }
  }

  /**
   * Creates {@code <library>/.arend/} if it is not there, restricted to the owner.
   *
   * <p>The permissions are the access control. The directory holds the socket clients connect
   * to and the lock that says where it is, and anything that can reach the socket can run
   * arbitrary CLI commands against a warm library or shut the daemon down. On a shared machine
   * a umask-default {@code rwxr-xr-x} hands that to every local user.
   *
   * <p>An existing directory is left as it is: it may predate this, and silently tightening a
   * directory the user created is not this method's business. {@link SocketBinder} sets the
   * socket's own mode, so the socket is protected either way.
   */
  public void ensureArendDir() throws IOException {
    if (Files.isDirectory(arendDir)) return;
    try {
      Files.createDirectory(arendDir, PosixFilePermissions.asFileAttribute(
          PosixFilePermissions.fromString("rwx------")));
    } catch (UnsupportedOperationException e) {
      // Not a POSIX filesystem (Windows); the mode is not expressible and the platform's own
      // ACLs apply instead.
      Files.createDirectories(arendDir);
    } catch (FileAlreadyExistsException e) {
      if (!Files.isDirectory(arendDir)) throw e;
    }
  }

  private static String computeHash(String input) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      byte[] digest = sha.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("SHA-256 unavailable", e);
    }
  }
}
