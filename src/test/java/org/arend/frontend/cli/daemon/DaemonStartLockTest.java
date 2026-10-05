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
import java.util.Arrays;
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

  private static final Path A = Path.of("/a/arend.yaml");
  private static final Path B = Path.of("/b/arend.yaml");

  /** A lock for a daemon serving {@code libraries}, the first of them being its home. */
  private static LockFile.Read found(Path... libraries) {
    List<String> configs = Arrays.stream(libraries).map(Path::toString).toList();
    return new LockFile.Read.Found(new LockFile(4242, configs.getFirst(), "h", configs, List.of(),
        LockFile.PROTOCOL_VERSION, 0L, "uds:/s"));
  }

  /**
   * The whole verdict table. LIVE_MISMATCH is the case that matters: a moved or re-pointed
   * {@code arend.yaml} leaves a lock behind that does not list this library and can perfectly well
   * belong to a process running right now, and removing it orphans that process.
   */
  @Test
  public void anExistingLockIsClassifiedByLibraryAndLiveness() {
    List<String> justA = List.of(A.toString());
    assertEquals(DaemonStart.Verdict.NO_DAEMON,
        DaemonStart.inspectLock(new LockFile.Read.Absent(), A, justA, DEAD));
    assertEquals("an unparseable lock must be refused, not guessed at",
        DaemonStart.Verdict.UNREADABLE_LOCK,
        DaemonStart.inspectLock(new LockFile.Read.Unreadable("boom"), A, justA, DEAD));
    assertEquals(DaemonStart.Verdict.ALREADY_RUNNING, DaemonStart.inspectLock(found(A), A, justA, ALIVE));
    assertEquals(DaemonStart.Verdict.STALE_LOCK, DaemonStart.inspectLock(found(A), A, justA, DEAD));
    assertEquals(DaemonStart.Verdict.STALE_MISMATCH, DaemonStart.inspectLock(found(B), A, justA, DEAD));
    assertEquals("a live daemon for another library must keep its lock",
        DaemonStart.Verdict.LIVE_MISMATCH, DaemonStart.inspectLock(found(B), A, justA, ALIVE));
  }

  /**
   * Two daemons must not share a library: a live daemon whose set includes this library but is
   * not the set asked for refuses the start, whichever of the two is larger. The same set in
   * another order is the same daemon.
   */
  @Test
  public void aLiveDaemonWithAnOverlappingSetRefusesTheStart() {
    List<String> ab = List.of(A.toString(), B.toString());
    assertEquals(DaemonStart.Verdict.OVERLAP, DaemonStart.inspectLock(found(A), A, ab, ALIVE));
    assertEquals(DaemonStart.Verdict.OVERLAP,
        DaemonStart.inspectLock(found(A, B), A, List.of(A.toString()), ALIVE));
    assertEquals(DaemonStart.Verdict.OVERLAP,
        DaemonStart.inspectLock(found(A, B), B, List.of(B.toString()), ALIVE));
    assertEquals(DaemonStart.Verdict.ALREADY_RUNNING,
        DaemonStart.inspectLock(found(B, A), A, ab, ALIVE));
    assertEquals("a dead daemon's set is no obstacle",
        DaemonStart.Verdict.STALE_LOCK, DaemonStart.inspectLock(found(A), A, ab, DEAD));
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
    List<String> cmd = DaemonStart.buildChildCommand(List.of(DaemonPaths.resolve(library())), List.of(), List.of(), "cp");

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
    List<String> cmd = DaemonStart.buildChildCommand(List.of(paths), List.of(), List.of(), "cp");

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
   * Several libraries reach the child in the order given, all by config path, after everything
   * else: the first is named after the bootstrap flag, since its directory holds the socket.
   */
  @Test
  public void theChildIsToldEveryLibraryInOrder() throws IOException {
    Path first = library();
    Path second = tempFolder.newFolder("other").toPath();
    Files.writeString(second.resolve("arend.yaml"), "dependencies: [mylib]\n", StandardCharsets.UTF_8);
    DaemonPaths a = DaemonPaths.resolve(first), b = DaemonPaths.resolve(second);
    List<String> cmd = DaemonStart.buildChildCommand(List.of(a, b), List.of(), List.of("-c"), "cp");

    List<String> appArgs = cmd.subList(cmd.indexOf("org.arend.frontend.ConsoleMain") + 1, cmd.size());
    assertEquals(List.of("--daemon-bootstrap", a.libraryConfig.toString()), appArgs.subList(0, 2));
    assertEquals(List.of("-c", a.libraryConfig.toString(), b.libraryConfig.toString()),
        appArgs.subList(appArgs.size() - 3, appArgs.size()));
  }

  /**
   * A shared library may come first in load order, but its directory is no daemon's: the socket
   * and the log go to the first library the daemon owns, and the bootstrap flag names that one.
   */
  @Test
  public void theHomeIsTheFirstOwnedLibrary() throws IOException {
    Path config = tempFolder.newFolder("config").toPath();
    Path shared = config.resolve("libs").resolve("sharedlib");
    Files.createDirectories(shared);
    Files.writeString(shared.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    try {
      DaemonPaths sharedPaths = DaemonPaths.resolve(shared), owned = DaemonPaths.resolve(library());
      assertTrue(sharedPaths.isShared());
      assertFalse(owned.isShared());
      List<DaemonPaths> libraries = List.of(sharedPaths, owned);
      assertEquals(owned.libraryConfig, DaemonStart.home(libraries).libraryConfig);

      List<String> cmd = DaemonStart.buildChildCommand(libraries, List.of(), List.of(), "cp");
      List<String> appArgs = cmd.subList(cmd.indexOf("org.arend.frontend.ConsoleMain") + 1, cmd.size());
      assertEquals(List.of("--daemon-bootstrap", owned.libraryConfig.toString()), appArgs.subList(0, 2));
      assertEquals("load order is kept as given",
          List.of(sharedPaths.libraryConfig.toString(), owned.libraryConfig.toString()),
          appArgs.subList(appArgs.size() - 2, appArgs.size()));
    } finally {
      System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
    }
  }

  /**
   * Two libraries of one name cannot share a daemon, and the start is refused before anything is
   * written into either.
   */
  @Test
  public void twoLibrariesOfOneNameAreRefused() throws IOException {
    Path first = tempFolder.newFolder("a", "foo").toPath();
    Path second = tempFolder.newFolder("b", "foo").toPath();
    for (Path root : List.of(first, second)) {
      Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    }
    assertEquals(1, DaemonStart.run(List.of(first.toString(), second.toString()), List.of(), List.of()));
    assertFalse(Files.exists(first.resolve(".arend")));
    assertFalse(Files.exists(second.resolve(".arend")));

    // A zip is named after its file, so it clashes with a project of that name too.
    Path zip = Files.writeString(tempFolder.getRoot().toPath().resolve("foo.zip"), "");
    assertEquals(1, DaemonStart.run(List.of(first.toString(), zip.toString()), List.of(), List.of()));
    assertFalse(Files.exists(first.resolve(".arend")));
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
