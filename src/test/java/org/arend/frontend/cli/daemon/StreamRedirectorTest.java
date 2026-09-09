package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.server.StreamRedirector;
import org.arend.frontend.cli.daemon.wire.Frame;
import org.arend.frontend.cli.daemon.wire.FrameChannel;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The per-thread stdout/stderr shim: what it replaces, and what it can put back. */
public class StreamRedirectorTest {
  /**
   * {@code install()} is a process-wide swap. In the daemon it lasts as long as the JVM, which is
   * why it was written without a way back; anywhere else -- a test, or anything embedding the CLI
   * -- the streams have to be returnable, or everything downstream keeps writing through a shim
   * belonging to a server that has since been closed.
   */
  @Test
  public void uninstallPutsTheRealStreamsBack() {
    PrintStream realOut = System.out;
    PrintStream realErr = System.err;
    try {
      StreamRedirector.install();
      assertNotSame("install must actually replace stdout", realOut, System.out);
      assertNotSame("install must actually replace stderr", realErr, System.err);
      StreamRedirector.uninstall();
      assertSame("uninstall must restore stdout", realOut, System.out);
      assertSame("uninstall must restore stderr", realErr, System.err);
    } finally {
      StreamRedirector.uninstall();
      System.setOut(realOut);
      System.setErr(realErr);
    }
  }

  /**
   * A line too long for one frame must still arrive. {@link FrameChannel#write} refuses an
   * oversize body, and the shim is reached through a {@link PrintStream}, which swallows
   * IOException into its trouble flag -- so the whole line vanished with no diagnostic, and only
   * when routed through a daemon.
   */
  @Test
  public void aLineTooLongForOneFrameIsSplitRatherThanDropped() throws IOException {
    String line = "x".repeat(Frame.MAX_FRAME_BYTES + 1024);
    ByteArrayOutputStream sink = new ByteArrayOutputStream();
    StreamRedirector.FrameOutputStream out =
        StreamRedirector.attach(new FrameChannel(Channels.newChannel(sink)), "req-1");
    try {
      out.write((line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    } finally {
      StreamRedirector.detach();
    }

    List<Map<String, Object>> frames = new ArrayList<>();
    ReadableByteChannel in = Channels.newChannel(new ByteArrayInputStream(sink.toByteArray()));
    Map<String, Object> frame;
    while ((frame = Frame.read(in)) != null) frames.add(frame);

    assertTrue("an oversize line must be split across frames, not sent as one", frames.size() > 1);
    StringBuilder reassembled = new StringBuilder();
    for (Map<String, Object> f : frames) {
      assertEquals("req-1", f.get("id"));
      assertEquals("stdout", f.get("kind"));
      reassembled.append(String.valueOf(f.get("data")));
    }
    assertEquals("every byte of the line must arrive", line + "\n", reassembled.toString());
  }
}
