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
 * <p>A daemon is keyed by the canonical absolute path of its {@code arend.yaml}, and every
 * per-daemon file lives under {@code <library>/.arend/}: {@code daemon.lock} (where the daemon
 * is; absent means none), {@code daemon.log} (its captured output) and {@code daemon.sock} (the
 * socket clients connect to).
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
   * {@code daemon.starting} — created by the parent before it spawns a child, removed once
   * the child is ready or has failed. Two {@code arend -d} calls racing would otherwise both
   * see no lock and both spawn, and only one of them can bind the socket. It names the processes
   * of the start ({@link StartMarker}).
   */
  public final Path startingMarker;
  /**
   * How long a start may take to become ready, and so how long a {@link #startingMarker} that names
   * no process stands for a start in progress. An older one is debris from a start that was killed.
   */
  public static final long START_TIMEOUT_MILLIS = 600_000;
  /** Hex-encoded SHA-256 of {@link #libraryConfig}'s string form, first 16 chars. */
  public final String libraryHash;

  private DaemonPaths(Path libraryConfig, String libraryHash) {
    this.libraryConfig = libraryConfig;
    this.libraryHash = libraryHash;
    Path libRoot = libraryConfig.getParent();
    if (libRoot == null) {
      throw new IllegalArgumentException("library config has no parent directory: " + libraryConfig);
    }
    this.arendDir = libRoot.resolve(".arend");
    this.lockFile = arendDir.resolve("daemon.lock");
    this.logFile = arendDir.resolve("daemon.log");
    this.socketFile = arendDir.resolve("daemon.sock");
    this.startingMarker = arendDir.resolve("daemon.starting");
  }

  /**
   * Two resolutions of one library are equal: callers key maps and sets on them, and compare a
   * library they hold with the ones they find on the disk again.
   */
  @Override
  public boolean equals(Object o) {
    return o instanceof DaemonPaths other && libraryConfig.equals(other.libraryConfig);
  }

  @Override
  public int hashCode() {
    return libraryConfig.hashCode();
  }

  /**
   * Build a {@code DaemonPaths} from a user-supplied library reference: a directory containing
   * {@code arend.yaml}, the {@code arend.yaml} itself, or any path that resolves to one of those
   * after canonicalisation. Returns null if it resolves to neither (caller reports it).
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

  public static final String CONFIG_DIR_PROPERTY = "arend.daemon.configDir";

  /** {@code ~/.arend}, unless {@value #CONFIG_DIR_PROPERTY} says otherwise, as a test does. */
  public static Path configDir() {
    String override = System.getProperty(CONFIG_DIR_PROPERTY);
    return override != null ? Paths.get(override) : FileUtils.configDir();
  }

  /**
   * Resolve a library reference by name, preferring the current directory.
   *
   * <p>Search order: the cwd's own {@code arend.yaml} when its containing directory is named
   * {@code libName}; then {@code libName} as a path; then {@code <libDir>/<libName>/arend.yaml}
   * for each {@code libDir} in order. The cwd preference is what stops
   * {@code arend --daemon-stop arend-lib}, run from inside {@code arend-lib/}, from targeting a
   * like-named system copy under {@code ~/.arend/libs/}.
   */
  public static DaemonPaths resolveByName(String libName, List<Path> libDirs) {
    return resolveByName(libName, libDirs, true);
  }

  /**
   * As {@link #resolveByName(String, List)}, but {@code warnOnShadow} says whether a shadowed
   * like-named library earns a line on stderr. Only a caller that commits to the choice should
   * say anything: the automatic routing probes this for every ordinary command, and a probe that
   * then declines leaves the local run to pick the library by its own rules and warn about the
   * opposite winner.
   */
  public static DaemonPaths resolveByName(String libName, List<Path> libDirs,
                                          boolean warnOnShadow) {
    DaemonPaths cwd = resolve(Paths.get("."));
    if (cwd != null && libName.equals(cwdLibName(cwd.libraryConfig))) {
      if (warnOnShadow) warnIfShadowed(libName, cwd.libraryConfig, libDirs);
      return cwd;
    }
    DaemonPaths direct = resolve(Paths.get(libName));
    if (direct != null) return direct;
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
   * <p>The permissions are the access control: the directory holds the socket clients connect to
   * and the lock saying where it is, and anything that can reach the socket can run arbitrary CLI
   * commands against a warm library or shut the daemon down. An existing directory is left as it
   * is -- tightening one the user made is not this method's call -- and {@code SocketBinder} sets
   * the socket's own mode, so the socket is covered either way.
   */
  /** The name of the library: its directory's. */
  public String libraryName() {
    Path directory = libraryConfig.getParent();
    return directory.getFileName() == null ? "" : directory.getFileName().toString();
  }

  public void ensureArendDir() throws IOException {
    if (Files.isDirectory(arendDir)) return;
    try {
      Files.createDirectory(arendDir, PosixFilePermissions.asFileAttribute(
          PosixFilePermissions.fromString("rwx------")));
    } catch (UnsupportedOperationException e) {
      // Not a POSIX filesystem (Windows): an ACL granting only the owner instead.
      Files.createDirectories(arendDir);
      PrivateFiles.restrictToOwner(arendDir, true);
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
