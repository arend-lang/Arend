package org.arend.frontend.cli.daemon.server;

import org.arend.frontend.cli.daemon.Platform;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Binds the daemon's listening socket.
 *
 * <p>A Unix domain socket at {@code <lib>/.arend/daemon.sock} where the platform has them, with
 * the socket file created {@code rw-------}: whoever can connect can run arbitrary CLI commands
 * against a warm library and can shut the daemon down, so the filesystem permissions are the
 * access control and have to actually be set. {@link
 * org.arend.frontend.cli.daemon.DaemonPaths#ensureArendDir} restricts the directory to match.
 *
 * <p>A socket path has a hard length limit the rest of the filesystem does not -- 104 bytes on
 * macOS, 108 on Linux, for the whole {@code sockaddr_un} -- and {@code <lib>/.arend/daemon.sock}
 * passes it for a checkout only a few directories deep. That is an ordinary path, not a broken
 * one, so it must not cost the daemon its access control: the second attempt is another Unix
 * socket, under a short per-user runtime directory ({@code $XDG_RUNTIME_DIR}, else a private
 * subdirectory of the temp dir), named for a hash of the library. The lock file records whichever
 * address was bound, so a client finds it either way.
 *
 * <p>TCP on an ephemeral loopback port is the last resort, and it is <em>not</em> automatic on a
 * platform that has Unix sockets. That fallback has no authentication at all: any local process
 * that finds the port can run CLI commands against a warm library or shut it down, which on a
 * shared or CI host is every other user. Reaching it where UDS exists but could not be bound
 * needs {@code -Darend.daemon.allowTcp=true} -- an explicit choice, rather than the silent
 * consequence of a long path. Windows has no UDS attempt here and still gets TCP directly.
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

  /** Opt-in for the unauthenticated loopback fallback where a Unix socket was possible. */
  public static final String ALLOW_TCP_PROPERTY = "arend.daemon.allowTcp";

  /**
   * Longest {@code sockaddr_un} path worth attempting. The real cap is 104 (macOS) or 108
   * (Linux) including the terminator; 100 is under both and the failure is reported the same way
   * by the bind itself if a platform is stricter still.
   */
  private static final int MAX_UDS_PATH_BYTES = 100;

  public static Bound bind(Path udsPath) throws IOException {
    String udsFailure = null;
    boolean relocatable = false;
    boolean udsPossible = Platform.supportsUnixSockets();
    if (udsPossible) {
      Attempt primary = tryBindUds(udsPath);
      if (primary.bound() != null) return primary.bound();
      udsFailure = primary.failure();

      // Only when the address itself is the problem -- too long for a sockaddr_un, or on a
      // filesystem that will not hold a socket. "Address already in use" must NOT come here:
      // something is answering on this library's socket, and binding a second daemon for it at
      // a different address is not a recovery, it is two daemons for one library with one lock
      // between them. That case keeps the old behaviour and drops to loopback.
      relocatable = primary.reason() != UdsFailure.IN_USE;
      if (relocatable) {
        // Where the checkout lives is not a reason to give up the permissions that are this
        // daemon's only access control, so try a short path that is still a Unix socket.
        Path shortPath = shortSocketPath(udsPath);
        if (shortPath != null && !shortPath.equals(udsPath)) {
          Attempt fallback = tryBindUds(shortPath);
          if (fallback.bound() != null) {
            System.err.println("[INFO] daemon socket is at " + shortPath + " because " + udsPath
                + " could not be bound (" + udsFailure + ")");
            return fallback.bound();
          }
          udsFailure = udsFailure + "; " + shortPath + ": " + fallback.failure();
        }
      }
    }

    // Unauthenticated loopback. Refused only where a Unix socket was possible in principle and a
    // secure alternative had already been tried and failed -- that is the path-length case, the
    // one that would otherwise cost a perfectly ordinary checkout its access control for free.
    if (udsPossible && relocatable && !Boolean.getBoolean(ALLOW_TCP_PROPERTY)) {
      throw new IOException("no Unix domain socket could be bound for this daemon (" + udsFailure
          + "). A TCP fallback would listen on loopback with no access control at all -- any"
          + " local process could run commands against the warm library or shut it down -- so it"
          + " is not taken on its own. Move the library somewhere with a shorter path, or pass"
          + " -D" + ALLOW_TCP_PROPERTY + "=true to accept that.");
    }

    ServerSocketChannel ch = ServerSocketChannel.open();
    ch.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    int port = ((InetSocketAddress) ch.getLocalAddress()).getPort();
    System.err.println("[WARN] daemon is listening on TCP 127.0.0.1:" + port
        + " because " + udsPath + " could not be bound"
        + (udsFailure == null ? "" : " (" + udsFailure + ")")
        + ". Unlike the socket file, this port has"
        + " no access control: any local process can drive this daemon.");
    return new Bound(ch, "tcp:127.0.0.1:" + port);
  }

  /** What kind of thing stopped a Unix socket bind. */
  private enum UdsFailure { TOO_LONG, IN_USE, OTHER }

  /**
   * One bind attempt: the socket, or why there isn't one.
   *
   * <p>Returned rather than stashed in fields, so two attempts in one {@link #bind} cannot read
   * each other's reason.
   */
  private record Attempt(Bound bound, String failure, UdsFailure reason) {
    static Attempt ok(Bound bound) { return new Attempt(bound, null, null); }
    static Attempt failed(String failure, UdsFailure reason) {
      return new Attempt(null, failure, reason);
    }
  }

  /** Binds a Unix socket at {@code path}, or reports why it could not. */
  private static Attempt tryBindUds(Path path) {
    Path abs = path.toAbsolutePath();
    if (abs.toString().getBytes(StandardCharsets.UTF_8).length > MAX_UDS_PATH_BYTES) {
      return Attempt.failed("socket path is too long (" + abs.toString().length() + " chars)",
          UdsFailure.TOO_LONG);
    }
    if (Files.exists(abs) && isLive(abs)) {
      // Determined by probe, not by parsing an errno message: something is accepting
      // connections on this name right now.
      return Attempt.failed("Address already in use", UdsFailure.IN_USE);
    }
    // A socket file left by a dead daemon blocks the bind. Nothing answers on it -- the live
    // case returned above -- so it is safe to unlink.
    if (Files.exists(abs)) {
      try {
        Files.deleteIfExists(abs);
      } catch (IOException ignored) {
        // Fall through: the bind below fails and the caller moves on.
      }
    }
    try {
      ServerSocketChannel ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      ch.bind(UnixDomainSocketAddress.of(abs));
      restrictToOwner(abs);
      return Attempt.ok(new Bound(ch, "uds:" + abs));
    } catch (IOException e) {
      // Keep the reason: it is what tells the user whether to move the library or stop a daemon.
      String failure = e.getMessage() == null ? e.toString() : e.getMessage();
      return Attempt.failed(failure,
          failure.contains("in use") ? UdsFailure.IN_USE : UdsFailure.OTHER);
    } catch (UnsupportedOperationException e) {
      return Attempt.failed("Unix domain sockets are not supported here", UdsFailure.OTHER);
    }
  }

  /**
   * A short socket path for the library whose state directory holds {@code udsPath}, or null if
   * no private directory could be prepared.
   *
   * <p>{@code $XDG_RUNTIME_DIR} is per-user and 0700 by specification, so it is used as given.
   * A temp directory is shared, so the socket goes in an {@code arend-<user>} subdirectory
   * created 0700 -- the directory permissions are what stands in for authentication, and a
   * socket sitting directly in a world-writable {@code /tmp} would not have them.
   */
  static Path shortSocketPath(Path udsPath) {
    String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
    Path dir;
    if (runtimeDir != null && !runtimeDir.isEmpty() && Files.isDirectory(Path.of(runtimeDir))) {
      dir = Path.of(runtimeDir);
    } else {
      String user = System.getProperty("user.name", "user").replaceAll("[^A-Za-z0-9._-]", "_");
      dir = Path.of(System.getProperty("java.io.tmpdir", "/tmp")).resolve("arend-" + user);
      try {
        if (!Files.isDirectory(dir)) {
          Files.createDirectories(dir);
        }
        Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString("rwx------"));
      } catch (IOException | UnsupportedOperationException e) {
        if (!Files.isDirectory(dir)) return null;
        // An existing directory whose permissions cannot be set is still usable; the socket's own
        // rw------- remains.
      }
    }
    return dir.resolve(pathTag(udsPath) + ".sock");
  }

  /** A short, stable, collision-resistant name for the library at {@code udsPath}. */
  private static String pathTag(Path udsPath) {
    String key = udsPath.toAbsolutePath().normalize().toString();
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256")
          .digest(key.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(12);
      for (int i = 0; i < 6; i++) sb.append(String.format("%02x", digest[i]));
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      return Integer.toHexString(key.hashCode());
    }
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

}
