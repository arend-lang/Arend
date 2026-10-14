package org.arend.frontend.cli.daemon;

import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.server.SocketBinder;
import org.arend.daemon.wire.FrameChannel;
import org.arend.daemon.client.DaemonRpc;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * What the client puts on the wire when it routes an ordinary command, and when it declines to.
 * A stand-in daemon answers, because the point is the contents of the request and a real daemon
 * would need a warm library to produce one.
 */
public class DaemonRouteTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private Path libRoot;
  private String address;
  private ServerSocketChannel listener;
  private Thread acceptor;
  private final CountDownLatch served = new CountDownLatch(1);
  private final AtomicReference<Map<String, Object>> seen = new AtomicReference<>();

  /**
   * A library with a lock naming the address actually bound -- not the one asked for. A socket
   * path has a length limit the rest of the filesystem does not, and a canonical temp path is
   * past it, so SocketBinder legitimately answers elsewhere; a lock naming the path that could
   * not be bound points the client at nothing, which looks exactly like "not routed".
   */
  @Before
  public void setUp() throws IOException {
    assumeTrue("Unix domain sockets required",
        !System.getProperty("os.name", "").toLowerCase().startsWith("windows"));

    libRoot = tempFolder.newFolder("mylib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    DaemonPaths paths = DaemonPaths.resolve(libRoot);
    assertNotNull(paths);
    paths.ensureArendDir();

    SocketBinder.Bound bound = SocketBinder.bind(paths.socketFile, paths.libraryHash);
    listener = bound.channel();
    address = bound.address();
    new LockFile(ProcessHandle.current().pid(), paths.libraryConfig.toString(), paths.libraryHash, List.of(paths.libraryConfig.toString()), List.of(), LockFile.PROTOCOL_VERSION, System.currentTimeMillis(), address, LockFile.HOST_CLI).writeAtomic(paths.lockFile);

    // Answers exactly one request and records it.
    acceptor = new Thread(() -> {
      try (SocketChannel ch = listener.accept()) {
        FrameChannel out = new FrameChannel(ch);
        Map<String, Object> req = FrameChannel.read(ch);
        seen.set(req);
        out.write(FrameChannel.doneFrame(String.valueOf(req.get("id")), 0));
      } catch (IOException ignored) {
      } finally {
        served.countDown();
      }
    }, "stand-in-daemon");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  @After
  public void tearDown() throws IOException {
    System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
    if (listener != null) listener.close();
    if (acceptor != null) acceptor.interrupt();
  }

  /**
   * A relative path means the directory the user typed the command in. The daemon's own working
   * directory is whatever shell started it and never changes, so unless the client says where it
   * was, the same command writes its output somewhere else the moment a daemon exists.
   */
  @Test
  public void aRoutedCommandCarriesTheClientsWorkingDirectory() throws Exception {
    String[] argv = { libRoot.toString(), "-ss", "foo" };
    OptionalInt rc = DaemonRpc.tryRouteCli(argv, List.of(libRoot.toString()), List.of());
    assertTrue("the command must have been routed to the daemon", rc.isPresent());
    assertEquals(0, rc.getAsInt());
    assertTrue("the stand-in daemon must have been reached", served.await(10, TimeUnit.SECONDS));

    Map<String, Object> req = seen.get();
    assertNotNull(req);
    assertEquals("cli", req.get("op"));
    assertEquals(List.of(argv), req.get("args"));
    assertEquals("the request must say where the client was",
        Path.of(".").toAbsolutePath().normalize().toString(), req.get("cwd"));
  }

  /**
   * A positional that exists on disk and is not this daemon's library must run locally.
   * {@code CliSetup.populateRequestedTargets} skips a positional that exists as a path, taking it
   * for the library already loaded, so a routed {@code arend ../other.zip} would check the
   * daemon's own library, find nothing wrong and exit 0, while the same command with --no-daemon
   * loads and checks the zip.
   */
  @Test
  public void aPositionalTheDaemonWouldDropIsNotRouted() throws Exception {
    Path stray = tempFolder.newFile("stray.zip").toPath();
    List<String> positional = List.of(libRoot.toString(), stray.toString());
    OptionalInt rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("a command carrying a positional the daemon would drop must run locally",
        rc.isEmpty());
  }

  /** A module name is not a path, so the daemon resolves it exactly as a local run would. */
  @Test
  public void aModuleNamePositionalIsStillRouted() throws Exception {
    List<String> positional = List.of(libRoot.toString(), "Data.Maybe");
    OptionalInt rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("a module name is not a path and must still route", rc.isPresent());
    assertTrue("the stand-in daemon must have been reached", served.await(10, TimeUnit.SECONDS));
  }

  private Path library(String name) throws IOException {
    Path root = tempFolder.newFolder(name).toPath();
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    return root;
  }

  private void serveAlso(Path... others) throws IOException {
    DaemonPaths home = DaemonPaths.resolve(libRoot);
    List<DaemonPaths> all = new ArrayList<>(List.of(home));
    for (Path other : others) all.add(DaemonPaths.resolve(other));
    LockFile lock = new LockFile(ProcessHandle.current().pid(), home.libraryConfig.toString(), home.libraryHash,
        all.stream().map(d -> d.libraryConfig.toString()).toList(), List.of(), LockFile.PROTOCOL_VERSION,
        System.currentTimeMillis(), address, LockFile.HOST_CLI);
    for (DaemonPaths d : all) {
      d.ensureArendDir();
      lock.writeAtomic(d.lockFile);
    }
  }

  @Test
  public void aCommandNamingLibrariesOfOneDaemonIsRouted() throws Exception {
    Path other = library("otherlib");
    serveAlso(other);
    List<String> positional = List.of(other.toString(), libRoot.toString());
    OptionalInt rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("both libraries are the daemon's, so the command must route", rc.isPresent());
    assertTrue("the stand-in daemon must have been reached", served.await(10, TimeUnit.SECONDS));
  }

  @Test
  public void aLibraryTheDaemonDoesNotServeIsNotRouted() throws Exception {
    Path other = library("otherlib");
    List<String> positional = List.of(libRoot.toString(), other.toString());
    OptionalInt rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("a library outside the daemon's set must keep the command local", rc.isEmpty());
  }

  private Path sharedLibrary(String name) throws IOException {
    Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    Path root = config.resolve("libs").resolve(name);
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    return root;
  }

  private Path register(long pid, String hash, long startedAt, String socket, Path... shared) throws IOException {
    List<String> sharedConfigs = new ArrayList<>();
    for (Path root : shared) sharedConfigs.add(DaemonPaths.resolve(root).libraryConfig.toString());
    LockFile lock = new LockFile(pid, "/owner-" + hash + "/arend.yaml", hash, List.of("/owner-" + hash + "/arend.yaml"),
        sharedConfigs, LockFile.PROTOCOL_VERSION, startedAt, socket, LockFile.HOST_CLI);
    Files.createDirectories(DaemonPaths.registryDir());
    lock.writeAtomic(lock.registryFile());
    return lock.registryFile();
  }

  @Test
  public void aCommandAboutASharedLibraryGoesToTheFirstDaemonThatLoadedIt() throws Exception {
    Path shared = sharedLibrary("sharedlib");
    long self = ProcessHandle.current().pid();
    Path dead = register(Long.MAX_VALUE, "dead000000000000", 0, address, shared);
    register(self, "first00000000000", 1, address, shared);
    register(self, "second0000000000", 2, "uds:/nonexistent/daemon.sock", shared);
    register(self, "unrelated0000000", 0, "uds:/nonexistent/daemon.sock");

    List<String> positional = List.of(shared.toString(), "Data.Maybe");
    OptionalInt rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("the first live daemon that loaded the library must take the command", rc.isPresent());
    assertTrue("the stand-in daemon must have been reached", served.await(10, TimeUnit.SECONDS));
    assertTrue("a dead daemon's registration must be cleared", !Files.exists(dead));
  }

  /**
   * Named together with an owned library, a shared one has to be loaded by that library's daemon:
   * it is the only daemon the owned one can go to.
   */
  @Test
  public void aSharedLibraryWithAnOwnedOneNeedsTheOwnersDaemonToHaveIt() throws Exception {
    Path shared = sharedLibrary("sharedlib");
    List<String> positional = List.of(libRoot.toString(), shared.toString());
    OptionalInt rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("the owner's daemon has not loaded the shared library, so the command stays local", rc.isEmpty());

    DaemonPaths home = DaemonPaths.resolve(libRoot);
    new LockFile(ProcessHandle.current().pid(), home.libraryConfig.toString(), home.libraryHash,
        List.of(home.libraryConfig.toString()), List.of(DaemonPaths.resolve(shared).libraryConfig.toString()),
        LockFile.PROTOCOL_VERSION, 1L, address, LockFile.HOST_CLI).writeAtomic(home.lockFile);
    rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("once the owner's daemon has it, the command must route", rc.isPresent());
  }
}
