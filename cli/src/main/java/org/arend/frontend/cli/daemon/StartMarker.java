package org.arend.frontend.cli.daemon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link DaemonPaths#startingMarker}: the processes a start of {@code arend -d} consists of, one per line
 * as {@code <pid> <processStart>} ({@link LockFile} has both) -- the {@code arend -d} that made it, then the
 * daemon it spawned. The daemon claims its libraries only once it is ready, seconds later, and until then
 * the marker says they are taken.
 *
 * <p>A start is under way while one of its processes runs. Once none does, the marker is debris from a
 * start that was killed, and is reclaimed at once. A killed {@code arend -d} -- a closed terminal, a
 * {@code kill -9} -- leaves its detached daemon loading, which is why that one is named too.
 *
 * <pre>
 *   12345 1792835182000
 *   12346 1792835182400
 * </pre>
 *
 * <p>A marker that names no process is judged by its age ({@link DaemonPaths#START_TIMEOUT_MILLIS}): it is
 * empty between being made and being written.
 */
public final class StartMarker {
  private StartMarker() {}

  /** Whether {@code marker} stands for a start under way. */
  public static boolean inProgress(Path marker) {
    try {
      List<long[]> named = named(marker);
      if (named.isEmpty()) {
        return System.currentTimeMillis() - Files.getLastModifiedTime(marker).toMillis() < DaemonPaths.START_TIMEOUT_MILLIS;
      }
      return named.stream().anyMatch(process -> LockFile.process(process[0], process[1]).isPresent());
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Claims the right to start a daemon for the library {@code marker} is in, atomically, for this process.
   *
   * <p>{@code CREATE_NEW} is the whole mechanism: between reading the lock and the child writing one there
   * is a window of however long the child takes to load its libraries, and two calls in that window both
   * see no daemon and both spawn. The loser cannot bind the socket and quietly becomes an unreachable JVM
   * holding a second copy of the library. The marker of a start no longer under way is reclaimed.
   *
   * @return true if the caller may proceed.
   */
  public static boolean reserve(Path marker) {
    try {
      create(marker);
      return true;
    } catch (FileAlreadyExistsException e) {
      if (inProgress(marker)) return false;
      try {
        Files.deleteIfExists(marker);
        create(marker);
        return true;
      } catch (IOException retry) {
        return false;
      }
    } catch (IOException e) {
      // The directory will not take the marker; it will not take the lock either, and the spawn
      // reports that properly. Refusing here would hide it behind a worse message.
      return true;
    }
  }

  private static void create(Path marker) throws IOException {
    Files.writeString(marker, line(ProcessHandle.current()), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW,
        StandardOpenOption.WRITE);
  }

  /** Names {@code process} in {@code marker} too: the daemon the start has spawned. Nothing if the marker is gone. */
  public static void add(Path marker, ProcessHandle process) {
    try {
      Files.writeString(marker, line(process), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
    } catch (IOException ignored) {
      // The process that made it keeps it standing while it waits for this one.
    }
  }

  /** Removes {@code marker} if it names {@code process}: the start is over, and no other start's marker goes with it. */
  public static void release(Path marker, ProcessHandle process) {
    try {
      // By its start too: a PID the marker names may be one this process was given only once that one's was gone.
      if (named(marker).stream().anyMatch(named -> named[0] == process.pid() && LockFile.process(named[0], named[1]).isPresent())) {
        LockFile.deleteQuietly(marker);
      }
    } catch (IOException ignored) {
      // Gone already.
    }
  }

  /** The {@code {pid, processStart}} pairs {@code marker} names; a line that is not one -- half-written -- is skipped. */
  private static List<long[]> named(Path marker) throws IOException {
    List<long[]> named = new ArrayList<>();
    for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
      String[] fields = line.trim().split(" ");
      if (fields.length != 2) continue;
      try {
        named.add(new long[] { Long.parseLong(fields[0]), Long.parseLong(fields[1]) });
      } catch (NumberFormatException ignored) {
      }
    }
    return named;
  }

  private static String line(ProcessHandle process) {
    return process.pid() + " " + LockFile.startOf(process) + "\n";
  }
}
