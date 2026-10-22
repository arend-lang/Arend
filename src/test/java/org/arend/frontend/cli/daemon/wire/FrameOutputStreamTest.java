package org.arend.frontend.cli.daemon.wire;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class FrameOutputStreamTest {
  /** What the frames a stream sent carry, in order. */
  private static String received(ByteArrayOutputStream wire) throws IOException {
    ReadableByteChannel in = Channels.newChannel(new ByteArrayInputStream(wire.toByteArray()));
    StringBuilder data = new StringBuilder();
    for (Map<String, Object> frame; (frame = FrameChannel.read(in)) != null; ) data.append(frame.get("data"));
    return data.toString();
  }

  /** A character written a byte at a time, with a flush after each, still arrives whole. */
  @Test
  public void aCharacterIsNeverSplitAcrossFrames() throws IOException {
    ByteArrayOutputStream wire = new ByteArrayOutputStream();
    FrameOutputStream stream = new FrameOutputStream(new FrameChannel(Channels.newChannel(wire)), "1", true);
    String text = "α → ∀x.\n[1/2]\r[2/2] 𝔸";
    for (byte b : text.getBytes(StandardCharsets.UTF_8)) {
      stream.write(b);
      stream.flush();
    }
    assertEquals(text, received(wire));
  }
}
