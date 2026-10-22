package org.arend.daemon.server;

import org.arend.daemon.Platform;
import org.arend.daemon.PrivateFiles;

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

/**
 * Binds the daemon's listening socket.
 *
 * <p>A Unix domain socket at {@code <lib>/.arend/daemon.sock}, made the owner's alone -- {@code
 * rw-------}, or on Windows an ACL granting only the owner, which Windows checks on connect as Unix
 * checks the permissions: whoever can connect can run arbitrary CLI commands against a warm library
 * and can shut the daemon down, so the file's access control has to actually be set.
 * {@link org.arend.daemon.DaemonPaths#ensureArendDir} restricts the directory to match.
 *
 * <p>A socket path has a hard length limit the rest of the filesystem does not -- about 104 bytes
 * for the whole {@code sockaddr_un} -- and {@code <lib>/.arend/daemon.sock} passes it for an
 * ordinary checkout only a few directories deep (under a macOS temp directory it is already 107).
 * That must not cost the daemon its access control, so the second attempt is another Unix socket,
 * in a short private directory ({@code $XDG_RUNTIME_DIR}, else an {@code arend-<user>} directory
 * created 700 under the temp dir) named for the library's hash. The lock file records whichever
 * address was bound, so a client follows either way.
 *
 * <p>TCP on an ephemeral loopback port is what is left, and it has no authentication at all: any
 * local process that finds the port can drive this daemon. Where a Unix socket was possible in
 * principle it needs {@code -Darend.daemon.allowTcp=true} -- an explicit choice rather than the
 * silent consequence of a long path. A platform that cannot open a Unix socket at all -- Windows
 * before 10 1803 -- makes no attempt and gets TCP directly.
 */
public final class SocketBinder {
  private SocketBinder() {}

  /** A bound listening socket and the address string recorded in the lock file. */
  public record Bound(ServerSocketChannel channel, String address) {}

  /** Opt-in for the unauthenticated loopback fallback where a Unix socket was possible. */
  public static final String ALLOW_TCP_PROPERTY = "arend.daemon.allowTcp";

  /**
   * Longest {@code sockaddr_un} path worth attempting. The real cap is 104 (macOS) or 108
   * (Linux) including the terminator; 100 is under both, and a stricter platform reports it the
   * same way through the bind itself.
   */
  private static final int MAX_UDS_PATH_BYTES = 100;

  /**
   * @param libraryTag short stable name for this library, used for the relocated socket's
   *                   filename. {@code DaemonPaths.libraryHash} is exactly that, and reusing it
   *                   keeps one digest of the library rather than two that could disagree.
   */
  public static Bound bind(Path udsPath, String libraryTag) throws IOException {
    if (!Platform.supportsUnixSockets()) return bindLoopback(udsPath, null);

    Path abs = udsPath.toAbsolutePath();
    // Probed, not read off an errno string. Something is accepting connections on this library's
    // socket, so the file must not be unlinked -- and binding this daemon at another address
    // instead is not a recovery, it is two daemons for one library with one lock between them.
    if (Files.exists(abs) && isLive(abs)) {
      throw new IOException("a daemon is already listening on " + abs
          + ". Stop it with `arend --daemon-stop` for this library before starting another.");
    }

    Attempt primary = tryBindUds(abs);
    if (primary.bound() != null) return primary.bound();
    String failure = primary.failure();

    // Where the checkout lives is not a reason to give up the permissions that are this daemon's
    // only access control, so try a short path that is still a Unix socket.
    Path shortPath = shortSocketPath(libraryTag);
    if (shortPath != null && !shortPath.equals(abs)) {
      Attempt relocated = tryBindUds(shortPath);
      if (relocated.bound() != null) {
        System.err.println("[INFO] daemon socket is at " + shortPath + " because " + abs
            + " could not be bound (" + failure + ")");
        return relocated.bound();
      }
      failure = failure + "; " + shortPath + ": " + relocated.failure();
    }

    if (!Boolean.getBoolean(ALLOW_TCP_PROPERTY)) {
      throw new IOException("no Unix domain socket could be bound for this daemon (" + failure
          + "). A TCP fallback would listen on loopback with no access control at all -- any"
          + " local process could run commands against the warm library or shut it down -- so it"
          + " is not taken on its own. Move the library somewhere with a shorter path, or pass"
          + " -D" + ALLOW_TCP_PROPERTY + "=true to accept that.");
    }
    return bindLoopback(udsPath, failure);
  }

  /**
   * One bind attempt: the socket, or why there isn't one. Returned rather than stashed in fields,
   * so two attempts in one {@link #bind} cannot read each other's result.
   */
  private record Attempt(Bound bound, String failure) {}

  /** Binds a Unix socket at {@code path}, or reports why it could not. */
  private static Attempt tryBindUds(Path path) {
    Path abs = path.toAbsolutePath();
    if (abs.toString().getBytes(StandardCharsets.UTF_8).length > MAX_UDS_PATH_BYTES) {
      return new Attempt(null, "socket path is too long (" + abs.toString().length() + " chars)");
    }
    // A socket file left by a dead daemon blocks the bind. Nothing answers on it -- the live case
    // is refused above -- so it is safe to unlink.
    try {
      Files.deleteIfExists(abs);
    } catch (IOException ignored) {
      // Fall through: the bind below fails and reports it properly.
    }
    try {
      ServerSocketChannel ch = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      ch.bind(UnixDomainSocketAddress.of(abs));
      restrictToOwner(abs);
      return new Attempt(new Bound(ch, "uds:" + abs), null);
    } catch (IOException e) {
      // Keep the reason: it is what tells the user whether to move the library or stop a daemon.
      return new Attempt(null, e.getMessage() == null ? e.toString() : e.getMessage());
    } catch (UnsupportedOperationException e) {
      return new Attempt(null, "Unix domain sockets are not supported here");
    }
  }

  private static Bound bindLoopback(Path udsPath, String failure) throws IOException {
    ServerSocketChannel ch = ServerSocketChannel.open();
    ch.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    int port = ((InetSocketAddress) ch.getLocalAddress()).getPort();
    System.err.println("[WARN] daemon is listening on TCP 127.0.0.1:" + port
        + (failure == null ? " because Unix domain sockets cannot be opened here" : " because " + udsPath + " could not be bound (" + failure + ")")
        + ". Unlike the socket file, this port has"
        + " no access control: any local process can drive this daemon.");
    return new Bound(ch, "tcp:127.0.0.1:" + port);
  }

  /**
   * A short socket path named for {@code libraryTag}, or null if no private directory could be
   * prepared. {@code $XDG_RUNTIME_DIR} is per-user and 0700 by specification; a temp directory is
   * shared, so the socket goes in an {@code arend-<user>} subdirectory of the user's own, 0700.
   *
   * <p>That name is anybody's to take first. A directory there that another user made is not used:
   * its owner could replace the socket in it with one of their own, and answer the clients that
   * come to it.
   */
  static Path shortSocketPath(String libraryTag) {
    return shortSocketPath(libraryTag, System.getenv("XDG_RUNTIME_DIR"), System.getProperty("java.io.tmpdir", "/tmp"));
  }

  /** As {@link #shortSocketPath(String)}, with the runtime and the temp directory given: a test cannot unset the one it runs with. */
  static Path shortSocketPath(String libraryTag, String runtimeDir, String tmpDir) {
    Path dir;
    if (runtimeDir != null && !runtimeDir.isEmpty() && Files.isDirectory(Path.of(runtimeDir))) {
      dir = Path.of(runtimeDir);
    } else {
      String user = System.getProperty("user.name", "user").replaceAll("[^A-Za-z0-9._-]", "_");
      dir = Path.of(tmpDir).resolve("arend-" + user);
      try {
        PrivateFiles.ensurePrivateDirectory(dir);
      } catch (IOException e) {
        System.err.println("[WARN] not putting the daemon's socket in " + dir + ": " + e.getMessage());
        return null;
      }
      if (!PrivateFiles.isPrivate(dir, true)) {
        System.err.println("[WARN] not putting the daemon's socket in " + dir + ": others can write to it");
        return null;
      }
    }
    return dir.resolve(libraryTag + ".sock");
  }

  /** Whether something is accepting connections on {@code udsPath} right now. */
  private static boolean isLive(Path udsPath) {
    try (SocketChannel probe = SocketChannel.open(UnixDomainSocketAddress.of(udsPath))) {
      return true;
    } catch (IOException | UnsupportedOperationException e) {
      return false;
    }
  }

  /** Makes the socket file the owner's alone: {@code rw-------}, or an ACL granting only the owner. Best effort. */
  private static void restrictToOwner(Path udsPath) {
    PrivateFiles.restrictToOwner(udsPath, false);
  }
}
