package org.arend.frontend.cli.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * The record a running daemon leaves on disk so a client can find it: a properties file at
 * {@code <library>/.arend/daemon.lock} in every library the daemon serves, so that a command
 * issued from any of them finds it and a second daemon asked to serve one sees it is taken; and
 * another copy in {@link DaemonPaths#registryDir}, which is how the MCP server finds every daemon.
 *
 * <pre>
 *   pid              process id of the daemon JVM
 *   libraryPath      canonical path to the arend.yaml of the daemon's first library, whose
 *                    {@code .arend/} holds its socket and log
 *   libraryHash      digest of that path; the daemon's identity
 *   library.N        canonical arend.yaml of every library served, N = 1, 2, ... in load order
 *   protocolVersion  wire-protocol version the daemon speaks
 *   startedAt        System.currentTimeMillis() at READY
 *   socketPath       the address clients connect to, as {@code uds:<path>} or {@code tcp:host:port}
 * </pre>
 *
 * @param pid             see above
 * @param libraryPath     see above
 * @param libraryHash     see above
 * @param libraries       see {@code library.N} above
 * @param protocolVersion see above
 * @param startedAt       see above
 * @param socketPath      see above
 */
public record LockFile(long pid, String libraryPath, String libraryHash, List<String> libraries,
                       int protocolVersion, long startedAt, String socketPath) {
  public LockFile {
    libraries = List.copyOf(libraries);
  }

  /** A lock for a daemon that serves the one library {@code libraryPath}. */
  public LockFile(long pid, String libraryPath, String libraryHash,
                  int protocolVersion, long startedAt, String socketPath) {
    this(pid, libraryPath, libraryHash, List.of(libraryPath), protocolVersion, startedAt, socketPath);
  }

  /** Whether the daemon serves the library whose canonical arend.yaml is {@code libraryConfig}. */
  public boolean serves(Path libraryConfig) {
    return libraries.contains(libraryConfig.toString());
  }

  /** Where each library's copy of this lock lives, in load order. */
  public List<Path> lockFiles() {
    return libraries.stream().map(library -> DaemonPaths.lockFileIn(Paths.get(library).getParent())).toList();
  }

  /**
   * Removes every copy of this lock that still names this daemon. A copy naming another one is
   * left alone: the library has moved on to a daemon of its own, and its lock is the only way to
   * reach that one.
   */
  public void deleteAllCopies() {
    List<Path> copies = new ArrayList<>(lockFiles());
    copies.add(registryFile());
    for (Path lockFile : copies) {
      if (readIfValid(lockFile).filter(other -> other.pid() == pid && other.libraryHash().equals(libraryHash)).isPresent()) {
        deleteQuietly(lockFile);
      }
    }
  }

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
    for (int i = 0; i < libraries.size(); i++) props.setProperty("library." + (i + 1), libraries.get(i));
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
      List<String> libraries = new ArrayList<>();
      for (int i = 1; props.getProperty("library." + i) != null; i++) libraries.add(props.getProperty("library." + i));
      if (libraryPath.isEmpty() || libraryHash.isEmpty()) {
        return new Read.Unreadable("lock file is missing libraryPath or libraryHash");
      }
      return new Read.Found(new LockFile(pid, libraryPath, libraryHash, libraries, protocolVersion, startedAt, socketPath));
    } catch (NumberFormatException e) {
      return new Read.Unreadable("lock file has a malformed numeric field: " + e.getMessage());
    }
  }

  /** The lock at {@code lockPath} if it is well-formed, else empty. For callers with no use for the reason. */
  public static Optional<LockFile> readIfValid(Path lockPath) {
    return read(lockPath) instanceof Read.Found found ? Optional.of(found.lock()) : Optional.empty();
  }

  /** This daemon's entry in {@link DaemonPaths#registryDir}. */
  public Path registryFile() {
    return DaemonPaths.registryDir().resolve(libraryHash + ".lock");
  }

  /** Every well-formed entry in {@link DaemonPaths#registryDir}, live or not, oldest daemon first. */
  public static List<LockFile> registered() {
    List<LockFile> out = new ArrayList<>();
    try (var entries = Files.newDirectoryStream(DaemonPaths.registryDir(), "*.lock")) {
      for (Path entry : entries) readIfValid(entry).ifPresent(out::add);
    } catch (IOException e) {
      // No registry yet: no daemon has ever registered.
    }
    out.sort(java.util.Comparator.comparingLong(LockFile::startedAt).thenComparingLong(LockFile::pid));
    return out;
  }

  /** Best-effort delete; does not throw if the file is already gone. */
  public static void deleteQuietly(Path lockPath) {
    try {
      Files.deleteIfExists(lockPath);
    } catch (IOException ignored) {
    }
  }
}
