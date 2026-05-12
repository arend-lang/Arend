package org.arend.frontend.cli.daemon.client;

import org.arend.frontend.cli.daemon.server.SocketBinder;
import org.arend.frontend.cli.daemon.wire.Frame;

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
 * <p>Usage pattern (M3):
 * <pre>
 *   try (DaemonClient c = DaemonClient.connect(SocketBinder.parseAddress(addr))) {
 *     int rc = c.invoke("ping", Map.of(), frame -> ...);   // returns daemon exitCode
 *   }
 * </pre>
 *
 * <p>{@link #invoke} sends one Request frame and reads Response frames until it sees
 * {@code kind:done}. Non-done frames are passed to the caller-supplied {@code onFrame}
 * consumer, which relays {@code kind:stdout/stderr} live to the terminal.
 */
public final class DaemonClient implements AutoCloseable {
  private final SocketChannel channel;

  private DaemonClient(SocketChannel channel) {
    this.channel = channel;
  }

  public static DaemonClient connect(SocketBinder.Address addr) throws IOException {
    SocketChannel ch;
    if (addr instanceof SocketBinder.Address.Uds uds) {
      ch = SocketChannel.open(StandardProtocolFamily.UNIX);
      ch.connect(UnixDomainSocketAddress.of(uds.path()));
    } else if (addr instanceof SocketBinder.Address.Tcp tcp) {
      ch = SocketChannel.open(new InetSocketAddress(tcp.host(), tcp.port()));
    } else {
      throw new IOException("unknown address kind: " + addr);
    }
    return new DaemonClient(ch);
  }

  /**
   * Send one Request, drain all Response frames up to and including {@code done}, and
   * return the {@code exitCode}. Non-done frames are passed to {@code onFrame} (may be
   * null to discard).
   */
  public int invoke(String op, Map<String, Object> extra, Consumer<Map<String, Object>> onFrame)
      throws IOException {
    String id = UUID.randomUUID().toString();
    Map<String, Object> req = new HashMap<>(extra);
    req.put("id", id);
    req.put("op", op);
    Frame.write(channel, req);

    while (true) {
      Map<String, Object> resp = Frame.read(channel);
      if (resp == null) {
        throw new IOException("daemon closed the connection before sending done");
      }
      Object kind = resp.get("kind");
      if ("done".equals(kind)) {
        Object ec = resp.get("exitCode");
        return ec instanceof Number n ? n.intValue() : 1;
      }
      if (onFrame != null) onFrame.accept(resp);
    }
  }

  @Override
  public void close() throws IOException {
    channel.close();
  }
}
