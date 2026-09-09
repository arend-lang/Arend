package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.client.DaemonClient;
import org.arend.frontend.cli.daemon.server.SocketBinder;
import org.arend.frontend.cli.daemon.wire.Frame;
import org.arend.frontend.cli.daemon.wire.FrameChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * {@link DaemonClient} against a hand-written peer on a real socket. The peer is scripted here
 * rather than being a DaemonServer, so a test can send exactly the frame sequence under
 * examination -- including ones a correct server would not send.
 */
public class DaemonClientTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private ServerSocketChannel listener;
  private SocketBinder.Address address;
  private Thread peer;

  @Before
  public void setUp() throws IOException {
    Path sock = tempFolder.newFolder("arend").toPath().resolve("daemon.sock");
    SocketBinder.Bound bound = SocketBinder.bind(sock);
    listener = bound.channel();
    address = SocketBinder.parseAddress(bound.address());
  }

  @After
  public void tearDown() throws Exception {
    if (peer != null) peer.join(10_000);
    listener.close();
  }

  /** Accepts one connection, reads the request, and replies with whatever {@code script} says. */
  private void servePeer(Consumer<PeerSession> script) {
    peer = new Thread(() -> {
      try (SocketChannel conn = listener.accept()) {
        Map<String, Object> request = Frame.read(conn);
        script.accept(new PeerSession(request, new FrameChannel(conn)));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    peer.start();
  }

  private record PeerSession(Map<String, Object> request, FrameChannel out) {}

  @Test
  public void invokeReturnsTheExitCodeFromDone() throws IOException {
    servePeer(s -> {
      try {
        s.out().write(Frame.doneFrame((String) s.request().get("id"), 7));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    try (DaemonClient client = DaemonClient.connect(address)) {
      assertEquals(7, client.invoke("ping", Map.of(), null));
    }
  }

  @Test
  public void outputFramesAreRelayedInOrderBeforeDone() throws IOException {
    servePeer(s -> {
      try {
        String id = (String) s.request().get("id");
        s.out().write(Frame.stdoutFrame(id, "one"));
        s.out().write(Frame.stderrFrame(id, "two"));
        s.out().write(Frame.doneFrame(id, 0));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    List<String> seen = new ArrayList<>();
    try (DaemonClient client = DaemonClient.connect(address)) {
      assertEquals(0, client.invoke("cli", Map.of(), f -> seen.add(f.get("kind") + ":" + f.get("data"))));
    }
    assertEquals(List.of("stdout:one", "stderr:two"), seen);
  }

  /**
   * The bug this filter exists for. A cancel sent from a shutdown hook carries a fresh id, and
   * the daemon acks it on the same socket; without the check, invoke returns that ack's exit
   * code -- 1 for a rejected cancel, 0 for an accepted one -- in place of the command's own.
   */
  @Test
  public void framesForAnotherRequestAreIgnored() throws IOException {
    servePeer(s -> {
      try {
        String id = (String) s.request().get("id");
        s.out().write(Frame.doneFrame("some-other-request", 1));
        s.out().write(Frame.stdoutFrame("some-other-request", "not mine"));
        s.out().write(Frame.stdoutFrame(id, "mine"));
        s.out().write(Frame.doneFrame(id, 0));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    List<String> seen = new ArrayList<>();
    try (DaemonClient client = DaemonClient.connect(address)) {
      assertEquals("the foreign done must not be taken as this request's result",
          0, client.invoke("cli", Map.of(), f -> seen.add((String) f.get("data"))));
    }
    assertEquals(List.of("mine"), seen);
  }

  /** A cancel goes out while invoke is blocked reading, and its ack does not disturb the result. */
  @Test
  public void aCancelInFlightDoesNotDisturbTheRequest() throws Exception {
    CountDownLatch cancelSeen = new CountDownLatch(1);
    // The cancel must go out *after* the request, or the peer reads them in the other order and
    // the test is asserting on a sequence that never happens in practice.
    CountDownLatch requestSeen = new CountDownLatch(1);
    peer = new Thread(() -> {
      try (SocketChannel conn = listener.accept()) {
        FrameChannel out = new FrameChannel(conn);
        Map<String, Object> request = Frame.read(conn);
        String id = (String) request.get("id");
        requestSeen.countDown();
        Map<String, Object> cancel = Frame.read(conn);
        assertEquals("cancel", cancel.get("op"));
        assertEquals(id, cancel.get("targetId"));
        // The daemon answers a cancel under the cancel's own id, not the request's.
        out.write(Frame.doneFrame((String) cancel.get("id"), 0));
        cancelSeen.countDown();
        out.write(Frame.doneFrame(id, 130));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    peer.start();

    try (DaemonClient client = DaemonClient.connect(address)) {
      String id = "the-request";
      Thread canceller = new Thread(() -> {
        try {
          assertTrue(requestSeen.await(10, TimeUnit.SECONDS));
          client.sendCancel(id);
        } catch (IOException e) {
          throw new RuntimeException(e);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      canceller.start();
      assertEquals(130, client.invoke(id, "cli", Map.of(), null));
      canceller.join(10_000);
      assertTrue(cancelSeen.await(10, TimeUnit.SECONDS));
    }
  }

  @Test
  public void aPeerThatHangsUpBeforeDoneIsAnError() throws IOException {
    servePeer(s -> { /* close without answering */ });
    try (DaemonClient client = DaemonClient.connect(address)) {
      IOException e = assertThrows(IOException.class, () -> client.invoke("ping", Map.of(), null));
      assertTrue(e.getMessage(), e.getMessage().contains("before sending done"));
    }
  }
}
