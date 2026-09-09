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
 * <p>Every branch decides whether a possibly-running daemon keeps the lock that is the only way
 * to find it. Removing a live daemon's lock does not stop the daemon: it leaves the process
 * holding a warm library and a bound socket with nothing on disk pointing at either, so it can no
 * longer be reached, refreshed or stopped.
 */
public class DaemonStartLockTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final Predicate<LockFile> ALIVE = lf -> true;
  private static final Predicate<LockFile> DEAD = lf -> false;

  private static LockFile.Read found(String hash) {
    return new LockFile.Read.Found(
        new LockFile(4242, "/lib/arend.yaml", hash, LockFile.PROTOCOL_VERSION, 0L, "uds:/s"));
  }

  /**
   * The whole verdict table. LIVE_MISMATCH is the case that matters: a moved or re-pointed
   * {@code arend.yaml} changes the hash, so a lock whose hash does not match can perfectly well
   * belong to a process running right now, and removing it orphans that process.
   */
  @Test
  public void anExistingLockIsClassifiedByHashAndLiveness() {
    assertEquals(DaemonStart.Verdict.NO_DAEMON,
        DaemonStart.inspectLock(new LockFile.Read.Absent(), "h", DEAD));
    assertEquals("an unparseable lock must be refused, not guessed at",
        DaemonStart.Verdict.UNREADABLE_LOCK,
        DaemonStart.inspectLock(new LockFile.Read.Unreadable("boom"), "h", DEAD));
    assertEquals(DaemonStart.Verdict.ALREADY_RUNNING, DaemonStart.inspectLock(found("h"), "h", ALIVE));
    assertEquals(DaemonStart.Verdict.STALE_LOCK, DaemonStart.inspectLock(found("h"), "h", DEAD));
    assertEquals(DaemonStart.Verdict.STALE_MISMATCH,
        DaemonStart.inspectLock(found("other"), "h", DEAD));
    assertEquals("a live daemon for another identity must keep its lock",
        DaemonStart.Verdict.LIVE_MISMATCH, DaemonStart.inspectLock(found("other"), "h", ALIVE));
  }

  private Path library() throws IOException {
    Path root = tempFolder.newFolder("mylib").toPath();
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    return root;
  }

  /**
   * The child must inherit the heap and stack this process was given -- the installed launcher
   * supplies both, and typechecking is deeply recursive enough that this build runs its own tests
   * with {@code -Xss16m}. A child built from scratch gets the defaults and then dies as "daemon
   * exited before becoming ready", which names neither cause.
   */
  @Test
  public void theChildJvmInheritsThisOnesJvmOptions() throws IOException {
    List<String> cmd = DaemonStart.buildChildCommand(DaemonPaths.resolve(library()), List.of(), List.of());

    List<String> forwardable = ManagementFactory.getRuntimeMXBean().getInputArguments().stream()
        .filter(DaemonStart::isForwardableJvmOption)
        .toList();
    assertFalse("this JVM must have something worth forwarding for the test to mean anything",
        forwardable.isEmpty());
    for (String option : forwardable) {
      assertTrue("the child command must carry " + option + ": " + cmd, cmd.contains(option));
    }
    assertTrue("JVM options must precede the main class",
        cmd.indexOf(forwardable.get(0)) < cmd.indexOf("org.arend.frontend.ConsoleMain"));
  }

  /**
   * The child must be told which library to load by its resolved config path, never by the
   * reference the user typed: the parent keys the daemon with {@code DaemonPaths.resolveByName},
   * which prefers the library in the current directory, while a bare name reaching the child is
   * resolved by {@code CliSetup.findLibrary}, which searches the {@code -L} directories first.
   * With a like-named copy under a libdir the daemon would warm that copy behind a lock
   * advertising this one. An absolute arend.yaml is the one form both resolvers agree on.
   */
  @Test
  public void theChildIsToldTheLibraryByItsConfigPath() throws IOException {
    Path root = library();
    DaemonPaths paths = DaemonPaths.resolve(root);
    List<String> cmd = DaemonStart.buildChildCommand(paths, List.of(), List.of());

    String config = paths.libraryConfig.toString();
    int main = cmd.indexOf("org.arend.frontend.ConsoleMain");
    assertTrue("the main class must be on the command line", main >= 0);
    List<String> appArgs = cmd.subList(main + 1, cmd.size());

    assertEquals("the bootstrap flag must name the library by config path",
        List.of("--daemon-bootstrap", config), appArgs.subList(0, 2));
    assertEquals("the inner pipeline's positional must be the config path too",
        config, appArgs.get(appArgs.size() - 1));
    assertFalse("the reference the user typed must not reach the child",
        appArgs.contains(root.getFileName().toString()));
  }

  /**
   * Two starts racing must not both spawn. The window is as long as a cold typecheck, and the
   * loser cannot bind the socket: it becomes an unreachable JVM holding a second copy of the
   * library. Debris from a killed parent must not block starts forever either.
   */
  @Test
  public void onlyOneStartAtATimeMayProceedAndAStaleMarkerIsReclaimed() throws IOException {
    Path marker = tempFolder.newFolder("state").toPath().resolve("daemon.starting");
    assertTrue("the first start must be allowed", DaemonStart.reserveStart(marker, 60_000));
    assertFalse("a second start while one is in flight must be refused",
        DaemonStart.reserveStart(marker, 60_000));

    Files.setLastModifiedTime(marker, FileTime.fromMillis(System.currentTimeMillis() - 120_000));
    assertTrue("a marker older than any possible start must be reclaimed",
        DaemonStart.reserveStart(marker, 60_000));
  }
}
