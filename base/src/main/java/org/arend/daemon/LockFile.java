package org.arend.daemon;

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
 * {@code <library>/.arend/daemon.lock} in every library the daemon owns, so that a command
 * issued from any of them finds it and a second daemon asked to serve one sees it is taken; and
 * another copy in {@link DaemonPaths#registryDir}, which is how the MCP server finds every daemon
 * and a command about a shared library (one under {@link DaemonPaths#sharedLibrariesRoot}, which
 * no daemon owns) finds the daemons that loaded it.
 *
 * <pre>
 *   pid              process id of the daemon JVM
 *   libraryPath      canonical path to the arend.yaml of the daemon's first library, whose
 *                    {@code .arend/} holds its socket and log
 *   libraryHash      digest of that path; the daemon's identity
 *   library.N        canonical arend.yaml of every library served, N = 1, 2, ... in load order
 *   shared.N         canonical arend.yaml or zip of every shared library loaded, listed or a dependency
 *   protocolVersion  wire-protocol version the daemon speaks
 *   startedAt        System.currentTimeMillis() at READY
 *   socketPath       the address clients connect to, as {@code uds:<path>} or {@code tcp:host:port}
 *   host             what runs the daemon: {@code cli}, a JVM of its own, or {@code intellij}, an IDE
 *                    that answers as a daemon from its own server; absent means {@code cli}
 * </pre>
 *
 * @param pid             see above
 * @param libraryPath     see above
 * @param libraryHash     see above
 * @param libraries       see {@code library.N} above
 * @param shared          see {@code shared.N} above
 * @param protocolVersion see above
 * @param startedAt       see above
 * @param socketPath      see above
 * @param host            see above
 */
public record LockFile(long pid, String libraryPath, String libraryHash, List<String> libraries,
                       List<String> shared, int protocolVersion, long startedAt, String socketPath, String host) {
  /** A daemon that is a JVM of its own, started by {@code arend -d}. */
  public static final String HOST_CLI = "cli";
  /** A daemon answered by the IntelliJ plugin from the IDE's own server, in the IDE's process. */
  public static final String HOST_INTELLIJ = "intellij";

  public LockFile {
    libraries = List.copyOf(libraries);
    shared = List.copyOf(shared);
    if (host == null || host.isEmpty()) host = HOST_CLI;
  }

  /** A lock for a daemon of its own JVM. */
  public LockFile(long pid, String libraryPath, String libraryHash, List<String> libraries,
                  List<String> shared, int protocolVersion, long startedAt, String socketPath) {
    this(pid, libraryPath, libraryHash, libraries, shared, protocolVersion, startedAt, socketPath, HOST_CLI);
  }

  /** A lock for a daemon that serves the one library {@code libraryPath}. */
  public LockFile(long pid, String libraryPath, String libraryHash,
                  int protocolVersion, long startedAt, String socketPath) {
    this(pid, libraryPath, libraryHash, List.of(libraryPath), List.of(), protocolVersion, startedAt, socketPath);
  }

  /**
   * Whether the daemon's process is something else besides: an IDE. Its PID is the IDE's, so the
   * daemon is stopped through its socket or not at all -- never by signalling that PID.
   */
  public boolean isHostedByIde() {
    return !HOST_CLI.equals(host);
  }

  /** Whether the daemon serves the library whose canonical arend.yaml is {@code libraryConfig}. */
  public boolean serves(Path libraryConfig) {
    return libraries.contains(libraryConfig.toString());
  }

  /** Whether the daemon has the library read from {@code libraryFile} loaded: listed, or a shared dependency. */
  public boolean hasLoaded(Path libraryFile) {
    return serves(libraryFile) || shared.contains(libraryFile.toString());
  }

  /** Where each owned library's copy of this lock lives, in load order: a shared one or a zip gets none. */
  public List<Path> lockFiles() {
    return libraries.stream().filter(library -> !shared.contains(library) && !library.endsWith(".zip"))
        .map(library -> DaemonPaths.lockFileIn(Paths.get(library).getParent())).toList();
  }

  /**
   * Removes every copy of this lock that still names this daemon. A copy naming another one is
   * left alone: the library has moved on to a daemon of its own, and its lock is the only way to
   * reach that one.
   */
  public void deleteAllCopies() {
    List<Path> copies = new ArrayList<>(lockFiles());
    copies.add(registryFile());
    copies.forEach(this::deleteIfOurs);
  }

  /** Removes the lock at {@code lockPath} if it still names this daemon, as {@link #deleteAllCopies} does. */
  public void deleteIfOurs(Path lockPath) {
    if (readIfValid(lockPath).filter(this::sameDaemon).isPresent()) deleteQuietly(lockPath);
  }

  /** Whether {@code other} is a copy of this daemon's lock: the same process, the same identity. */
  public boolean sameDaemon(LockFile other) {
    return other.pid == pid && other.libraryHash.equals(libraryHash);
  }

  /**
   * Whether the daemon's process is alive, judged by its PID alone. A socket probe would also catch
   * a recycled PID, but it can fail for reasons that have nothing to do with the daemon being gone,
   * and every caller that concludes "dead" goes on to remove the lock. A false "still running"
   * costs the user one {@code --daemon-stop}; a false "dead" costs them a process they can no
   * longer reach.
   */
  public boolean processAlive() {
    return ProcessHandle.of(pid).isPresent();
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
    for (int i = 0; i < shared.size(); i++) props.setProperty("shared." + (i + 1), shared.get(i));
    props.setProperty("protocolVersion", Integer.toString(protocolVersion));
    props.setProperty("startedAt", Long.toString(startedAt));
    props.setProperty("socketPath", socketPath == null ? "" : socketPath);
    props.setProperty("host", host);

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
      String host = props.getProperty("host", HOST_CLI);
      List<String> libraries = new ArrayList<>();
      for (int i = 1; props.getProperty("library." + i) != null; i++) libraries.add(props.getProperty("library." + i));
      List<String> shared = new ArrayList<>();
      for (int i = 1; props.getProperty("shared." + i) != null; i++) shared.add(props.getProperty("shared." + i));
      if (libraryPath.isEmpty() || libraryHash.isEmpty()) {
        return new Read.Unreadable("lock file is missing libraryPath or libraryHash");
      }
      return new Read.Found(new LockFile(pid, libraryPath, libraryHash, libraries, shared, protocolVersion, startedAt, socketPath, host));
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
