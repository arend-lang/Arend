package org.arend.daemon;

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
  /** Decided on the first ask, which canonicalises paths: routing asks it of every library of every daemon. */
  private Boolean canOwn;

  private DaemonPaths(Path libraryConfig, String libraryHash) {
    this.libraryConfig = libraryConfig;
    this.libraryHash = libraryHash;
    Path libRoot = libraryConfig.getParent();
    if (libRoot == null) {
      throw new IllegalArgumentException("library config has no parent directory: " + libraryConfig);
    }
    this.arendDir = libRoot.resolve(".arend");
    this.lockFile = lockFileIn(libRoot);
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

  /** Whether {@code arend -d} is starting a daemon for this library right now: its marker is there, and a process it names runs. */
  public boolean startInProgress() {
    return StartMarker.inProgress(startingMarker);
  }

  /**
   * The project a daemon owning {@code owned} holds, for a command issued from it to find the daemon:
   * the deepest directory holding every one of them. None when that is the file system's root or the
   * user's home -- a directory nobody means by "the project" -- or when nothing is owned.
   */
  public static Path projectDirOf(List<DaemonPaths> owned) {
    Path common = null;
    for (DaemonPaths paths : owned) {
      Path root = paths.libraryConfig.getParent();
      if (common == null) {
        common = root;
        continue;
      }
      while (common != null && !root.startsWith(common)) common = common.getParent();
    }
    if (common == null || common.getParent() == null) return null;
    try {
      if (common.equals(Paths.get(System.getProperty("user.home")).toRealPath())) return null;
    } catch (IOException ignored) {
    }
    return common;
  }

  public static Path lockFileIn(Path libRoot) {
    return libRoot.resolve(".arend").resolve("daemon.lock");
  }

  public static final String CONFIG_DIR_PROPERTY = "arend.daemon.configDir";

  /** {@code ~/.arend}, unless {@value #CONFIG_DIR_PROPERTY} says otherwise, as a test does. */
  public static Path configDir() {
    String override = System.getProperty(CONFIG_DIR_PROPERTY);
    return override != null ? Paths.get(override) : FileUtils.configDir();
  }

  public static Path sharedLibrariesRoot() {
    return configDir().resolve("libs");
  }

  public static Path registryDir() {
    return configDir().resolve("daemons");
  }

  public static boolean isShared(Path libraryFile) {
    try {
      Path root = sharedLibrariesRoot();
      if (!Files.isDirectory(root)) return false;
      return libraryFile.toRealPath().startsWith(root.toRealPath());
    } catch (IOException e) {
      return false;
    }
  }

  public boolean isShared() {
    return isShared(libraryConfig);
  }

  public String libraryName() {
    return nameOf(libraryConfig);
  }

  /** The name of the library {@code libraryFile} -- its arend.yaml or its zip -- is: the directory's, or the zip's. */
  public static String nameOf(Path libraryFile) {
    String file = libraryFile.getFileName().toString();
    if (file.endsWith(FileUtils.ZIP_EXTENSION)) return file.substring(0, file.length() - FileUtils.ZIP_EXTENSION.length());
    Path directory = libraryFile.getParent();
    return directory == null || directory.getFileName() == null ? "" : directory.getFileName().toString();
  }

  /** Whether a daemon may own the library: not a zip, and not shared from ~/.arend/libs. */
  public boolean canOwn() {
    if (canOwn == null) canOwn = !libraryConfig.getFileName().toString().endsWith(FileUtils.ZIP_EXTENSION) && !isShared(libraryConfig);
    return canOwn;
  }

  /** {@code path} as a client in {@code cwd} meant it: against {@code cwd} when one is given and the path is relative. */
  public static Path against(String cwd, String path) {
    return cwd == null ? Paths.get(path) : Paths.get(cwd).resolve(path);
  }

  /** As {@link #resolve(Path)}, for a reference a client in {@code cwd} gave; null for this process's own directory. */
  public static DaemonPaths resolve(String cwd, String reference) {
    return resolve(against(cwd, reference));
  }

  /**
   * Build a {@code DaemonPaths} from a user-supplied library reference: a directory containing
   * {@code arend.yaml}, the {@code arend.yaml} itself, a zip library, or any path that resolves to
   * one of those after canonicalisation. Returns null if it resolves to none (caller reports it).
   */
  public static DaemonPaths resolve(Path libraryRef) {
    try {
      Path probe = libraryRef.toAbsolutePath();
      Path config;
      if (Files.isDirectory(probe)) {
        config = probe.resolve(FileUtils.LIBRARY_CONFIG_FILE);
      } else if (probe.getFileName() != null
          && (probe.getFileName().toString().equals(FileUtils.LIBRARY_CONFIG_FILE)
              || probe.getFileName().toString().endsWith(FileUtils.ZIP_EXTENSION))) {
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
    if (cwd != null && libName.equals(cwd.libraryName())) {
      if (warnOnShadow) warnIfShadowed(libName, cwd.libraryConfig, libDirs);
      return cwd;
    }
    DaemonPaths direct = resolve(Paths.get(libName));
    if (direct != null) return direct;
    for (Path libDir : libDirs) {
      DaemonPaths d = resolve(libDir.resolve(libName));
      if (d == null) d = resolve(libDir.resolve(libName + FileUtils.ZIP_EXTENSION));
      if (d != null) return d;
    }
    return null;
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
