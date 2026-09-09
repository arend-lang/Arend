package org.arend.frontend.cli.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.Properties;

/**
 * The record a running daemon leaves on disk so a client can find it: a properties file at
 * {@code <library>/.arend/daemon.lock}.
 *
 * <pre>
 *   pid              process id of the daemon JVM
 *   libraryPath      canonical path to the library's arend.yaml
 *   libraryHash      digest of that path; cross-check against {@link DaemonPaths#libraryHash}
 *   protocolVersion  wire-protocol version the daemon speaks
 *   startedAt        System.currentTimeMillis() at READY
 *   socketPath       the address clients connect to, as {@code uds:<path>} or {@code tcp:host:port}
 * </pre>
 *
 * @param pid             see above
 * @param libraryPath     see above
 * @param libraryHash     see above
 * @param protocolVersion see above
 * @param startedAt       see above
 * @param socketPath      see above
 */
public record LockFile(long pid, String libraryPath, String libraryHash,
                       int protocolVersion, long startedAt, String socketPath) {
  /**
   * The wire protocol this build speaks. Bump it for every change to the frames that an older
   * peer could not interpret; a client that finds a different value refuses to talk to that
   * daemon rather than exchanging frames neither side understands.
   */
  public static final int PROTOCOL_VERSION = 1;

  /**
   * What {@link #read} found. A lock file that is absent and one that cannot be read are
   * different situations -- the first means no daemon, the second means we do not know -- and
   * conflating them is how a second daemon gets started on top of a live one.
   */
  public sealed interface Read {
    /** No lock file at this path. */
    record Absent() implements Read {}
    /** A lock file that cannot be parsed, or could not be read. Its {@code reason} is for the user. */
    record Unreadable(String reason) implements Read {}
    /** A well-formed lock file. */
    record Found(LockFile lock) implements Read {}
  }

  /**
   * Writes the lock atomically: build a scratch file beside the destination, then rename over it,
   * so a reader never sees a half-written lock. The scratch name is unique per attempt, since two
   * processes racing to start a daemon for the same library would otherwise share it.
   */
  public void writeAtomic(Path lockPath) throws IOException {
    Properties props = new Properties();
    props.setProperty("pid", Long.toString(pid));
    props.setProperty("libraryPath", libraryPath);
    props.setProperty("libraryHash", libraryHash);
    props.setProperty("protocolVersion", Integer.toString(protocolVersion));
    props.setProperty("startedAt", Long.toString(startedAt));
    props.setProperty("socketPath", socketPath == null ? "" : socketPath);

    Path directory = lockPath.getParent();
    Path tmp = Files.createTempFile(directory, lockPath.getFileName() + ".", ".tmp");
    try {
      try (OutputStream out = Files.newOutputStream(tmp)) {
        props.store(out, "Arend daemon lock; do not edit by hand");
      }
      try {
        Files.move(tmp, lockPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        // Some network mounts cannot rename atomically, and refusing to start there is worse
        // than a window. FileBinarySource.commitOutput does the same.
        Files.move(tmp, lockPath, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
      }
    }
  }

  /** Parses the lock file at {@code lockPath}, distinguishing absent from unreadable. */
  public static Read read(Path lockPath) {
    if (!Files.exists(lockPath)) return new Read.Absent();
    Properties props = new Properties();
    try (InputStream in = Files.newInputStream(lockPath)) {
      props.load(in);
    } catch (IOException e) {
      return new Read.Unreadable(e.toString());
    }
    try {
      long pid = Long.parseLong(props.getProperty("pid", "").trim());
      String libraryPath = props.getProperty("libraryPath", "");
      String libraryHash = props.getProperty("libraryHash", "");
      int protocolVersion = Integer.parseInt(props.getProperty("protocolVersion", "0").trim());
      long startedAt = Long.parseLong(props.getProperty("startedAt", "0").trim());
      String socketPath = props.getProperty("socketPath", "");
      if (libraryPath.isEmpty() || libraryHash.isEmpty()) {
        return new Read.Unreadable("lock file is missing libraryPath or libraryHash");
      }
      return new Read.Found(new LockFile(pid, libraryPath, libraryHash, protocolVersion, startedAt, socketPath));
    } catch (NumberFormatException e) {
      return new Read.Unreadable("lock file has a malformed numeric field: " + e.getMessage());
    }
  }

  /** The lock at {@code lockPath} if it is well-formed, else empty. For callers with no use for the reason. */
  public static Optional<LockFile> readIfValid(Path lockPath) {
    return read(lockPath) instanceof Read.Found found ? Optional.of(found.lock()) : Optional.empty();
  }

  /** Best-effort delete; does not throw if the file is already gone. */
  public static void deleteQuietly(Path lockPath) {
    try {
      Files.deleteIfExists(lockPath);
    } catch (IOException ignored) {
    }
  }
}
