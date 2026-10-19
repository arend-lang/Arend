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
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * The record a running daemon leaves on disk so a client can find it: a properties file at
 * {@code <library>/.arend/daemon.lock} in every library the daemon owns, so that a command
 * issued from any of them finds it and a second daemon asked to serve one sees it is taken; and
 * another copy in {@link DaemonPaths#registryDir}, which is how the MCP server finds every daemon
 * and a command about a library no daemon owns -- a shared one under
 * {@link DaemonPaths#sharedLibrariesRoot}, a zip, or a dependency a daemon was not given -- finds
 * the daemons that loaded it.
 *
 * <p>Only the registry's copy is trusted. A library's directory may be anybody's -- a clone, a
 * shared checkout -- and a lock in it may have been put there by anybody, naming any address and any
 * process; so a library's copy only points at the registry entry it names ({@link #lookup}), which
 * lives in a directory of the user's own and is the only copy that carries the daemon's
 * {@code token}, the secret every request has to present.
 *
 * <pre>
 *   pid              process id of the daemon JVM
 *   libraryPath      canonical path to the arend.yaml of the daemon's first library, whose
 *                    {@code .arend/} holds its socket and log
 *   libraryHash      digest of that path; the daemon's identity
 *   library.N        canonical arend.yaml of every library served, N = 1, 2, ... in load order
 *   loaded.N         canonical arend.yaml or zip of every library loaded that the daemon does not own,
 *                    listed or a dependency: a shared one, a zip, or a dependency it was not given
 *   protocolVersion  wire-protocol version the daemon speaks
 *   startedAt        System.currentTimeMillis() at READY
 *   socketPath       the address clients connect to, as {@code uds:<path>} or {@code tcp:host:port}
 *   host             what runs the daemon: {@code cli}, a JVM of its own, or {@code intellij}, an IDE
 *                    that answers as a daemon from its own server; absent means {@code cli}
 *   processStart     when the process {@code pid} names started, in epoch milliseconds, as the
 *                    process itself reads it; absent when the platform does not say
 *   token            the secret a client presents with every request; in the registry's copy only
 *   build.*          the jar the daemon runs from, as it was when it started ({@link BuildIdentity}):
 *                    build.path, build.size, build.modified, build.key; absent when it is not one jar
 *   projectDir       the directory of the project the daemon holds: an IntelliJ project's, or the
 *                    deepest one holding every library {@code arend -d} was given; a command issued
 *                    from it, naming no library, goes to this daemon. Absent when there is none
 * </pre>
 *
 * @param pid             see above
 * @param libraryPath     see above
 * @param libraryHash     see above
 * @param libraries       see {@code library.N} above
 * @param loaded          see {@code loaded.N} above
 * @param protocolVersion see above
 * @param startedAt       see above
 * @param socketPath      see above
 * @param host            see above
 * @param processStart    see above; {@link #UNKNOWN_START} when absent
 * @param token           see above; empty in a library's copy
 * @param build           see above; null when absent
 * @param projectDir      see above; empty when absent
 */
public record LockFile(long pid, String libraryPath, String libraryHash, List<String> libraries,
                       List<String> loaded, int protocolVersion, long startedAt, String socketPath, String host,
                       long processStart, String token, BuildIdentity build, String projectDir) {
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

  private static final Pattern HASH = Pattern.compile("[0-9a-f]{16}");
  private static final SecureRandom RANDOM = new SecureRandom();

  public LockFile {
    libraries = List.copyOf(libraries);
    loaded = List.copyOf(loaded);
    if (host == null || host.isEmpty()) host = HOST_CLI;
    if (token == null) token = "";
    if (projectDir == null) projectDir = "";
  }

  /** A lock that does not say when its process started, as one written before {@code processStart} did, with no token. */
  public LockFile(long pid, String libraryPath, String libraryHash, List<String> libraries, List<String> loaded,
                  int protocolVersion, long startedAt, String socketPath, String host) {
    this(pid, libraryPath, libraryHash, libraries, loaded, protocolVersion, startedAt, socketPath, host, UNKNOWN_START, "", null, "");
  }

  /** The lock of a daemon this process runs, whose first library is {@code home} and whose requests present {@code token}. */
  public static LockFile ofThisProcess(DaemonPaths home, List<String> libraries, List<String> loaded, long startedAt,
                                       String socketPath, String host, String token) {
    ProcessHandle self = ProcessHandle.current();
    return new LockFile(self.pid(), home.libraryConfig.toString(), home.libraryHash, libraries,
        loaded, PROTOCOL_VERSION, startedAt, socketPath, host, startOf(self), token,
        HOST_CLI.equals(host) ? BuildIdentity.ofThisProcess() : null, "");
  }

  /** This lock, saying that the daemon holds the project in {@code projectDir} (null for none). */
  public LockFile withProjectDir(Path projectDir) {
    return new LockFile(pid, libraryPath, libraryHash, libraries, loaded, protocolVersion, startedAt, socketPath, host,
        processStart, token, build, projectDir == null ? "" : projectDir.toString());
  }

  /** A fresh secret for a daemon to demand of its clients: 256 random bits, in hex. */
  public static String newToken() {
    byte[] bytes = new byte[32];
    RANDOM.nextBytes(bytes);
    return HexFormat.of().formatHex(bytes);
  }

  /** This lock as a library's copy has it: without the token, which only the registry's copy holds. */
  public LockFile withoutToken() {
    return new LockFile(pid, libraryPath, libraryHash, libraries, loaded, protocolVersion, startedAt, socketPath, host, processStart, "", build, projectDir);
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

  /**
   * Whether the daemon has the library read from {@code libraryFile} loaded: listed, or a dependency
   * of one -- whether it owns it or not.
   */
  public boolean hasLoaded(Path libraryFile) {
    return serves(libraryFile) || loaded.contains(libraryFile.toString());
  }

  /** Where each owned library's copy of this lock lives, in load order: one it only loaded, or a zip, gets none. */
  public List<Path> lockFiles() {
    return libraries.stream().filter(library -> !loaded.contains(library) && !library.endsWith(FileUtils.ZIP_EXTENSION))
        .map(library -> DaemonPaths.lockFileIn(Paths.get(library).getParent())).toList();
  }

  /**
   * Writes every copy of this lock: the registry's first, the first library's last, so that a client
   * finding that one can count on all the others. The libraries' {@code .arend/} must exist. The
   * registry's directory is made the user's alone, and only its copy carries the token.
   */
  public void writeEverywhere() throws IOException {
    PrivateFiles.ensurePrivateDirectory(DaemonPaths.registryDir());
    writeAtomic(registryFile());
    LockFile copy = withoutToken();
    for (Path library : lockFiles().reversed()) copy.writeAtomic(library);
  }

  /**
   * Removes every copy of this lock that still names this daemon. A copy naming another one is
   * left alone: the library has moved on to a daemon of its own, and its lock is the only way to
   * reach that one.
   */
  public void deleteAllCopies() {
    List<Path> copies = new ArrayList<>(lockFiles());
    // A hash that is not one names no entry, and must not name a path outside the registry either.
    if (HASH.matcher(libraryHash).matches()) copies.add(registryFile());
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
  public static final int PROTOCOL_VERSION = 2;

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
    /** A well-formed lock file. Through {@link #lookup}, the registry's copy it points at. */
    record Found(LockFile lock) implements Read {}
    /**
     * A library's copy that no registry entry of the user's confirms: left by a daemon whose entry
     * is gone, or put there by somebody else. Nothing it says is to be acted on -- not its address,
     * and not its process, which must never be signalled on its word.
     */
    record Unregistered(LockFile copy) implements Read {}
  }

  /**
   * The lock of the daemon that owns the library of {@code paths}, as the registry has it: the
   * library's own copy names the entry, and the entry -- private to the user, and the only copy with
   * the token -- has to agree that it is the same daemon and that it serves the library.
   */
  public static Read lookup(DaemonPaths paths) {
    Read read = read(paths.lockFile);
    if (!(read instanceof Read.Found found)) return read;
    LockFile copy = found.lock();
    Optional<LockFile> entry = registryEntry(copy.libraryHash)
        .filter(registered -> registered.pid == copy.pid && registered.processStart == copy.processStart
            && registered.serves(paths.libraryConfig));
    return entry.<Read>map(Read.Found::new).orElseGet(() -> new Read.Unregistered(copy));
  }

  /** The registry's entry for {@code libraryHash}, if it is there and is the user's alone. */
  static Optional<LockFile> registryEntry(String libraryHash) {
    if (!HASH.matcher(libraryHash).matches()) return Optional.empty();
    Path registry = DaemonPaths.registryDir();
    Path entry = registry.resolve(libraryHash + ".lock");
    if (!Files.isRegularFile(entry) || !PrivateFiles.isPrivate(registry, true) || !PrivateFiles.isPrivate(entry, false)) {
      return Optional.empty();
    }
    return readIfValid(entry).filter(lock -> lock.libraryHash.equals(libraryHash));
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
    for (int i = 0; i < loaded.size(); i++) props.setProperty("loaded." + (i + 1), loaded.get(i));
    props.setProperty("protocolVersion", Integer.toString(protocolVersion));
    props.setProperty("startedAt", Long.toString(startedAt));
    props.setProperty("socketPath", socketPath == null ? "" : socketPath);
    props.setProperty("host", host);
    if (processStart != UNKNOWN_START) props.setProperty("processStart", Long.toString(processStart));
    if (!token.isEmpty()) props.setProperty("token", token);
    if (build != null) {
      props.setProperty("build.path", build.path());
      props.setProperty("build.size", Long.toString(build.size()));
      props.setProperty("build.modified", Long.toString(build.modified()));
      props.setProperty("build.key", build.key());
    }
    if (!projectDir.isEmpty()) props.setProperty("projectDir", projectDir);

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
      String token = props.getProperty("token", "");
      String buildPath = props.getProperty("build.path");
      BuildIdentity build = buildPath == null ? null : new BuildIdentity(buildPath,
          Long.parseLong(props.getProperty("build.size", "-1").trim()), Long.parseLong(props.getProperty("build.modified", "-1").trim()),
          props.getProperty("build.key", ""));
      List<String> libraries = new ArrayList<>();
      for (int i = 1; props.getProperty("library." + i) != null; i++) libraries.add(props.getProperty("library." + i));
      // A lock from before library.N named only the one library its daemon served.
      if (libraries.isEmpty() && !libraryPath.isEmpty()) libraries.add(libraryPath);
      List<String> loaded = new ArrayList<>();
      for (int i = 1; props.getProperty("loaded." + i) != null; i++) loaded.add(props.getProperty("loaded." + i));
      if (libraryPath.isEmpty() || libraryHash.isEmpty()) {
        return new Read.Unreadable("lock file is missing libraryPath or libraryHash");
      }
      return new Read.Found(new LockFile(pid, libraryPath, libraryHash, libraries, loaded, protocolVersion, startedAt,
          socketPath, host, processStart, token, build, props.getProperty("projectDir", "")));
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

  /**
   * Every well-formed entry in {@link DaemonPaths#registryDir}, live or not, oldest daemon first; none
   * when the registry is not the user's alone, and no entry that is not.
   */
  public static List<LockFile> registered() {
    List<LockFile> out = new ArrayList<>();
    Path registry = DaemonPaths.registryDir();
    if (!Files.isDirectory(registry) || !PrivateFiles.isPrivate(registry, true)) return out;
    try (var entries = Files.newDirectoryStream(registry, "*.lock")) {
      for (Path entry : entries) {
        if (PrivateFiles.isPrivate(entry, false)) readIfValid(entry).ifPresent(out::add);
      }
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
