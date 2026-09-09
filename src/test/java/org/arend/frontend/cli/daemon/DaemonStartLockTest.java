package org.arend.frontend.cli.daemon;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What {@code arend -d} does about a lock file that is already there.
 *
 * <p>Every branch here decides whether a possibly-running daemon keeps the lock that is the only
 * way to find it. Removing a live daemon's lock does not stop the daemon: it leaves the process
 * holding a warm library and a bound socket, with nothing on disk pointing at either, so it can
 * no longer be reached, refreshed or stopped.
 */
public class DaemonStartLockTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final Predicate<LockFile> ALIVE = lf -> true;
  private static final Predicate<LockFile> DEAD = lf -> false;

  private static LockFile lock(String hash) {
    return new LockFile(4242, "/lib/arend.yaml", hash, LockFile.PROTOCOL_VERSION, 0L, "uds:/s");
  }

  private static LockFile.Read found(String hash) {
    return new LockFile.Read.Found(lock(hash));
  }

  @Test
  public void noLockMeansNothingIsInTheWay() {
    assertEquals(DaemonStart.Verdict.NO_DAEMON,
        DaemonStart.inspectLock(new LockFile.Read.Absent(), "h", DEAD));
  }

  @Test
  public void anUnreadableLockIsRefusedRatherThanGuessedAt() {
    assertEquals(DaemonStart.Verdict.UNREADABLE_LOCK,
        DaemonStart.inspectLock(new LockFile.Read.Unreadable("boom"), "h", DEAD));
  }

  @Test
  public void aHealthyDaemonForThisLibraryIsLeftAlone() {
    assertEquals(DaemonStart.Verdict.ALREADY_RUNNING,
        DaemonStart.inspectLock(found("h"), "h", ALIVE));
  }

  @Test
  public void aLockForThisLibraryWithADeadProcessIsStale() {
    assertEquals(DaemonStart.Verdict.STALE_LOCK,
        DaemonStart.inspectLock(found("h"), "h", DEAD));
  }

  @Test
  public void aLockForAnotherLibraryWithADeadProcessIsStale() {
    assertEquals(DaemonStart.Verdict.STALE_MISMATCH,
        DaemonStart.inspectLock(found("other"), "h", DEAD));
  }

  /**
   * The case that matters. A daemon and a synthetic daemon in one directory are different
   * identities, and a moved or re-pointed {@code arend.yaml} changes the hash too -- so a lock
   * whose hash does not match can perfectly well belong to a process that is running right now.
   * Removing it and starting a second daemon orphans the first.
   */
  @Test
  public void aLockForAnotherLibraryWhoseProcessIsLiveIsNotRemoved() {
    assertEquals(DaemonStart.Verdict.LIVE_MISMATCH,
        DaemonStart.inspectLock(found("other"), "h", ALIVE));
  }

  /**
   * The child JVM must inherit the heap and stack it was started with.
   *
   * <p>The installed launcher is {@code java -Xms4G -Xmx16G -jar ...} and typechecking is deeply
   * recursive enough that this build runs its own tests with {@code -Xss16m}. A child built from
   * scratch gets the defaults for both, so a daemon can die on a library the identical direct
   * call handles -- and it dies as "daemon exited before becoming ready", which names neither
   * cause.
   */
  @Test
  public void theChildJvmInheritsThisOnesJvmOptions() throws IOException {
    Path root = tempFolder.newFolder("mylib").toPath();
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    DaemonPaths paths = DaemonPaths.resolve(root);

    List<String> cmd = DaemonStart.buildChildCommand(root.toString(), paths, List.of(), List.of(), null);

    List<String> forwardable = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .filter(DaemonStart::isForwardableJvmOption)
        .toList();
    assertTrue("this JVM must have something worth forwarding for the test to mean anything",
        !forwardable.isEmpty());
    for (String option : forwardable) {
      assertTrue("the child command must carry " + option + ": " + cmd, cmd.contains(option));
    }
    assertTrue("JVM options must precede the main class",
        cmd.indexOf(forwardable.get(0)) < cmd.indexOf("org.arend.frontend.ConsoleMain"));
  }

  /**
   * Two starts racing must not both spawn. The window is as long as a cold typecheck, so it is
   * not a narrow one; the loser cannot bind the socket and becomes an unreachable JVM holding a
   * second copy of the library.
   */
  @Test
  public void onlyOneStartAtATimeMayProceed() throws IOException {
    Path marker = tempFolder.newFolder("state").toPath().resolve("daemon.starting");
    assertTrue("the first start must be allowed", DaemonStart.reserveStart(marker, 60_000));
    assertFalse("a second start while one is in flight must be refused",
        DaemonStart.reserveStart(marker, 60_000));
  }

  /** Debris from a parent that was killed must not block starts forever. */
  @Test
  public void aStaleStartMarkerIsReclaimed() throws IOException {
    Path marker = tempFolder.newFolder("state").toPath().resolve("daemon.starting");
    assertTrue(DaemonStart.reserveStart(marker, 60_000));
    Files.setLastModifiedTime(marker, FileTime.fromMillis(System.currentTimeMillis() - 120_000));
    assertTrue("a marker older than any possible start must be reclaimed",
        DaemonStart.reserveStart(marker, 60_000));
  }
}
