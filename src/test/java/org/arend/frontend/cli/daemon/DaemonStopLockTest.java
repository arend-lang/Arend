package org.arend.frontend.cli.daemon;

import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;

/** What {@code arend --daemon-stop} concludes from the lock. */
public class DaemonStopLockTest {
  private static final Predicate<LockFile> ALIVE = lf -> true;
  private static final Predicate<LockFile> DEAD = lf -> false;

  private static LockFile.Read found(String hash) {
    return new LockFile.Read.Found(
        new LockFile(4242, "/lib/arend.yaml", hash, LockFile.PROTOCOL_VERSION, 0L, "uds:/s"));
  }

  /**
   * REFUSE_LIVE_MISMATCH is the case that matters: a running daemon for a different identity in
   * the same directory is not ours to stop, and its lock is the only record of where it listens,
   * so removing it and reporting success leaves a process nobody can reach.
   */
  @Test
  public void anExistingLockIsClassifiedByHashAndLiveness() {
    assertEquals(DaemonStop.Verdict.NOTHING_TO_DO,
        DaemonStop.inspectLock(new LockFile.Read.Absent(), "h", DEAD));
    assertEquals("an unparseable lock must be reported, not removed",
        DaemonStop.Verdict.UNREADABLE_LOCK,
        DaemonStop.inspectLock(new LockFile.Read.Unreadable("boom"), "h", DEAD));
    assertEquals(DaemonStop.Verdict.SIGNAL, DaemonStop.inspectLock(found("h"), "h", ALIVE));
    assertEquals(DaemonStop.Verdict.REMOVE_STALE, DaemonStop.inspectLock(found("h"), "h", DEAD));
    assertEquals(DaemonStop.Verdict.REMOVE_STALE_MISMATCH,
        DaemonStop.inspectLock(found("other"), "h", DEAD));
    assertEquals("a live daemon for another identity must not be silently delisted",
        DaemonStop.Verdict.REFUSE_LIVE_MISMATCH,
        DaemonStop.inspectLock(found("other"), "h", ALIVE));
  }
}
