package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.server.SocketBinder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The socket is the daemon's whole attack surface: whoever reaches it runs CLI commands against a
 * warm library, so its permissions have to be set, and a socket a live daemon is answering on
 * must never be taken away from it.
 */
public class SocketBinderTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static boolean udsSupported() {
    return !System.getProperty("os.name", "").toLowerCase().startsWith("windows");
  }

  private Path socketPath() throws IOException {
    return tempFolder.newFolder("arend").toPath().resolve("daemon.sock");
  }

  @Test
  public void aBoundSocketIsPrivateToItsOwner() throws IOException {
    assumeTrue(udsSupported());
    Path sock = socketPath();
    try (ServerSocketChannel ch = SocketBinder.bind(sock, "0123456789abcdef").channel()) {
      assertNotNull(ch);
      assumeTrue(Files.getFileStore(sock).supportsFileAttributeView("posix"));
      assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(sock)));
    }
  }

  /** A socket file left by a dead daemon must not stop the next one from starting. */
  @Test
  public void aStaleSocketFileIsReplaced() throws IOException {
    assumeTrue(udsSupported());
    Path sock = socketPath();
    SocketBinder.bind(sock, "0123456789abcdef").channel().close();  // leaves the file, nothing listening
    assertTrue(Files.exists(sock));
    try (ServerSocketChannel ch = SocketBinder.bind(sock, "0123456789abcdef").channel()) {
      assertTrue(ch.isOpen());
    }
  }

  /**
   * A socket a live daemon is answering on is neither unlinked nor bound around. Unlinking would
   * leave that daemon running and unreachable, with clients connecting to whatever binds the name
   * next; relocating this one instead would make two daemons for one library with one lock
   * between them. The refusal has to name the cause, since that is what tells the user to stop
   * the other daemon rather than move the library.
   */
  @Test
  public void aLiveSocketIsRefusedRatherThanTakenOver() throws IOException {
    assumeTrue(udsSupported());
    Path sock = socketPath();
    try (ServerSocketChannel live = SocketBinder.bind(sock, "0123456789abcdef").channel()) {
      IOException e = assertThrows(IOException.class,
          () -> SocketBinder.bind(sock, "0123456789abcdef"));
      assertTrue(e.getMessage(), e.getMessage().contains("already listening"));
      assertTrue("the running daemon's socket must survive a second bind attempt", live.isOpen());
      assertTrue("and its socket file must still be there", Files.exists(sock));
    }
  }

  /**
   * A path past the sockaddr_un cap relocates to a short private directory rather than giving up
   * the file permissions that are the daemon's only access control. Every canonical macOS temp
   * path is already past it, so this is an ordinary case and not a broken one.
   */
  @Test
  public void aPathTooLongForASocketRelocatesAndStaysAUnixSocket() throws IOException {
    assumeTrue(udsSupported());
    Path deep = tempFolder.newFolder("arend").toPath();
    for (int i = 0; i < 8; i++) deep = deep.resolve("a-directory-with-a-long-name");
    Files.createDirectories(deep);
    Path sock = deep.resolve("daemon.sock");
    assertTrue("fixture must exceed the cap: " + sock.toString().length(),
        sock.toString().length() > 108);

    SocketBinder.Bound bound = SocketBinder.bind(sock, "0123456789abcdef");
    try {
      assertTrue("relocation must still be a Unix socket, not a TCP port: " + bound.address(),
          bound.address().startsWith("uds:"));
      assertEquals("0123456789abcdef.sock",
          Path.of(bound.address().substring(4)).getFileName().toString());
    } finally {
      bound.channel().close();
    }
  }
}
