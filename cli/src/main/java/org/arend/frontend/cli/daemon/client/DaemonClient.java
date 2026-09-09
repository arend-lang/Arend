package org.arend.frontend.cli.daemon.client;

import org.arend.frontend.cli.daemon.wire.FrameChannel;
import org.arend.frontend.cli.daemon.wire.SocketAddress;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client-side RPC: open a socket to a running daemon and exchange framed JSON.
 *
 * <pre>
 *   try (DaemonClient c = DaemonClient.connect(SocketAddress.parse(addr))) {
 *     int rc = c.invoke("ping", Map.of(), frame -&gt; ...);   // returns the daemon's exit code
 *   }
 * </pre>
 *
 * <p>{@link #invoke} sends one request and reads frames until the {@code done} <em>for that
 * request</em>; the others go to {@code onFrame}, which relays {@code stdout}/{@code stderr} to
 * the terminal as they arrive.
 *
 * <p>The connection is not one request at a time: {@link #sendCancel} writes while {@code invoke}
 * is blocked reading, and the daemon answers it on the same socket. Both directions therefore go
 * through one {@link FrameChannel}, and every frame is matched against the id of the request that
 * is waiting for it.
 */
public final class DaemonClient implements AutoCloseable {
  private final SocketChannel socket;
  private final FrameChannel channel;

  private DaemonClient(SocketChannel socket) {
    this.socket = socket;
    this.channel = new FrameChannel(socket);
  }

  public static DaemonClient connect(SocketAddress addr) throws IOException {
    SocketChannel ch;
    if (addr instanceof SocketAddress.Uds uds) {
      ch = SocketChannel.open(StandardProtocolFamily.UNIX);
      ch.connect(UnixDomainSocketAddress.of(uds.path()));
    } else if (addr instanceof SocketAddress.Tcp tcp) {
      ch = SocketChannel.open(new InetSocketAddress(tcp.host(), tcp.port()));
    } else {
      throw new IOException("unknown address kind: " + addr);
    }
    return new DaemonClient(ch);
  }

  /** Told whether the daemon accepted a {@code cancel} this client sent for its own request. */
  public interface CancelAckListener {
    void acknowledged(boolean accepted);
  }

  /**
   * Sends one request under a fresh id, relays its frames to {@code onFrame} (may be null), and
   * returns the exit code from its {@code done}.
   */
  public int invoke(String op, Map<String, Object> extra, Consumer<Map<String, Object>> onFrame)
      throws IOException {
    return invoke(UUID.randomUUID().toString(), op, extra, onFrame, null);
  }

  /**
   * As {@link #invoke(String, Map, Consumer)}, but the caller picks the request id up front --
   * needed when something has to name the in-flight request before the reads start, such as a
   * shutdown hook sending a {@code cancel} targeting it -- and is told, through
   * {@code onCancelAck}, whether the daemon accepted such a cancel.
   */
  public int invoke(String id, String op, Map<String, Object> extra,
                    Consumer<Map<String, Object>> onFrame,
                    CancelAckListener onCancelAck) throws IOException {
    Map<String, Object> req = new HashMap<>(extra);
    req.put("id", id);
    req.put("op", op);
    channel.write(req);

    while (true) {
      Map<String, Object> resp = FrameChannel.read(socket);
      if (resp == null) {
        throw new IOException("daemon closed the connection before sending done");
      }
      // Ahead of the id filter, which would drop it: the ack is addressed to the cancel's own id,
      // so a caller waiting to hear that its cancel landed would instead wait out the whole run.
      // targetId names the request, which is this one.
      if (onCancelAck != null && "cancel-ack".equals(resp.get("kind"))
          && id.equals(resp.get("targetId"))) {
        onCancelAck.acknowledged(Boolean.TRUE.equals(resp.get("accepted")));
        continue;
      }
      // Frames for another request share this socket -- the done for a cancel we sent most of
      // all, which carries its own id and its own exit code. Returning that as this request's
      // result reports a rejected cancel as exit 1 and an accepted one as exit 0, in place of
      // whatever the command actually did.
      if (!id.equals(resp.get("id"))) continue;
      if ("done".equals(resp.get("kind"))) {
        Object exitCode = resp.get("exitCode");
        return exitCode instanceof Number n ? n.intValue() : 1;
      }
      if (onFrame != null) onFrame.accept(resp);
    }
  }

  /**
   * Asks the daemon to abandon the request {@code targetId}. Fire-and-forget: the caller is
   * usually a shutdown hook with no time to wait, and the ack comes back on the same socket,
   * where {@code invoke} recognises it by {@code targetId}.
   */
  public void sendCancel(String targetId) throws IOException {
    Map<String, Object> req = new HashMap<>();
    req.put("id", UUID.randomUUID().toString());
    req.put("op", "cancel");
    req.put("targetId", targetId);
    channel.write(req);
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
