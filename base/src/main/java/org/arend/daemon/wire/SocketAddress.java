package org.arend.daemon.wire;

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
      String host = hostPort.substring(0, colon);
      // A daemon only ever binds the loopback interface; a lock naming anything else did not come from one.
      if (!isLoopback(host)) {
        throw new IllegalArgumentException("socket address is not on this machine: " + encoded);
      }
      return new Tcp(host, port);
    }
    throw new IllegalArgumentException("unrecognised socket address: " + encoded);
  }

  /** Whether {@code host} is a loopback address written out: {@code 127.x.x.x}, {@code ::1} or {@code localhost}. No lookup. */
  static boolean isLoopback(String host) {
    if (host.equals("localhost") || host.equals("::1") || host.equals("[::1]")) return true;
    String[] parts = host.split("\\.", -1);
    if (parts.length != 4 || !parts[0].equals("127")) return false;
    for (String part : parts) {
      if (part.isEmpty() || part.length() > 3 || !part.chars().allMatch(Character::isDigit) || Integer.parseInt(part) > 255) return false;
    }
    return true;
  }
}
