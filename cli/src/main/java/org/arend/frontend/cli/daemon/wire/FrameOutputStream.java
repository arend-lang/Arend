package org.arend.frontend.cli.daemon.wire;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * An {@link OutputStream} that writes what it is given as wire frames: up to its last {@code \n},
 * and what is left at a {@link #flush()} -- which an autoflush {@link PrintStream} does after every
 * write, so that a progress counter rewritten with {@code \r} reaches the client live. A frame is
 * split when it would be too long, and a character never: its bytes wait until they are all here.
 */
public final class FrameOutputStream extends OutputStream {
  private final FrameChannel client;
  private final String requestId;
  private final boolean stdout;
  private byte[] pending = new byte[256];
  private int size;

  /** Writes to {@code client} as frames of {@code requestId}: stdout ones if {@code stdout}, else stderr ones. */
  public FrameOutputStream(FrameChannel client, String requestId, boolean stdout) {
    this.client = client;
    this.requestId = requestId;
    this.stdout = stdout;
  }

  @Override
  public synchronized void write(int b) throws IOException {
    writeBytes(new byte[] { (byte) b }, 0, 1);
  }

  @Override
  public synchronized void write(byte[] b, int off, int len) throws IOException {
    writeBytes(b, off, len);
  }

  private void writeBytes(byte[] b, int off, int len) throws IOException {
    if (size + len > pending.length) pending = Arrays.copyOf(pending, Math.max(2 * pending.length, size + len));
    System.arraycopy(b, off, pending, size, len);
    size += len;
    // A \n byte is never part of a longer UTF-8 sequence, so up to the last one is whole characters,
    // sent as one frame however many lines it holds.
    int end = size;
    while (end > size - len && pending[end - 1] != '\n') end--;
    if (end > size - len) {
      sendFrame(new String(pending, 0, end, StandardCharsets.UTF_8));
      drop(end);
    }
  }

  /** Sends what is pending up to its last whole character. */
  @Override
  public synchronized void flush() throws IOException {
    int end = size;
    // Back over the bytes of a character not yet complete: a lead byte whose sequence runs past the end.
    for (int i = size - 1; i >= Math.max(0, size - 3); i--) {
      int b = pending[i] & 0xFF;
      if (b < 0x80) break;
      if (b >= 0xC0) {
        int length = b >= 0xF0 ? 4 : b >= 0xE0 ? 3 : 2;
        if (i + length > size) end = i;
        break;
      }
    }
    if (end > 0) sendFrame(new String(pending, 0, end, StandardCharsets.UTF_8));
    drop(end);
  }

  private void drop(int count) {
    System.arraycopy(pending, count, pending, 0, size - count);
    size -= count;
  }

  /**
   * Chars per frame. A frame body is JSON, where one char can reach six bytes
   * (as a JSON numeric escape) before UTF-8 even applies, so the budget is the frame limit
   * divided by eight -- which leaves room for the envelope as well.
   */
  private static final int MAX_CHUNK_CHARS = FrameChannel.MAX_FRAME_BYTES / 8;

  /**
   * Sends {@code data}, split across frames if it is too long for one. The client appends every
   * {@code data} verbatim, so a frame is a piece of the stream rather than a line of it --
   * whereas an oversize body raises an IOException into a {@link PrintStream}, which files it
   * in its trouble flag and tells nobody, so the output would simply disappear.
   */
  private void sendFrame(String data) throws IOException {
    int start = 0;
    do {
      int end = Math.min(start + MAX_CHUNK_CHARS, data.length());
      // Never split a surrogate pair: encoded separately, the halves arrive as replacements.
      if (end < data.length() && Character.isHighSurrogate(data.charAt(end - 1))) end--;
      String chunk = data.substring(start, end);
      client.write(stdout ? FrameChannel.stdoutFrame(requestId, chunk) : FrameChannel.stderrFrame(requestId, chunk));
      start = end;
    } while (start < data.length());
  }
}
