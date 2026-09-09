package org.arend.frontend.cli.daemon.wire;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;

/**
 * Length-prefixed JSON framing for daemon I/O.
 *
 * <p>Wire format: {@code [4-byte BE length][N bytes UTF-8 JSON]}. Both sides may write at any
 * time: the daemon streams output frames while a request is in flight, and the client may send
 * a {@code cancel} on the same connection meanwhile. Frames therefore carry the {@code id} of
 * the request they belong to, and a reader must ignore the ones addressed to something else.
 *
 * <p>JSON shape is a plain {@code Map<String,Object>} — no POJO marshalling:
 * <pre>
 *   Request:  {"id":"uuid","op":"cli|ping|status|shutdown|cancel", "targetId":"uuid"?}
 *   Response: {"id":"uuid","kind":"stdout|stderr|state|done|error", ...}
 * </pre>
 *
 * <p>Writing is not here: a frame must reach the socket whole, and two threads calling a static
 * write on one channel cannot guarantee that. {@link FrameChannel} owns the channel and its
 * lock, and is the only way to write one.
 */
public final class Frame {
  /**
   * Largest frame this will read or write. A length prefix is the first thing an unauthenticated
   * peer controls, so it is bounded before anything is allocated for it; a megabyte is already
   * far past any legitimate frame, and the margin above that is for a pathological diagnostic.
   */
  public static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;

  static final ObjectMapper MAPPER = new ObjectMapper();

  private Frame() {}

  /**
   * Reads one frame. Returns null on a clean EOF before the length prefix (the peer closed
   * between frames); throws on a truncated frame or malformed JSON.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> read(ReadableByteChannel ch) throws IOException {
    ByteBuffer lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
    if (!readFully(ch, lenBuf, true)) return null; // clean EOF
    lenBuf.flip();
    int len = lenBuf.getInt();
    if (len <= 0 || len > MAX_FRAME_BYTES) {
      throw new IOException("invalid frame length: " + len);
    }
    byte[] bytes = new byte[len];
    if (!readFully(ch, ByteBuffer.wrap(bytes), false)) {
      throw new EOFException("truncated frame body (expected " + len + " bytes)");
    }
    Object parsed = MAPPER.readValue(bytes, Object.class);
    if (!(parsed instanceof Map)) {
      throw new IOException("frame body is not a JSON object");
    }
    return (Map<String, Object>) parsed;
  }

  /**
   * @param allowEofAtStart if true, return false on EOF before any bytes are read
   *                        (clean close); if false, throw on any EOF.
   */
  private static boolean readFully(ReadableByteChannel ch, ByteBuffer buf, boolean allowEofAtStart) throws IOException {
    boolean read = false;
    while (buf.hasRemaining()) {
      int n = ch.read(buf);
      if (n < 0) {
        if (allowEofAtStart && !read) return false;
        throw new EOFException("unexpected EOF (got " + buf.position() + " of " + buf.capacity() + " bytes)");
      }
      if (n > 0) read = true;
    }
    return true;
  }

  /** Convenience: render a UTF-8 string in a {@code kind:stdout/stderr} frame body. */
  public static Map<String, Object> stdoutFrame(String requestId, String data) {
    return Map.of("id", requestId, "kind", "stdout", "data", data);
  }

  public static Map<String, Object> stderrFrame(String requestId, String data) {
    return Map.of("id", requestId, "kind", "stderr", "data", data);
  }

  public static Map<String, Object> stateFrame(String requestId, String state) {
    return Map.of("id", requestId, "kind", "state", "state", state);
  }

  public static Map<String, Object> doneFrame(String requestId, int exitCode) {
    return Map.of("id", requestId, "kind", "done", "exitCode", exitCode);
  }

  public static Map<String, Object> errorFrame(String requestId, String message) {
    return Map.of("id", requestId, "kind", "error", "message", message);
  }
}
