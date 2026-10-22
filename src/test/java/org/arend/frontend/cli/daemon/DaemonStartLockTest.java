package org.arend.frontend.cli.daemon;

import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.StartMarker;
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
        new LockFile(4242, "/lib/arend.yaml", hash, LockFile.PROTOCOL_VERSION, 0L, "uds:/s", LockFile.UNKNOWN_START));
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

    assertEquals("--daemon-bootstrap", appArgs.getFirst());
    assertEquals("the inner pipeline's positional must be the config path",
        config, appArgs.get(appArgs.size() - 1));
    assertFalse("the reference the user typed must not reach the child",
        appArgs.contains(root.getFileName().toString()));
  }

  /**
   * Two starts racing must not both spawn. The window is as long as the child takes to load its
   * libraries, and the loser cannot bind the socket: it becomes an unreachable JVM holding a second
   * copy of the library. A start that was killed must not block the next one, which takes its
   * marker over at once.
   */
  @Test
  public void onlyOneStartAtATimeMayProceedAndAKilledOnesMarkerIsReclaimed() throws IOException {
    Path marker = tempFolder.newFolder("state").toPath().resolve("daemon.starting");
    assertTrue("the first start must be allowed", StartMarker.reserve(marker));
    assertFalse("a second start while one is in flight must be refused", StartMarker.reserve(marker));

    Files.writeString(marker, killedStart());
    assertTrue("the marker of a start whose processes are gone must be reclaimed", StartMarker.reserve(marker));
    assertTrue("and then stand for the start that took it over", StartMarker.inProgress(marker));
  }

  /** A marker that names no process is one between being made and being written: only its age tells. */
  @Test
  public void aMarkerNamingNoProcessIsJudgedByItsAge() throws IOException {
    Path marker = tempFolder.newFolder("state").toPath().resolve("daemon.starting");
    Files.writeString(marker, "");
    assertFalse("a marker being written must be respected", StartMarker.reserve(marker));
    Files.setLastModifiedTime(marker, FileTime.fromMillis(System.currentTimeMillis() - DaemonPaths.START_TIMEOUT_MILLIS - 1000));
    assertTrue("a marker older than any start is debris", StartMarker.reserve(marker));
  }

  /** A start removes its own marker, and never one another start has made since. */
  @Test
  public void aStartRemovesOnlyAMarkerThatNamesIt() throws IOException {
    Path marker = tempFolder.newFolder("state").toPath().resolve("daemon.starting");
    Files.writeString(marker, killedStart());
    StartMarker.release(marker, ProcessHandle.current());
    assertTrue("a marker naming this PID for a process that started at another time is not this one's",
        Files.exists(marker));

    assertTrue(StartMarker.reserve(marker));
    StartMarker.release(marker, ProcessHandle.current());
    assertFalse(Files.exists(marker));
  }

  /**
   * A marker of a start none of whose processes runs: one of a PID nothing has, and one of this
   * process's PID as a process that started at another time had it.
   */
  private static String killedStart() {
    return Long.MAX_VALUE + " 0\n" + ProcessHandle.current().pid() + " 1\n";
  }
}
