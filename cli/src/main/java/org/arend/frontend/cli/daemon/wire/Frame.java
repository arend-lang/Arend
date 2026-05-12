package org.arend.frontend.cli.daemon.wire;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

/**
 * Length-prefixed JSON framing for daemon I/O.
 *
 * <p>Wire format: {@code [4-byte BE length][N bytes UTF-8 JSON]}. Either side may emit
 * an arbitrary number of frames over a single socket; the connection is half-duplex per
 * request (client sends one Request frame, daemon sends one-or-more Response frames
 * terminated by a {@code kind:done}).
 *
 * <p>JSON shape is a plain {@code Map<String,Object>} — no POJO marshalling. M3 fields:
 * <pre>
 *   Request:  {"id":"uuid","op":"ping|status|shutdown|cancel", "targetId":"uuid"?}
 *   Response: {"id":"uuid","kind":"stdout|stderr|state|done", ...}
 * </pre>
 *
 * <p>The {@code MAX_FRAME_BYTES} cap is defensive against a buggy/hostile peer sending
 * an absurd length; legitimate Arend payloads are well under 1 MiB.
 */
public final class Frame {
  public static final int MAX_FRAME_BYTES = 16 * 1024 * 1024; // 16 MiB

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Frame() {}

  /** Write one frame: length prefix + UTF-8 JSON body. */
  public static void write(WritableByteChannel ch, Map<String, Object> payload) throws IOException {
    byte[] body = MAPPER.writeValueAsBytes(payload);
    if (body.length > MAX_FRAME_BYTES) {
      throw new IOException("frame too large: " + body.length + " bytes (max " + MAX_FRAME_BYTES + ")");
    }
    ByteBuffer buf = ByteBuffer.allocate(4 + body.length).order(ByteOrder.BIG_ENDIAN);
    buf.putInt(body.length);
    buf.put(body);
    buf.flip();
    while (buf.hasRemaining()) {
      ch.write(buf);
    }
  }

  /**
   * Read one frame. Returns null on a clean EOF before the length prefix (peer closed
   * between frames); throws on a truncated frame or malformed JSON.
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> read(ReadableByteChannel ch) throws IOException {
    ByteBuffer lenBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
    if (!readFully(ch, lenBuf, true)) return null; // clean EOF
    lenBuf.flip();
    int len = lenBuf.getInt();
    if (len < 0 || len > MAX_FRAME_BYTES) {
      throw new IOException("invalid frame length: " + len);
    }
    ByteBuffer body = ByteBuffer.allocate(len);
    if (!readFully(ch, body, false)) {
      throw new EOFException("truncated frame body (expected " + len + " bytes)");
    }
    body.flip();
    byte[] bytes = new byte[len];
    body.get(bytes);
    return MAPPER.readValue(bytes, Map.class);
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
