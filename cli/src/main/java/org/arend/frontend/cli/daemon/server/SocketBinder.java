package org.arend.frontend.cli.daemon.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Binds the daemon's listening socket. Prefers a Unix domain socket at
 * {@code <lib>/.arend/daemon.sock} (filesystem permissions = built-in auth). On Windows,
 * or if the UDS bind fails (rare; usually a stale socket file we can't remove), falls
 * back to TCP bound to 127.0.0.1 on an ephemeral port.
 *
 * <p>Both kinds expose the same {@link ServerSocketChannel} API, so the rest of the
 * daemon doesn't have to branch on transport.
 *
 * <p>The encoded {@link Bound#address} string is what goes into the lock file's
 * {@code socketPath} field; the client mirrors {@link #parseAddress} to decode it.
 */
public final class SocketBinder {

  /** Result of a successful bind. */
  public record Bound(ServerSocketChannel channel, String address) {}

  private SocketBinder() {}

  /**
   * Bind the daemon socket. UDS is tried first; if it fails (Windows / stale path that
   * can't be unlinked), falls back to TCP-localhost.
   */
  public static Bound bind(Path udsPath) throws IOException {
    if (!isWindows()) {
      // Stale socket files block bind even though the owning process is gone. Best-effort
      // remove first; if anything goes wrong we fall through to TCP.
      try {
        Files.deleteIfExists(udsPath);
      } catch (IOException ignored) {
      }
      try {
        ServerSocketChannel ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        ch.bind(UnixDomainSocketAddress.of(udsPath));
        return new Bound(ch, "uds:" + udsPath.toAbsolutePath());
      } catch (IOException e) {
        // fall through to TCP
      } catch (UnsupportedOperationException e) {
        // UNIX domain sockets not supported on this JVM/OS; fall through.
      }
    }
    ServerSocketChannel ch = ServerSocketChannel.open();
    ch.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    int port = ((InetSocketAddress) ch.getLocalAddress()).getPort();
    return new Bound(ch, "tcp:127.0.0.1:" + port);
  }

  /** Parse a lock-file {@code socketPath} value back into something the client can connect to. */
  public sealed interface Address {
    record Uds(Path path) implements Address {}
    record Tcp(String host, int port) implements Address {}
  }

  public static Address parseAddress(String encoded) {
    if (encoded.startsWith("uds:")) {
      return new Address.Uds(Path.of(encoded.substring(4)));
    }
    if (encoded.startsWith("tcp:")) {
      int colon = encoded.lastIndexOf(':');
      String host = encoded.substring(4, colon);
      int port = Integer.parseInt(encoded.substring(colon + 1));
      return new Address.Tcp(host, port);
    }
    throw new IllegalArgumentException("unrecognised socket address: " + encoded);
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }
}
