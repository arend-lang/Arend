package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.wire.Frame;
import org.arend.frontend.cli.daemon.wire.FrameChannel;
import org.junit.Test;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The wire format the daemon and its clients share: {@code [4-byte BE length][UTF-8 JSON]}.
 *
 * <p>Everything a client sees goes through it, and a desynchronised stream is unrecoverable —
 * the next length prefix is read out of the middle of a body — so the cases worth pinning are
 * the ones that would desynchronise: a truncated frame, a length prefix that is not a length,
 * and two threads writing at once.
 */
public class FrameTest {
  private static Map<String, Object> roundTrip(Map<String, Object> payload) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    new FrameChannel(Channels.newChannel(sink)).write(payload);
    return Frame.read(Channels.newChannel(new ByteArrayInputStream(sink.toByteArray())));
  }

  private static ReadableByteChannel bytes(byte[] data) {
    return Channels.newChannel(new ByteArrayInputStream(data));
  }

  @Test
  public void aFrameSurvivesTheRoundTrip() throws IOException {
    assertEquals(Map.of("id", "abc", "kind", "done", "exitCode", 0),
        roundTrip(Frame.doneFrame("abc", 0)));
    assertEquals(Map.of("id", "abc", "kind", "stdout", "data", "hello\nworld"),
        roundTrip(Frame.stdoutFrame("abc", "hello\nworld")));
  }

  /** The body is UTF-8, and the length prefix counts bytes rather than characters. */
  @Test
  public void nonAsciiSurvivesTheRoundTrip() throws IOException {
    String text = "λ ≡ ∀ — ✗◯";
    assertEquals(text, roundTrip(Frame.stderrFrame("id", text)).get("data"));
  }

  /** Several frames on one stream have to come back one at a time, in order. */
  @Test
  public void framesAreReadBackOneAtATime() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    FrameChannel out = new FrameChannel(Channels.newChannel(sink));
    out.write(Frame.stdoutFrame("id", "one"));
    out.write(Frame.stdoutFrame("id", "two"));
    out.write(Frame.doneFrame("id", 3));

    ReadableByteChannel in = Channels.newChannel(new ByteArrayInputStream(sink.toByteArray()));
    assertEquals("one", Frame.read(in).get("data"));
    assertEquals("two", Frame.read(in).get("data"));
    assertEquals(3, Frame.read(in).get("exitCode"));
    assertNull("a clean close between frames is not an error", Frame.read(in));
  }

  @Test
  public void aCleanEofBetweenFramesReadsAsNull() throws IOException {
    assertNull(Frame.read(bytes(new byte[0])));
  }

  @Test
  public void aTruncatedLengthPrefixIsAnError() {
    assertThrows(EOFException.class, () -> Frame.read(bytes(new byte[] { 0, 0 })));
  }

  @Test
  public void aTruncatedBodyIsAnError() throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    new FrameChannel(Channels.newChannel(sink)).write(Frame.stdoutFrame("id", "a long enough body"));
    byte[] full = sink.toByteArray();
    byte[] cut = new byte[full.length - 3];
    System.arraycopy(full, 0, cut, 0, cut.length);
    assertThrows(EOFException.class, () -> Frame.read(bytes(cut)));
  }

  /** A hostile length prefix must be refused before anything is allocated for it. */
  @Test
  public void anImpossibleLengthPrefixIsRefused() {
    for (int len : new int[] { -1, Integer.MIN_VALUE, 0, Frame.MAX_FRAME_BYTES + 1, Integer.MAX_VALUE }) {
      ByteBuffer prefix = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(len);
      IOException e = assertThrows("length " + len + " must be refused",
          IOException.class, () -> Frame.read(bytes(prefix.array())));
      assertTrue(e.getMessage(), e.getMessage().contains("invalid frame length"));
    }
  }

  @Test
  public void aBodyThatIsNotAJsonObjectIsAnError() throws IOException {
    byte[] body = "[1,2,3]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    ByteBuffer buf = ByteBuffer.allocate(4 + body.length).order(ByteOrder.BIG_ENDIAN);
    buf.putInt(body.length).put(body);
    assertThrows(IOException.class, () -> Frame.read(bytes(buf.array())));
  }

  @Test
  public void aFrameLargerThanTheCapIsRefusedOnWrite() {
    Map<String, Object> huge = Map.of("id", "x", "kind", "stdout",
        "data", "x".repeat(Frame.MAX_FRAME_BYTES + 1));
    IOException e = assertThrows(IOException.class,
        () -> new FrameChannel(Channels.newChannel(new ByteArrayOutputStream())).write(huge));
    assertTrue(e.getMessage(), e.getMessage().contains("frame too large"));
  }

  /**
   * The reason writing lives on {@link FrameChannel} rather than a static: the daemon's worker
   * thread streams output while its per-client thread answers a cancel on the same socket, and a
   * frame spliced into another's body ends the connection's framing for good.
   *
   * <p>The sink here accepts a few bytes per call, which is what a socket whose send buffer is
   * full does. That is the case the lock exists for: {@code SocketChannel.write} is itself
   * atomic, so a frame that fits in one call is safe by accident, and only a frame that takes
   * several can be split.
   */
  @Test
  public void concurrentWritersDoNotSpliceFrames() throws Exception {
    int writers = 8;
    int perWriter = 25;
    ChunkedSink sink = new ChunkedSink();
    FrameChannel out = new FrameChannel(sink);

    // Bodies of very different sizes, so a splice cannot pass as a same-length swap.
    List<Thread> threads = new ArrayList<>();
    CountDownLatch go = new CountDownLatch(1);
    for (int w = 0; w < writers; w++) {
      String data = String.valueOf((char) ('a' + w)).repeat(1 + w * 300);
      Thread t = new Thread(() -> {
        try {
          go.await();
          for (int i = 0; i < perWriter; i++) out.write(Frame.stdoutFrame("id", data));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      });
      t.start();
      threads.add(t);
    }
    go.countDown();
    for (Thread t : threads) t.join(60_000);

    ReadableByteChannel in = bytes(sink.written());
    for (int i = 0; i < writers * perWriter; i++) {
      String data = (String) Frame.read(in).get("data");
      assertEquals("spliced frame body", 1, data.chars().distinct().count());
      assertEquals("truncated or merged frame body", 1 + (data.charAt(0) - 'a') * 300, data.length());
    }
    assertNull("no bytes left over", Frame.read(in));
  }

  /** A sink that accepts at most a few bytes per call, as a socket with a full send buffer does. */
  private static final class ChunkedSink implements WritableByteChannel {
    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();

    @Override
    public int write(ByteBuffer src) {
      int n = Math.min(src.remaining(), 13);
      byte[] chunk = new byte[n];
      src.get(chunk);
      // Deliberately unsynchronised: serialising writes is FrameChannel's job, not the sink's.
      sink.write(chunk, 0, n);
      Thread.yield();
      return n;
    }

    byte[] written() {
      return sink.toByteArray();
    }

    @Override public boolean isOpen() { return true; }
    @Override public void close() {}
  }
}
