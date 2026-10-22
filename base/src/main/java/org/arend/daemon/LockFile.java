package org.arend.daemon;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
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
 *   processStart     when the process {@code pid} names started, in epoch milliseconds, as the
 *                    process itself reads it; absent when the platform does not say
 * </pre>
 *
 * @param pid             see above
 * @param libraryPath     see above
 * @param libraryHash     see above
 * @param protocolVersion see above
 * @param startedAt       see above
 * @param socketPath      see above
 * @param processStart    see above; {@link #UNKNOWN_START} when absent
 */
public record LockFile(long pid, String libraryPath, String libraryHash,
                       int protocolVersion, long startedAt, String socketPath, long processStart) {
  /** The {@code processStart} of a lock that does not say when its process started. */
  public static final long UNKNOWN_START = -1;

  /**
   * How far apart two readings of one process's start may be. They are the same number read twice,
   * but a platform may derive it from a clock with coarser steps than a millisecond.
   */
  private static final long START_TOLERANCE_MILLIS = 1000;

  /** The lock of the daemon this process runs. */
  public static LockFile ofThisProcess(DaemonPaths paths, long startedAt, String socketPath) {
    ProcessHandle self = ProcessHandle.current();
    return new LockFile(self.pid(), paths.libraryConfig.toString(), paths.libraryHash, PROTOCOL_VERSION,
        startedAt, socketPath, startOf(self));
  }

  /** When {@code process} started, in epoch milliseconds, or {@link #UNKNOWN_START}. */
  static long startOf(ProcessHandle process) {
    return process.info().startInstant().map(Instant::toEpochMilli).orElse(UNKNOWN_START);
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
    return process(pid, processStart);
  }

  /** The process {@code pid} names, provided that it started at {@code processStart}, as {@link #process()} judges it. */
  static Optional<ProcessHandle> process(long pid, long processStart) {
    return ProcessHandle.of(pid).filter(process -> startedAsRecorded(process, processStart));
  }

  private static boolean startedAsRecorded(ProcessHandle process, long processStart) {
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
    props.setProperty("protocolVersion", Integer.toString(protocolVersion));
    props.setProperty("startedAt", Long.toString(startedAt));
    props.setProperty("socketPath", socketPath == null ? "" : socketPath);
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
      long processStart = Long.parseLong(props.getProperty("processStart", Long.toString(UNKNOWN_START)).trim());
      if (libraryPath.isEmpty() || libraryHash.isEmpty()) {
        return new Read.Unreadable("lock file is missing libraryPath or libraryHash");
      }
      return new Read.Found(new LockFile(pid, libraryPath, libraryHash, protocolVersion, startedAt, socketPath, processStart));
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
