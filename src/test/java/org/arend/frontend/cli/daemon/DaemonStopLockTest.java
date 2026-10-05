package org.arend.frontend.cli.daemon;

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
  private static final Path B = Path.of("/b/arend.yaml");

  private static LockFile.Read found(Path... libraries) {
    List<String> configs = java.util.Arrays.stream(libraries).map(Path::toString).toList();
    return new LockFile.Read.Found(new LockFile(4242, configs.getFirst(), "h", configs, List.of(),
        LockFile.PROTOCOL_VERSION, 0L, "uds:/s"));
  }

  /**
   * REFUSE_LIVE_MISMATCH is the case that matters: a running daemon for a different identity in
   * the same directory is not ours to stop, and its lock is the only record of where it listens,
   * so removing it and reporting success leaves a process nobody can reach.
   */
  @Test
  public void anExistingLockIsClassifiedByLibraryAndLiveness() {
    assertEquals(DaemonStop.Verdict.NOTHING_TO_DO,
        DaemonStop.inspectLock(new LockFile.Read.Absent(), A, DEAD));
    assertEquals("an unparseable lock must be reported, not removed",
        DaemonStop.Verdict.UNREADABLE_LOCK,
        DaemonStop.inspectLock(new LockFile.Read.Unreadable("boom"), A, DEAD));
    assertEquals(DaemonStop.Verdict.SIGNAL, DaemonStop.inspectLock(found(A), A, ALIVE));
    assertEquals(DaemonStop.Verdict.REMOVE_STALE, DaemonStop.inspectLock(found(A), A, DEAD));
    assertEquals(DaemonStop.Verdict.REMOVE_STALE_MISMATCH, DaemonStop.inspectLock(found(B), A, DEAD));
    assertEquals("a live daemon for another library must not be silently delisted",
        DaemonStop.Verdict.REFUSE_LIVE_MISMATCH, DaemonStop.inspectLock(found(B), A, ALIVE));
    assertEquals("any library a daemon serves stops it",
        DaemonStop.Verdict.SIGNAL, DaemonStop.inspectLock(found(B, A), A, ALIVE));
  }
}
