package org.arend.daemon.client;

import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.PrivateFiles;
import org.arend.daemon.server.SocketBinder;
import org.arend.daemon.wire.FrameChannel;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * What the client puts on the wire when it routes an ordinary command, and when it declines to.
 * A stand-in daemon answers, because the point is the contents of the request and a real daemon
 * would need a warm library to produce one.
 */
public class DaemonRouteTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final String TOKEN = LockFile.newToken();

  private Path config;
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
        org.arend.daemon.Platform.supportsUnixSockets());

    // The registry is the user's own; a test must not write to the real one.
    config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    libRoot = tempFolder.newFolder("mylib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    DaemonPaths paths = DaemonPaths.resolve(libRoot);
    assertNotNull(paths);
    paths.ensureArendDir();

    SocketBinder.Bound bound = SocketBinder.bind(paths.socketFile, paths.libraryHash);
    listener = bound.channel();
    address = bound.address();
    lockOf(paths, List.of(paths), List.of()).writeEverywhere();

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

  /** The lock this process would write as a daemon owning {@code owned} and having loaded {@code shared}. */
  private LockFile lockOf(DaemonPaths home, List<DaemonPaths> owned, List<String> shared) {
    return LockFile.ofThisProcess(home, owned.stream().map(d -> d.libraryConfig.toString()).toList(), shared,
        System.currentTimeMillis(), address, LockFile.HOST_CLI, TOKEN);
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
    assertEquals("the request must present the daemon's token", TOKEN, req.get("token"));
  }

  /**
   * A library's directory may be anybody's, and so may a lock in it: one the registry does not
   * confirm points the client nowhere, whatever address and process it names.
   */
  @Test
  public void aLockTheRegistryDoesNotConfirmIsNotRouted() throws Exception {
    DaemonPaths paths = DaemonPaths.resolve(libRoot);
    Files.delete(lockOf(paths, List.of(paths), List.of()).registryFile());
    List<String> positional = List.of(libRoot.toString(), "Data.Maybe");
    assertTrue("a lock with no registry entry behind it must keep the command local",
        DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of()).isEmpty());
    assertTrue(LockFile.lookup(paths) instanceof LockFile.Read.Unregistered);
  }

  /** The registry's entries carry tokens: one that others may read or replace is not trusted. */
  @Test
  public void aRegistryEntryOthersCanReadIsNotTrusted() throws Exception {
    DaemonPaths paths = DaemonPaths.resolve(libRoot);
    Path entry = lockOf(paths, List.of(paths), List.of()).registryFile();
    if (Files.getFileAttributeView(entry, java.nio.file.attribute.PosixFileAttributeView.class) != null) {
      Files.setPosixFilePermissions(entry, java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--"));
    } else {
      // Windows: an ACL that lets Everyone read it too. The name is a localized one; skip where it is not this.
      java.nio.file.attribute.UserPrincipal everyone;
      try {
        everyone = entry.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("Everyone");
      } catch (java.nio.file.attribute.UserPrincipalNotFoundException e) {
        org.junit.Assume.assumeNoException(e);
        return;
      }
      java.nio.file.attribute.AclFileAttributeView view = Files.getFileAttributeView(entry, java.nio.file.attribute.AclFileAttributeView.class);
      List<java.nio.file.attribute.AclEntry> acl = new java.util.ArrayList<>(view.getAcl());
      acl.add(java.nio.file.attribute.AclEntry.newBuilder().setType(java.nio.file.attribute.AclEntryType.ALLOW).setPrincipal(everyone)
          .setPermissions(java.nio.file.attribute.AclEntryPermission.READ_DATA).build());
      view.setAcl(acl);
    }
    List<String> positional = List.of(libRoot.toString(), "Data.Maybe");
    assertTrue(DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of()).isEmpty());
    assertTrue(LockFile.registered().isEmpty());
  }

  /**
   * A positional that exists on disk and is not this daemon's library must run locally: the daemon
   * holds no such library and would fail {@code arend ../other.zip}, while the same command with
   * --no-daemon loads and checks the zip.
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
    for (DaemonPaths d : all) d.ensureArendDir();
    lockOf(home, all, List.of()).writeEverywhere();
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
    Path root = config.resolve("libs").resolve(name);
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    return root;
  }

  private Path register(long pid, String hash, long startedAt, String socket, Path... shared) throws IOException {
    List<String> sharedConfigs = new ArrayList<>();
    for (Path root : shared) sharedConfigs.add(DaemonPaths.resolve(root).libraryConfig.toString());
    LockFile lock = new LockFile(pid, "/owner-" + hash + "/arend.yaml", hash, List.of("/owner-" + hash + "/arend.yaml"),
        sharedConfigs, LockFile.PROTOCOL_VERSION, startedAt, socket, LockFile.HOST_CLI, LockFile.UNKNOWN_START, TOKEN, null, "");
    PrivateFiles.ensurePrivateDirectory(DaemonPaths.registryDir());
    lock.writeAtomic(lock.registryFile());
    return lock.registryFile();
  }

  @Test
  public void aCommandAboutASharedLibraryGoesToTheFirstDaemonThatLoadedIt() throws Exception {
    Path shared = sharedLibrary("sharedlib");
    long self = ProcessHandle.current().pid();
    // setUp registered this process as the daemon of libRoot, started now: these are older.
    Files.delete(lockOf(DaemonPaths.resolve(libRoot), List.of(), List.of()).registryFile());
    Path dead = register(Long.MAX_VALUE, "dead000000000000", 0, address, shared);
    register(self, "f1000000000000aa", 1, address, shared);
    register(self, "f2000000000000aa", 2, "uds:/nonexistent/daemon.sock", shared);
    register(self, "f3000000000000aa", 0, "uds:/nonexistent/daemon.sock");

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
    lockOf(home, List.of(home), List.of(DaemonPaths.resolve(shared).libraryConfig.toString())).writeEverywhere();
    rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("once the owner's daemon has it, the command must route", rc.isPresent());
  }

  /**
   * A dependency the daemon was not given is loaded all the same: a command about it goes to that
   * daemon while it has no daemon of its own, and to its own once it has one.
   */
  @Test
  public void aDependencyTheDaemonLoadedWithoutOwningItIsRoutedToIt() throws Exception {
    Path dependency = library("deplib");
    DaemonPaths home = DaemonPaths.resolve(libRoot);
    DaemonPaths dep = DaemonPaths.resolve(dependency);
    lockOf(home, List.of(home), List.of(dep.libraryConfig.toString())).writeEverywhere();
    assertTrue(LockFile.lookup(dep) instanceof LockFile.Read.Absent);

    List<String> positional = List.of(dependency.toString(), "Data.Maybe");
    OptionalInt rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("the daemon holds the dependency, so the command must route to it", rc.isPresent());
    assertTrue("the stand-in daemon must have been reached", served.await(10, TimeUnit.SECONDS));

    // Its own daemon -- here one that cannot be reached -- takes it from then on.
    dep.ensureArendDir();
    LockFile.ofThisProcess(dep, List.of(dep.libraryConfig.toString()), List.of(), System.currentTimeMillis(),
        "uds:/nonexistent/daemon.sock", LockFile.HOST_CLI, TOKEN).writeEverywhere();
    assertEquals(dep.libraryConfig.toString(), DaemonRpc.daemonFor(List.of(dep)).libraryPath());
  }

  /**
   * A command naming no library, issued from a directory that is none, goes to the daemon holding
   * the project there -- and to none when two do, rather than to either.
   */
  @Test
  public void theDaemonHoldingTheProjectOfADirectoryIsFoundThere() throws Exception {
    Path project = tempFolder.newFolder("project").toPath().toRealPath();
    assertNull(DaemonRpc.projectDaemon(project, false));
    DaemonPaths home = DaemonPaths.resolve(libRoot);
    LockFile lock = lockOf(home, List.of(home), List.of()).withProjectDir(project);
    lock.writeEverywhere();
    assertEquals(lock, DaemonRpc.projectDaemon(project, false));
    assertNull("a directory below the project is not the project", DaemonRpc.projectDaemon(
        Files.createDirectory(project.resolve("sub")), false));

    DaemonPaths other = DaemonPaths.resolve(library("otherlib"));
    other.ensureArendDir();
    lockOf(other, List.of(other), List.of()).withProjectDir(project).writeEverywhere();
    assertNull("two daemons of one project: neither", DaemonRpc.projectDaemon(project, false));
  }
}
