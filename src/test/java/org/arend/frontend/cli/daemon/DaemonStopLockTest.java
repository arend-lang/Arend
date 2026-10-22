package org.arend.frontend.cli.daemon;

import org.arend.daemon.LockFile;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;

/** What {@code arend --daemon-stop} concludes from the lock. */
public class DaemonStopLockTest {
  private static final Predicate<LockFile> ALIVE = lf -> true;
  private static final Predicate<LockFile> DEAD = lf -> false;

  private static final Path A = Path.of("/a/arend.yaml");

  private static LockFile.Read found(Path library) {
    return new LockFile.Read.Found(new LockFile(4242, library.toString(), "h", List.of(library.toString()), List.of(),
        LockFile.PROTOCOL_VERSION, 0L, "uds:/s", LockFile.HOST_CLI, LockFile.UNKNOWN_START, "", null, ""));
  }

  /** A lock {@link LockFile#lookup} found serves the library: whether it is stopped turns on its process alone. */
  @Test
  public void anExistingLockIsClassifiedByLiveness() {
    assertEquals(DaemonStop.Verdict.NOTHING_TO_DO,
        DaemonStop.inspectLock(new LockFile.Read.Absent(), DEAD));
    assertEquals("an unparseable lock must be reported, not removed",
        DaemonStop.Verdict.UNREADABLE_LOCK,
        DaemonStop.inspectLock(new LockFile.Read.Unreadable("boom"), DEAD));
    assertEquals(DaemonStop.Verdict.SIGNAL, DaemonStop.inspectLock(found(A), ALIVE));
    assertEquals(DaemonStop.Verdict.REMOVE_STALE, DaemonStop.inspectLock(found(A), DEAD));
    // Alive or not, a copy the registry does not confirm is never signalled on its word.
    LockFile planted = ((LockFile.Read.Found) found(A)).lock();
    assertEquals(DaemonStop.Verdict.REMOVE_UNREGISTERED, DaemonStop.inspectLock(new LockFile.Read.Unregistered(planted), ALIVE));
  }
}
