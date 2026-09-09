package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.client.DaemonRpc;
import org.arend.frontend.cli.daemon.server.SocketBinder;
import org.arend.frontend.cli.daemon.wire.Frame;
import org.arend.frontend.cli.daemon.wire.FrameChannel;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * What the client actually puts on the wire when it routes an ordinary command to a daemon.
 *
 * <p>A stand-in daemon is used rather than a real one: the point is the contents of the request,
 * and a real daemon would need a warm library to answer it.
 */
public class DaemonRouteTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private ServerSocketChannel listener;
  private Thread acceptor;

  @After
  public void tearDown() throws IOException {
    if (listener != null) listener.close();
    if (acceptor != null) acceptor.interrupt();
  }

  /** Answers exactly one request and records it. */
  private AtomicReference<Map<String, Object>> serveOnce(CountDownLatch served) {
    AtomicReference<Map<String, Object>> seen = new AtomicReference<>();
    acceptor = new Thread(() -> {
      try (SocketChannel ch = listener.accept()) {
        FrameChannel out = new FrameChannel(ch);
        Map<String, Object> req = Frame.read(ch);
        seen.set(req);
        out.write(Frame.doneFrame(String.valueOf(req.get("id")), 0));
        served.countDown();
      } catch (IOException ignored) {
        served.countDown();
      }
    }, "stand-in-daemon");
    acceptor.setDaemon(true);
    acceptor.start();
    return seen;
  }

  /**
   * A relative path means the directory the user typed the command in. The daemon's own working
   * directory is whatever shell started it and never changes, so unless the client says where it
   * was, the same command writes its output somewhere else the moment a daemon exists.
   */
  @Test
  public void aRoutedCommandCarriesTheClientsWorkingDirectory() throws Exception {
    assumeTrue("Unix domain sockets required",
        !System.getProperty("os.name", "").toLowerCase().startsWith("windows"));

    Path libRoot = tempFolder.newFolder("mylib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    DaemonPaths paths = DaemonPaths.resolve(libRoot);
    assertNotNull(paths);
    paths.ensureArendDir();

    // The address actually bound, not the one asked for. A socket path has a length limit the
    // rest of the filesystem does not, and a temp directory is long enough to reach it, so
    // SocketBinder legitimately answers on another address -- and a lock naming the path that
    // could not be bound points the client at nothing, which looks exactly like "not routed".
    SocketBinder.Bound bound = SocketBinder.bind(paths.socketFile);
    listener = bound.channel();
    new LockFile(ProcessHandle.current().pid(), paths.libraryConfig.toString(), paths.libraryHash,
        LockFile.PROTOCOL_VERSION, System.currentTimeMillis(),
        bound.address()).writeAtomic(paths.lockFile);

    CountDownLatch served = new CountDownLatch(1);
    AtomicReference<Map<String, Object>> seen = serveOnce(served);

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
   *
   * <p>{@code CliSetup.populateRequestedTargets} skips a positional that exists as a path, taking
   * it for the library already loaded. Route such a command and the daemon checks its own
   * library instead, finds nothing wrong with it, and exits 0 -- so `arend ../other.zip` reports
   * success without ever looking at the zip, while the same command with --no-daemon loads and
   * checks it. Answering a different question is worse than answering slowly.
   */
  @Test
  public void aPositionalTheDaemonWouldDropIsNotRouted() throws Exception {
    assumeTrue("Unix domain sockets required",
        !System.getProperty("os.name", "").toLowerCase().startsWith("windows"));

    Path libRoot = tempFolder.newFolder("mylib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    DaemonPaths paths = DaemonPaths.resolve(libRoot);
    assertNotNull(paths);
    paths.ensureArendDir();

    SocketBinder.Bound bound = SocketBinder.bind(paths.socketFile);
    listener = bound.channel();
    new LockFile(ProcessHandle.current().pid(), paths.libraryConfig.toString(), paths.libraryHash,
        LockFile.PROTOCOL_VERSION, System.currentTimeMillis(),
        bound.address()).writeAtomic(paths.lockFile);

    // An ordinary file that is not a library and not this daemon's business.
    Path stray = tempFolder.newFile("stray.zip").toPath();
    List<String> positional = List.of(libRoot.toString(), stray.toString());
    String[] argv = positional.toArray(new String[0]);

    OptionalInt rc = DaemonRpc.tryRouteCli(argv, positional, List.of());
    assertTrue("a command carrying a positional the daemon would drop must run locally",
        rc.isEmpty());
  }

  /** The same daemon still serves a module name, which it resolves exactly as a local run would. */
  @Test
  public void aModuleNamePositionalIsStillRouted() throws Exception {
    assumeTrue("Unix domain sockets required",
        !System.getProperty("os.name", "").toLowerCase().startsWith("windows"));

    Path libRoot = tempFolder.newFolder("mylib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    DaemonPaths paths = DaemonPaths.resolve(libRoot);
    assertNotNull(paths);
    paths.ensureArendDir();

    SocketBinder.Bound bound = SocketBinder.bind(paths.socketFile);
    listener = bound.channel();
    new LockFile(ProcessHandle.current().pid(), paths.libraryConfig.toString(), paths.libraryHash,
        LockFile.PROTOCOL_VERSION, System.currentTimeMillis(),
        bound.address()).writeAtomic(paths.lockFile);

    CountDownLatch served = new CountDownLatch(1);
    serveOnce(served);

    List<String> positional = List.of(libRoot.toString(), "Data.Maybe");
    OptionalInt rc = DaemonRpc.tryRouteCli(positional.toArray(new String[0]), positional, List.of());
    assertTrue("a module name is not a path and must still route", rc.isPresent());
    assertTrue("the stand-in daemon must have been reached", served.await(10, TimeUnit.SECONDS));
  }
}
