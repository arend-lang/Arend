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

    listener = SocketBinder.bind(paths.socketFile).channel();
    new LockFile(ProcessHandle.current().pid(), paths.libraryConfig.toString(), paths.libraryHash,
        LockFile.PROTOCOL_VERSION, System.currentTimeMillis(),
        "uds:" + paths.socketFile.toAbsolutePath()).writeAtomic(paths.lockFile);

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
}
