package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.server.StreamRedirector;
import org.arend.frontend.cli.daemon.wire.FrameChannel;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The per-thread stdout/stderr shim, driven the way the worker drives it. */
public class StreamRedirectorTest {
  /** Everything the shim emitted for {@code body}, in order. */
  private static List<Map<String, Object>> framesFrom(Runnable body) throws IOException {
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    StreamRedirector.install();
    try {
      StreamRedirector.attach(new FrameChannel(Channels.newChannel(sink)), "req-1");
      try {
        body.run();
      } finally {
        StreamRedirector.detach();
      }
    } finally {
      StreamRedirector.uninstall();
    }

    List<Map<String, Object>> frames = new ArrayList<>();
    ReadableByteChannel in = Channels.newChannel(new ByteArrayInputStream(sink.toByteArray()));
    Map<String, Object> frame;
    while ((frame = FrameChannel.read(in)) != null) frames.add(frame);
    return frames;
  }

  private static String dataOf(List<Map<String, Object>> frames, String kind) {
    StringBuilder sb = new StringBuilder();
    for (Map<String, Object> f : frames) {
      assertEquals("req-1", f.get("id"));
      if (kind.equals(f.get("kind"))) sb.append(String.valueOf(f.get("data")));
    }
    return sb.toString();
  }

  /** A command's output reaches the client as frames, and the two streams stay apart. */
  @Test
  public void stdoutAndStderrAreStreamedSeparately() throws IOException {
    List<Map<String, Object>> frames = framesFrom(() -> {
      System.out.println("out line");
      System.err.println("err line");
    });
    assertEquals("out line\n", dataOf(frames, "stdout"));
    assertEquals("err line\n", dataOf(frames, "stderr"));
  }

  /**
   * A trailing line with no newline -- a progress fragment, an interrupted stack trace -- is held
   * in the buffer, so it has to be flushed on detach or the last thing a cancelled run said is
   * dropped.
   */
  @Test
  public void detachFlushesAnUnterminatedLine() throws IOException {
    List<Map<String, Object>> frames = framesFrom(() -> System.err.print("no newline here"));
    assertEquals("no newline here", dataOf(frames, "stderr"));
  }

  /**
   * A line too long for one frame must still arrive. {@link FrameChannel#write} refuses an
   * oversize body, and the shim is reached through a PrintStream, which swallows IOException into
   * its trouble flag -- so the whole line vanished with no diagnostic, and only when routed
   * through a daemon.
   */
  @Test
  public void aLineTooLongForOneFrameIsSplitRatherThanDropped() throws IOException {
    String line = "x".repeat(FrameChannel.MAX_FRAME_BYTES + 1024);
    List<Map<String, Object>> frames = framesFrom(() -> System.out.println(line));
    assertTrue("an oversize line must be split across frames, not sent as one", frames.size() > 1);
    assertEquals("every byte of the line must arrive", line + "\n", dataOf(frames, "stdout"));
  }
}
