package org.arend.frontend.cli.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/**
 * Properties-file-on-disk lock recording a running daemon.
 *
 * <p>Atomicity: writes go to a sibling temp file and rename-into-place. Reads accept the
 * file may not exist; only fail loudly if it's malformed (which is treated as "stale" by
 * the caller).
 *
 * <p>Schema:
 * <pre>
 *   pid              = process id of the daemon JVM
 *   libraryPath      = canonical path to arend.yaml
 *   libraryHash      = SHA-256-derived hash; sanity check against {@link DaemonPaths#libraryHash}
 *   protocolVersion  = wire-protocol version (0 in M2 — no socket yet)
 *   startedAt        = System.currentTimeMillis() at READY
 *   socketPath       = absolute path of UDS socket; empty string until M3 binds one
 * </pre>
 */
public final class LockFile {
  public static final int PROTOCOL_VERSION_M2 = 0;
  /** Bump for every protocol-breaking change to the wire frames. */
  public static final int PROTOCOL_VERSION_M3 = 1;

  public final long pid;
  public final String libraryPath;
  public final String libraryHash;
  public final int protocolVersion;
  public final long startedAt;
  public final String socketPath;

  public LockFile(long pid, String libraryPath, String libraryHash,
                  int protocolVersion, long startedAt, String socketPath) {
    this.pid = pid;
    this.libraryPath = libraryPath;
    this.libraryHash = libraryHash;
    this.protocolVersion = protocolVersion;
    this.startedAt = startedAt;
    this.socketPath = socketPath;
  }

  /** Write atomically: serialise into a sibling tmp, then rename over the lock path. */
  public void writeAtomic(Path lockPath) throws IOException {
    Properties props = new Properties();
    props.setProperty("pid", Long.toString(pid));
    props.setProperty("libraryPath", libraryPath);
    props.setProperty("libraryHash", libraryHash);
    props.setProperty("protocolVersion", Integer.toString(protocolVersion));
    props.setProperty("startedAt", Long.toString(startedAt));
    props.setProperty("socketPath", socketPath == null ? "" : socketPath);

    Path tmp = lockPath.resolveSibling(lockPath.getFileName().toString() + ".tmp");
    try (OutputStream out = Files.newOutputStream(tmp)) {
      props.store(out, "Arend daemon lock; do not edit by hand");
    }
    Files.move(tmp, lockPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
  }

  /**
   * Parse a lock file. Returns empty if the file does not exist or is unreadable; throws
   * {@link IOException} only on genuine I/O failures. Malformed-but-present files return
   * empty so callers treat them as stale and clean up.
   */
  public static Optional<LockFile> read(Path lockPath) {
    if (!Files.isRegularFile(lockPath)) return Optional.empty();
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(lockPath)) {
      props.load(in);
    } catch (IOException e) {
      return Optional.empty();
    }
    try {
      long pid = Long.parseLong(props.getProperty("pid", "").trim());
      String libraryPath = props.getProperty("libraryPath", "");
      String libraryHash = props.getProperty("libraryHash", "");
      int protocolVersion = Integer.parseInt(props.getProperty("protocolVersion", "0").trim());
      long startedAt = Long.parseLong(props.getProperty("startedAt", "0").trim());
      String socketPath = props.getProperty("socketPath", "");
      if (libraryPath.isEmpty() || libraryHash.isEmpty()) return Optional.empty();
      return Optional.of(new LockFile(pid, libraryPath, libraryHash, protocolVersion, startedAt, socketPath));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /** Best-effort delete; does not throw if the file is already gone. */
  public static void deleteQuietly(Path lockPath) {
    try {
      Files.deleteIfExists(lockPath);
    } catch (IOException ignored) {
    }
  }
}
