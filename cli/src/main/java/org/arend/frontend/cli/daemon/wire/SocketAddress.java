package org.arend.frontend.cli.daemon.wire;

import java.nio.file.Path;

/**
 * The address a daemon listens on, as recorded in its lock file: {@code uds:<path>} or
 * {@code tcp:<host>:<port>}. Written by the server that bound it, read by every client.
 */
public sealed interface SocketAddress {
  record Uds(Path path) implements SocketAddress {}
  record Tcp(String host, int port) implements SocketAddress {}

  /**
   * Parses a lock file's {@code socketPath} back into something a client can connect to.
   *
   * @throws IllegalArgumentException on anything malformed -- callers recover by ignoring the
   *         lock, and every rejection has to arrive as the same exception for that to work.
   */
  static SocketAddress parse(String encoded) {
    if (encoded.startsWith("uds:")) {
      String path = encoded.substring(4);
      if (path.isEmpty()) throw new IllegalArgumentException("socket address has no path: " + encoded);
      return new Uds(Path.of(path));
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
      return new Tcp(hostPort.substring(0, colon), port);
    }
    throw new IllegalArgumentException("unrecognised socket address: " + encoded);
  }
}
