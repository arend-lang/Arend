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
 * Length-prefixed JSON framing for daemon I/O: {@code [4-byte BE length][N bytes UTF-8 JSON]}.
 *
 * <p>Both sides may write at any time -- the daemon streams output frames while a request is in
 * flight, and the client may send a {@code cancel} on the same connection meanwhile -- so a frame
 * carries the {@code id} of the request it belongs to and a reader must ignore the rest:
 * <pre>
 *   Request:  {"id":"uuid","op":"cli|ping|status|shutdown|cancel", "targetId":"uuid"?}
 *   Response: {"id":"uuid","kind":"stdout|stderr|state|done|error", ...}
 * </pre>
 *
 * <p>An instance owns one channel and the lock that serialises writes to it, and {@link #write}
 * is the only way to put a frame on it. A frame must reach the socket whole:
 * {@code SocketChannel.write} is atomic per call but returns short once the send buffer fills, so
 * a frame that takes more than one call can have another thread's bytes spliced into it -- after
 * which the peer's framing is lost for the life of the connection.
 *
 * <p>Reading is static and deliberately not serialised: a connection has one reader by
 * construction, and sharing the writer's lock would deadlock as soon as it blocked.
 */
public final class FrameChannel {
  /**
   * Largest frame this will read or write. A length prefix is the first thing an unauthenticated
   * peer controls, so it is bounded before anything is allocated for it.
   */
  public static final int MAX_FRAME_BYTES = 4 * 1024 * 1024;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WritableByteChannel channel;
  private final Object writeLock = new Object();

  public FrameChannel(WritableByteChannel channel) {
    this.channel = channel;
  }

  /** Writes one frame, atomically with respect to other writers. */
  public void write(Map<String, Object> payload) throws IOException {
    byte[] body = MAPPER.writeValueAsBytes(payload);
    if (body.length > MAX_FRAME_BYTES) {
      throw new IOException("frame too large: " + body.length + " bytes (max " + MAX_FRAME_BYTES + ")");
    }
    ByteBuffer buf = ByteBuffer.allocate(4 + body.length).order(ByteOrder.BIG_ENDIAN);
    buf.putInt(body.length);
    buf.put(body);
    buf.flip();
    synchronized (writeLock) {
      while (buf.hasRemaining()) {
        channel.write(buf);
      }
    }
  }

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

  // ───────── frame bodies ─────────

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
