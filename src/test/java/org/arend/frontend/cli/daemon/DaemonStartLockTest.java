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
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
        LockFile.PROTOCOL_VERSION, 0L, "uds:/s", LockFile.UNKNOWN_START, "", null, ""));
  }

  /** The verdict table, for a lock {@link LockFile#lookup} found: one that serves the library. */
  @Test
  public void anExistingLockIsClassifiedByLiveness() {
    List<String> justA = List.of(A.toString());
    assertEquals(DaemonStart.Verdict.NO_DAEMON,
        DaemonStart.inspectLock(new LockFile.Read.Absent(), justA, DEAD));
    assertEquals("an unparseable lock must be refused, not guessed at",
        DaemonStart.Verdict.UNREADABLE_LOCK,
        DaemonStart.inspectLock(new LockFile.Read.Unreadable("boom"), justA, DEAD));
    assertEquals(DaemonStart.Verdict.ALREADY_RUNNING, DaemonStart.inspectLock(found(A), justA, ALIVE));
    assertEquals(DaemonStart.Verdict.STALE_LOCK, DaemonStart.inspectLock(found(A), justA, DEAD));
  }

  /** A copy no registry entry confirms names no daemon a client could reach, live PID or not. */
  @Test
  public void anUnregisteredLockStopsNoStart() {
    LockFile planted = ((LockFile.Read.Found) found(A)).lock();
    assertEquals(DaemonStart.Verdict.UNREGISTERED_LOCK,
        DaemonStart.inspectLock(new LockFile.Read.Unregistered(planted), List.of(A.toString()), ALIVE));
  }

  /**
   * Two daemons must not share a library: a live daemon whose set includes this library but is
   * not the set asked for refuses the start, whichever of the two is larger. The same set in
   * another order is the same daemon.
   */
  @Test
  public void aLiveDaemonWithAnOverlappingSetRefusesTheStart() {
    List<String> ab = List.of(A.toString(), B.toString());
    assertEquals(DaemonStart.Verdict.OVERLAP, DaemonStart.inspectLock(found(A), ab, ALIVE));
    assertEquals(DaemonStart.Verdict.OVERLAP,
        DaemonStart.inspectLock(found(A, B), List.of(A.toString()), ALIVE));
    assertEquals(DaemonStart.Verdict.OVERLAP,
        DaemonStart.inspectLock(found(A, B), List.of(B.toString()), ALIVE));
    assertEquals(DaemonStart.Verdict.ALREADY_RUNNING,
        DaemonStart.inspectLock(found(B, A), ab, ALIVE));
    assertEquals("a dead daemon's set is no obstacle",
        DaemonStart.Verdict.STALE_LOCK, DaemonStart.inspectLock(found(A), ab, DEAD));
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
    List<String> cmd = DaemonStart.buildChildCommand(List.of(DaemonPaths.resolve(library())), List.of(), List.of(), "cp", List.of());

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
    List<String> cmd = DaemonStart.buildChildCommand(List.of(paths), List.of(), List.of(), "cp", List.of());

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
   * Several libraries reach the child in the order given, all by config path, after everything
   * else.
   */
  @Test
  public void theChildIsToldEveryLibraryInOrder() throws IOException {
    Path first = library();
    Path second = tempFolder.newFolder("other").toPath();
    Files.writeString(second.resolve("arend.yaml"), "dependencies: [mylib]\n", StandardCharsets.UTF_8);
    DaemonPaths a = DaemonPaths.resolve(first), b = DaemonPaths.resolve(second);
    List<String> cmd = DaemonStart.buildChildCommand(List.of(a, b), List.of(), List.of("-c"), "cp", List.of());

    List<String> appArgs = cmd.subList(cmd.indexOf("org.arend.frontend.ConsoleMain") + 1, cmd.size());
    assertEquals(List.of("-c", a.libraryConfig.toString(), b.libraryConfig.toString()),
        appArgs.subList(appArgs.size() - 3, appArgs.size()));
  }

  /**
   * A shared library may come first in load order, but its directory is no daemon's: the socket
   * and the log go to the first library the daemon owns.
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

      List<String> cmd = DaemonStart.buildChildCommand(libraries, List.of(), List.of(), "cp", List.of());
      List<String> appArgs = cmd.subList(cmd.indexOf("org.arend.frontend.ConsoleMain") + 1, cmd.size());
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
   * The locks are checked when `-d` runs, and the child claims its libraries only once they are
   * loaded, seconds later: whatever claimed one of them meanwhile keeps it, and this start gives up.
   */
  @Test
  public void aLibraryClaimedWhileTheChildLoadedStopsTheStart() throws IOException {
    java.nio.file.Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    try {
      DaemonPaths paths = DaemonPaths.resolve(library());
      paths.ensureArendDir();
      assertNull(org.arend.frontend.cli.daemon.server.DaemonMain.claimedMeanwhile(List.of(paths)));

      LockFile.ofThisProcess(paths, List.of(paths.libraryConfig.toString()), List.of(), 1L, "uds:/s",
          LockFile.newToken()).writeEverywhere();
      String claimed = org.arend.frontend.cli.daemon.server.DaemonMain.claimedMeanwhile(List.of(paths));
      assertNotNull(claimed);
      assertTrue(claimed, claimed.contains("PID " + ProcessHandle.current().pid()));
    } finally {
      System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
    }
  }

  /**
   * A marker of a start none of whose processes runs: one of a PID nothing has, and one of this
   * process's PID as a process that started at another time had it.
   */
  private static String killedStart() {
    return Long.MAX_VALUE + " 0\n" + ProcessHandle.current().pid() + " 1\n";
  }

  /**
   * A daemon runs from a copy of the jar, which a rebuild does not touch: one per build, the same one
   * picked again, and another made once the jar has changed. A copy left by a build nobody runs goes;
   * a young one might be one a daemon's JVM has not opened yet, and stays.
   */
  @Test
  public void aDaemonRunsFromACopyOfItsBuildsJar() throws IOException {
    Path jar = tempFolder.newFile("arend.jar").toPath();
    Files.write(jar, new byte[] { 1, 2, 3 });
    Path dir = tempFolder.getRoot().toPath().resolve("copies");
    org.arend.daemon.BuildIdentity build = org.arend.daemon.BuildIdentity.of(jar);

    Path copy = DaemonStart.runnableCopy(build, dir);
    assertTrue(Arrays.equals(new byte[] { 1, 2, 3 }, Files.readAllBytes(copy)));
    assertEquals("the same build, the same copy", copy, DaemonStart.runnableCopy(build, dir));

    Path old = dir.resolve("arend-0000000000000000.jar");
    Files.write(old, new byte[] { 9 });
    Files.setLastModifiedTime(old, FileTime.fromMillis(System.currentTimeMillis() - 3_600_000L));
    Path young = dir.resolve("arend-1111111111111111.jar");
    Files.write(young, new byte[] { 9 });

    Files.write(jar, new byte[] { 4, 5, 6, 7 });
    org.arend.daemon.BuildIdentity rebuilt = org.arend.daemon.BuildIdentity.of(jar);
    Path next = DaemonStart.runnableCopy(rebuilt, dir);
    assertFalse("another build, another copy", next.equals(copy));
    assertTrue(Arrays.equals(new byte[] { 4, 5, 6, 7 }, Files.readAllBytes(next)));
    assertTrue("the first build's copy is still there for the daemon running it", Files.exists(copy));
    assertFalse("a copy of a build nobody runs is removed", Files.exists(old));
    assertTrue("a young copy is left for the daemon about to open it", Files.exists(young));
  }

  /** The child runs the copy, and is told which jar it is a copy of: that one's changing is what retires it. */
  @Test
  public void theChildIsToldWhichJarItRunsACopyOf() throws IOException {
    String property = "-D" + org.arend.daemon.BuildIdentity.ORIGINAL_JAR_PROPERTY + "=/the/arend.jar";
    List<String> cmd = DaemonStart.buildChildCommand(List.of(DaemonPaths.resolve(library())), List.of(), List.of(),
        "/copies/arend-0123456789abcdef.jar", List.of(property));
    assertTrue(cmd.toString(), cmd.contains(property));
    assertEquals("/copies/arend-0123456789abcdef.jar", cmd.get(cmd.indexOf("-cp") + 1));
    assertTrue("JVM properties must precede the main class", cmd.indexOf(property) < cmd.indexOf("org.arend.frontend.ConsoleMain"));
  }
}
