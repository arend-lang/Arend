package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.client.DaemonRpc;
import org.junit.Test;

import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** What {@code arend --daemon-stop} concludes from the lock, and when it escalates. */
public class DaemonStopLockTest {
  private static final Predicate<LockFile> ALIVE = lf -> true;
  private static final Predicate<LockFile> DEAD = lf -> false;

  private static LockFile.Read found(String hash) {
    return new LockFile.Read.Found(
        new LockFile(4242, "/lib/arend.yaml", hash, LockFile.PROTOCOL_VERSION, 0L, "uds:/s"));
  }

  @Test
  public void noLockMeansNothingToStop() {
    assertEquals(DaemonStop.Verdict.NOTHING_TO_DO,
        DaemonStop.inspectLock(new LockFile.Read.Absent(), "h", DEAD));
  }

  @Test
  public void anUnreadableLockIsReportedRatherThanRemoved() {
    assertEquals(DaemonStop.Verdict.UNREADABLE_LOCK,
        DaemonStop.inspectLock(new LockFile.Read.Unreadable("boom"), "h", DEAD));
  }

  @Test
  public void ourRunningDaemonIsSignalled() {
    assertEquals(DaemonStop.Verdict.SIGNAL, DaemonStop.inspectLock(found("h"), "h", ALIVE));
  }

  @Test
  public void ourDeadDaemonsLockIsJustRemoved() {
    assertEquals(DaemonStop.Verdict.REMOVE_STALE, DaemonStop.inspectLock(found("h"), "h", DEAD));
  }

  @Test
  public void anotherLibrarysDeadLockIsRemoved() {
    assertEquals(DaemonStop.Verdict.REMOVE_STALE_MISMATCH,
        DaemonStop.inspectLock(found("other"), "h", DEAD));
  }

  /**
   * A running daemon for a different identity in the same directory is not ours to stop, and its
   * lock is the only record of where it listens -- removing it and reporting success leaves a
   * process nobody can reach.
   */
  @Test
  public void anotherLibrarysRunningDaemonIsNotSilentlyDelisted() {
    assertEquals(DaemonStop.Verdict.REFUSE_LIVE_MISMATCH,
        DaemonStop.inspectLock(found("other"), "h", ALIVE));
  }

  @Test
  public void aCleanAcknowledgementFollowedByTheLockVanishingIsSuccess() {
    assertTrue(DaemonStop.shutdownSucceeded(0, () -> true));
  }

  @Test
  public void anAcknowledgementWithoutTheLockVanishingEscalates() {
    assertFalse(DaemonStop.shutdownSucceeded(0, () -> false));
  }

  /**
   * The upgrade case. A daemon from an older build makes {@code reachableDaemon} return
   * NO_DAEMON, and its own warning tells the user to run {@code --daemon-stop}. Treating that as
   * success makes that command a no-op, so the stale daemon can never be stopped -- exactly the
   * situation the protocol version exists to get out of.
   */
  @Test
  public void anUnreachableButLiveDaemonMustStillBeSignalled() {
    assertFalse("NO_DAEMON from the shutdown RPC is not a stopped daemon",
        DaemonStop.shutdownSucceeded(DaemonRpc.NO_DAEMON, () -> false));
    assertFalse("nor is any other non-zero result",
        DaemonStop.shutdownSucceeded(1, () -> false));
  }
}
