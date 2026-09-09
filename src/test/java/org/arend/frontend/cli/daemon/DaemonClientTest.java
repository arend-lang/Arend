package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.client.DaemonClient;
import org.arend.frontend.cli.daemon.server.SocketBinder;
import org.arend.frontend.cli.daemon.wire.FrameChannel;
import org.arend.frontend.cli.daemon.wire.SocketAddress;
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
  private SocketAddress address;
  private Thread peer;

  @Before
  public void setUp() throws IOException {
    Path sock = tempFolder.newFolder("arend").toPath().resolve("daemon.sock");
    SocketBinder.Bound bound = SocketBinder.bind(sock, "0123456789abcdef");
    listener = bound.channel();
    address = SocketAddress.parse(bound.address());
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
        Map<String, Object> request = FrameChannel.read(conn);
        script.accept(new PeerSession(request, new FrameChannel(conn)));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    peer.start();
  }

  private record PeerSession(Map<String, Object> request, FrameChannel out) {}

  /**
   * A cancel-ack for the in-flight request must reach the caller. It is addressed to the cancel's
   * own id, so the read loop's id filter would drop it and a caller waiting to hear that its
   * cancel landed would wait out the whole run; {@code targetId} is what makes it recognisable.
   */
  @Test
  public void aCancelAckForThisRequestReachesTheCaller() throws IOException {
    CountDownLatch acked = new CountDownLatch(1);
    List<Boolean> verdicts = new ArrayList<>();

    servePeer(s -> {
      try {
        String requestId = (String) s.request().get("id");
        // The ack the daemon really sends: its own fresh id, plus the target it abandons.
        Map<String, Object> ack = new java.util.HashMap<>();
        ack.put("id", "a-quite-different-id");
        ack.put("kind", "cancel-ack");
        ack.put("targetId", requestId);
        ack.put("accepted", true);
        s.out().write(ack);
        // The done for the cancel, under the cancel's id -- must not end the request either.
        s.out().write(FrameChannel.doneFrame("a-quite-different-id", 0));
        assertTrue("the ack must have been seen before the request finished",
            awaitQuietly(acked));
        s.out().write(FrameChannel.doneFrame(requestId, 3));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    try (DaemonClient client = DaemonClient.connect(address)) {
      int rc = client.invoke("req-1", "cli", Map.of(), null, accepted -> {
        verdicts.add(accepted);
        acked.countDown();
      });
      assertEquals("the request's own done still decides the exit code", 3, rc);
    }
    assertEquals("the ack must be reported exactly once", List.of(true), verdicts);
  }

  private static boolean awaitQuietly(CountDownLatch latch) {
    try {
      return latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Test
  public void outputFramesAreRelayedInOrderBeforeDone() throws IOException {
    servePeer(s -> {
      try {
        String id = (String) s.request().get("id");
        s.out().write(FrameChannel.stdoutFrame(id, "one"));
        s.out().write(FrameChannel.stderrFrame(id, "two"));
        s.out().write(FrameChannel.doneFrame(id, 0));
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
   * Without the id filter, invoke returns a foreign done's exit code -- 1 for a rejected cancel,
   * 0 for an accepted one -- in place of the command's own.
   */
  @Test
  public void framesForAnotherRequestAreIgnored() throws IOException {
    servePeer(s -> {
      try {
        String id = (String) s.request().get("id");
        s.out().write(FrameChannel.doneFrame("some-other-request", 1));
        s.out().write(FrameChannel.stdoutFrame("some-other-request", "not mine"));
        s.out().write(FrameChannel.stdoutFrame(id, "mine"));
        s.out().write(FrameChannel.doneFrame(id, 0));
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
        Map<String, Object> request = FrameChannel.read(conn);
        String id = (String) request.get("id");
        requestSeen.countDown();
        Map<String, Object> cancel = FrameChannel.read(conn);
        assertEquals("cancel", cancel.get("op"));
        assertEquals(id, cancel.get("targetId"));
        // The daemon answers a cancel under the cancel's own id, not the request's.
        out.write(FrameChannel.doneFrame((String) cancel.get("id"), 0));
        cancelSeen.countDown();
        out.write(FrameChannel.doneFrame(id, 130));
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
      assertEquals(130, client.invoke(id, "cli", Map.of(), null, null));
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
