package org.arend.frontend.cli.daemon.server;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;

/**
 * Binds the daemon's listening socket.
 *
 * <p>A Unix domain socket at {@code <lib>/.arend/daemon.sock} where the platform has them, with
 * the socket file created {@code rw-------}: whoever can connect can run arbitrary CLI commands
 * against a warm library and can shut the daemon down, so the filesystem permissions are the
 * access control and have to actually be set. {@link
 * org.arend.frontend.cli.daemon.DaemonPaths#ensureArendDir} restricts the directory to match.
 *
 * <p>On Windows, or if the UDS bind fails, it falls back to TCP on an ephemeral loopback port.
 * That fallback has <em>no</em> authentication: any local process that finds the port can drive
 * the daemon. It is loopback-only and last-resort, and {@link #bind} says so on stderr rather
 * than leaving the difference undocumented at the point where it matters.
 */
public final class SocketBinder {
  private SocketBinder() {}

  /** A bound listening socket and the address string recorded in the lock file. */
  public record Bound(ServerSocketChannel channel, String address) {}

  /** Parsed form of a lock file's {@code socketPath}. */
  public sealed interface Address {
    record Uds(Path path) implements Address {}
    record Tcp(String host, int port) implements Address {}
  }

  public static Bound bind(Path udsPath) throws IOException {
    if (!isWindows()) {
      // A socket file left by a dead daemon blocks the bind, but one belonging to a live daemon
      // must not be unlinked -- that would leave it running and unreachable, with clients
      // connecting to whatever binds the name next. Only remove it if nothing answers.
      if (Files.exists(udsPath) && !isLive(udsPath)) {
        try {
          Files.deleteIfExists(udsPath);
        } catch (IOException ignored) {
          // Fall through: the bind below will fail and we drop to TCP.
        }
      }
      try {
        ServerSocketChannel ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        ch.bind(UnixDomainSocketAddress.of(udsPath));
        restrictToOwner(udsPath);
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
    System.err.println("[WARN] daemon is listening on TCP 127.0.0.1:" + port
        + " because no Unix domain socket could be bound. Unlike the socket file, this port has"
        + " no access control: any local process can drive this daemon.");
    return new Bound(ch, "tcp:127.0.0.1:" + port);
  }

  /** Whether something is accepting connections on {@code udsPath} right now. */
  private static boolean isLive(Path udsPath) {
    try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(udsPath))) {
      return true;
    } catch (IOException | UnsupportedOperationException e) {
      return false;
    }
  }

  /**
   * Sets the socket file to {@code rw-------}. Best-effort: a filesystem with no POSIX
   * permissions cannot express it, and there the platform's own ACLs apply.
   */
  private static void restrictToOwner(Path udsPath) {
    try {
      Files.setPosixFilePermissions(udsPath, PosixFilePermissions.fromString("rw-------"));
    } catch (IOException | UnsupportedOperationException ignored) {
    }
  }

  /**
   * Parses a lock file's {@code socketPath} back into something a client can connect to.
   *
   * @throws IllegalArgumentException on anything malformed -- callers recover by ignoring the
   *         lock, and every rejection has to arrive as the same exception for that to work.
   */
  public static Address parseAddress(String encoded) {
    if (encoded.startsWith("uds:")) {
      String path = encoded.substring(4);
      if (path.isEmpty()) throw new IllegalArgumentException("socket address has no path: " + encoded);
      return new Address.Uds(Path.of(path));
    }
    if (encoded.startsWith("tcp:")) {
      String hostPort = encoded.substring(4);
      int colon = hostPort.lastIndexOf(':');
      if (colon <= 0 || colon == hostPort.length() - 1) {
        throw new IllegalArgumentException("socket address is not host:port: " + encoded);
      }
      int port;
      try {
        port = Integer.parseInt(hostPort.substring(colon + 1));
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("socket address has a non-numeric port: " + encoded);
      }
      if (port < 1 || port > 65535) {
        throw new IllegalArgumentException("socket address has an out-of-range port: " + encoded);
      }
      return new Address.Tcp(hostPort.substring(0, colon), port);
    }
    throw new IllegalArgumentException("unrecognised socket address: " + encoded);
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
  }
}
