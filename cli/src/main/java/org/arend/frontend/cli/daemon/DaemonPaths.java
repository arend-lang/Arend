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
 *   <li>{@code daemon.log}  — append-only stdout+stderr of the child.</li>
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

  /** Like {@link #resolve(Path)} but also tries {@code <libDir>/<libName>/arend.yaml}. */
  public static DaemonPaths resolveByName(String libName, java.util.List<Path> libDirs) {
    Path direct = Paths.get(libName);
    DaemonPaths d = resolve(direct);
    if (d != null) return d;
    for (Path libDir : libDirs) {
      d = resolve(libDir.resolve(libName));
      if (d != null) return d;
    }
    return null;
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
