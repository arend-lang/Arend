package org.arend.frontend.cli.daemon.wire;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Map;

/**
 * The write end of one daemon connection: everything that may put a frame on a socket goes
 * through an instance of this.
 *
 * <p>A frame is a length prefix followed by exactly that many bytes, and a partial write splices
 * the next writer's bytes into the middle of it — after which the peer's framing is lost for the
 * life of the connection. That is reachable on any ordinary Ctrl-C: the daemon's worker thread
 * streams output frames while its per-client thread answers the cancel that arrives on the same
 * socket.
 *
 * <p>So the channel and the lock that protects it are one object, and {@link #write} is the only
 * way in. There is no static write for a call site to reach for by mistake, which is the point:
 * the previous arrangement was correct in three places and wrong in nine, and nothing about the
 * API said which was which.
 *
 * <p>Reading is deliberately not serialised. One connection has one reader by construction, and
 * a lock shared with the writer would deadlock the moment a reader blocked.
 */
public final class FrameChannel {
  private final WritableByteChannel channel;
  private final Object writeLock = new Object();

  public FrameChannel(WritableByteChannel channel) {
    this.channel = channel;
  }

  /** The underlying channel, for the single reader. */
  public ReadableByteChannel readEnd() {
    return (ReadableByteChannel) channel;
  }

  /** Writes one frame: length prefix + UTF-8 JSON body, atomically with respect to other writers. */
  public void write(Map<String, Object> payload) throws IOException {
    byte[] body = Frame.MAPPER.writeValueAsBytes(payload);
    if (body.length > Frame.MAX_FRAME_BYTES) {
      throw new IOException("frame too large: " + body.length + " bytes (max " + Frame.MAX_FRAME_BYTES + ")");
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
}
