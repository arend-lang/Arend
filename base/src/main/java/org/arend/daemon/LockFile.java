package org.arend.daemon;

import org.arend.util.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
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
 *   processStart     when the process {@code pid} names started, in epoch milliseconds, as the
 *                    process itself reads it; absent when the platform does not say
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
 * @param processStart    see above; {@link #UNKNOWN_START} when absent
 */
public record LockFile(long pid, String libraryPath, String libraryHash, List<String> libraries,
                       List<String> shared, int protocolVersion, long startedAt, String socketPath, String host,
                       long processStart) {
  /** A daemon that is a JVM of its own, started by {@code arend -d}. */
  public static final String HOST_CLI = "cli";
  /** A daemon answered by the IntelliJ plugin from the IDE's own server, in the IDE's process. */
  public static final String HOST_INTELLIJ = "intellij";

  /** The {@code processStart} of a lock that does not say when its process started. */
  public static final long UNKNOWN_START = -1;

  /**
   * How far apart two readings of one process's start may be. They are the same number read twice,
   * but a platform may derive it from a clock with coarser steps than a millisecond.
   */
  private static final long START_TOLERANCE_MILLIS = 1000;

  public LockFile {
    libraries = List.copyOf(libraries);
    shared = List.copyOf(shared);
    if (host == null || host.isEmpty()) host = HOST_CLI;
  }

  /** A lock that does not say when its process started, as one written before {@code processStart} did. */
  public LockFile(long pid, String libraryPath, String libraryHash, List<String> libraries, List<String> shared,
                  int protocolVersion, long startedAt, String socketPath, String host) {
    this(pid, libraryPath, libraryHash, libraries, shared, protocolVersion, startedAt, socketPath, host, UNKNOWN_START);
  }

  /** The lock of a daemon this process runs, whose first library is {@code home}. */
  public static LockFile ofThisProcess(DaemonPaths home, List<String> libraries, List<String> shared, long startedAt,
                                       String socketPath, String host) {
    ProcessHandle self = ProcessHandle.current();
    return new LockFile(self.pid(), home.libraryConfig.toString(), home.libraryHash, libraries,
        shared, PROTOCOL_VERSION, startedAt, socketPath, host, startOf(self));
  }

  /** When {@code process} started, in epoch milliseconds, or {@link #UNKNOWN_START}. */
  static long startOf(ProcessHandle process) {
    return process.info().startInstant().map(Instant::toEpochMilli).orElse(UNKNOWN_START);
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
    return libraries.stream().filter(library -> !shared.contains(library) && !library.endsWith(FileUtils.ZIP_EXTENSION))
        .map(library -> DaemonPaths.lockFileIn(Paths.get(library).getParent())).toList();
  }

  /**
   * Writes every copy of this lock: the registry's first, the first library's last, so that a client
   * finding that one can count on all the others. The libraries' {@code .arend/} must exist.
   */
  public void writeEverywhere() throws IOException {
    Files.createDirectories(DaemonPaths.registryDir());
    writeAtomic(registryFile());
    for (Path copy : lockFiles().reversed()) writeAtomic(copy);
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

  /** Removes every trace of the daemon: the copies of its lock that still name it, and its socket file. */
  public void removeTraces() {
    deleteAllCopies();
    if (socketPath != null && socketPath.startsWith("uds:")) deleteQuietly(Paths.get(socketPath.substring("uds:".length())));
  }

  /** Removes the lock at {@code lockPath} if it still names this daemon, as {@link #deleteAllCopies} does. */
  public void deleteIfOurs(Path lockPath) {
    // A copy of this daemon's lock is one of the same process with the same identity.
    if (readIfValid(lockPath).filter(other -> other.pid == pid && other.libraryHash.equals(libraryHash)).isPresent()) {
      deleteQuietly(lockPath);
    }
  }

  /**
   * The daemon's process, while it runs: the process its PID names, provided that one started when
   * the daemon's did. A PID is reused once its process is gone -- after a crash or a reboot the lock
   * outlives it -- and the one it names then is somebody else's, which {@code --daemon-stop} would
   * otherwise signal, process tree and all. A lock that does not say when its process started is
   * judged by its PID alone.
   *
   * <p>No socket probe: one can fail for reasons that have nothing to do with the daemon being gone,
   * and every caller that concludes "dead" goes on to remove the lock.
   */
  public Optional<ProcessHandle> process() {
    return ProcessHandle.of(pid).filter(this::startedAsRecorded);
  }

  private boolean startedAsRecorded(ProcessHandle process) {
    if (processStart == UNKNOWN_START) return true;
    long start = startOf(process);
    // A process that will not say when it started is not one this lock can vouch for: the daemon
    // that wrote it could say it of itself.
    return start != UNKNOWN_START && Math.abs(start - processStart) <= START_TOLERANCE_MILLIS;
  }

  /** Whether the daemon's process is alive, as {@link #process} judges it. */
  public boolean processAlive() {
    return process().isPresent();
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
    if (processStart != UNKNOWN_START) props.setProperty("processStart", Long.toString(processStart));

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
      long processStart = Long.parseLong(props.getProperty("processStart", Long.toString(UNKNOWN_START)).trim());
      List<String> libraries = new ArrayList<>();
      for (int i = 1; props.getProperty("library." + i) != null; i++) libraries.add(props.getProperty("library." + i));
      // A lock from before library.N named only the one library its daemon served.
      if (libraries.isEmpty() && !libraryPath.isEmpty()) libraries.add(libraryPath);
      List<String> shared = new ArrayList<>();
      for (int i = 1; props.getProperty("shared." + i) != null; i++) shared.add(props.getProperty("shared." + i));
      if (libraryPath.isEmpty() || libraryHash.isEmpty()) {
        return new Read.Unreadable("lock file is missing libraryPath or libraryHash");
      }
      return new Read.Found(new LockFile(pid, libraryPath, libraryHash, libraries, shared, protocolVersion, startedAt,
          socketPath, host, processStart));
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
