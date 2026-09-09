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
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The socket is the daemon's whole attack surface: whoever reaches it runs CLI commands against
 * a warm library. And the address string is round-tripped through the lock file, where anything
 * may have happened to it, so every malformed form has to be rejected the same way -- callers
 * recover by catching IllegalArgumentException and ignoring the lock.
 */
public class SocketBinderTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static boolean udsSupported() {
    return !System.getProperty("os.name", "").toLowerCase().startsWith("windows");
  }

  @Test
  public void aBoundSocketIsPrivateToItsOwner() throws IOException {
    assumeTrue(udsSupported());
    Path sock = tempFolder.newFolder("arend").toPath().resolve("daemon.sock");
    try (ServerSocketChannel ch = SocketBinder.bind(sock).channel()) {
      assertNotNull(ch);
      assumeTrue(Files.getFileStore(sock).supportsFileAttributeView("posix"));
      assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(sock)));
    }
  }

  @Test
  public void theBoundAddressRoundTripsThroughTheLockFile() throws IOException {
    assumeTrue(udsSupported());
    Path sock = tempFolder.newFolder("arend").toPath().resolve("daemon.sock");
    try (ServerSocketChannel ignored = SocketBinder.bind(sock).channel()) {
      SocketBinder.Address parsed = SocketBinder.parseAddress("uds:" + sock.toAbsolutePath());
      assertEquals(new SocketBinder.Address.Uds(sock.toAbsolutePath()), parsed);
    }
  }

  /** A socket file left by a dead daemon must not stop the next one from starting. */
  @Test
  public void aStaleSocketFileIsReplaced() throws IOException {
    assumeTrue(udsSupported());
    Path sock = tempFolder.newFolder("arend").toPath().resolve("daemon.sock");
    SocketBinder.bind(sock).channel().close();   // leaves the file behind, nothing listening
    assertTrue(Files.exists(sock));
    try (ServerSocketChannel ch = SocketBinder.bind(sock).channel()) {
      assertTrue(ch.isOpen());
    }
  }

  /**
   * A socket file belonging to a <em>live</em> daemon must not be unlinked: that would leave it
   * running and unreachable, with clients connecting to whatever binds the name next.
   */
  @Test
  public void aLiveSocketFileIsNotUnlinked() throws IOException {
    assumeTrue(udsSupported());
    Path sock = tempFolder.newFolder("arend").toPath().resolve("daemon.sock");
    try (ServerSocketChannel live = SocketBinder.bind(sock).channel()) {
      SocketBinder.Bound second = SocketBinder.bind(sock);
      try {
        assertTrue("the running daemon's socket must survive a second bind attempt", live.isOpen());
        assertTrue("the second bind must not claim the same address",
            second.address().startsWith("tcp:"));
      } finally {
        second.channel().close();
      }
    }
  }

  @Test
  public void aWellFormedAddressParses() {
    assertEquals(new SocketBinder.Address.Uds(Path.of("/tmp/a.sock")),
        SocketBinder.parseAddress("uds:/tmp/a.sock"));
    assertEquals(new SocketBinder.Address.Tcp("127.0.0.1", 4242),
        SocketBinder.parseAddress("tcp:127.0.0.1:4242"));
    assertEquals(new SocketBinder.Address.Tcp("::1", 1),
        SocketBinder.parseAddress("tcp:::1:1"));
  }

  /**
   * Every one of these used to be either accepted or thrown as something other than
   * IllegalArgumentException -- {@code "tcp:host"} raised StringIndexOutOfBoundsException, which
   * the callers' guards do not catch, so a damaged lock file crashed the client.
   */
  @Test
  public void everyMalformedAddressIsRejectedTheSameWay() {
    List<String> malformed = List.of(
        "", "uds:", "tcp:", "tcp:host", "tcp:host:", "tcp::", "tcp:host:notaport",
        "tcp:host:0", "tcp:host:65536", "tcp:host:-1", "pipe:/tmp/x", "/tmp/x.sock");
    for (String address : malformed) {
      assertThrows("must be rejected: <" + address + ">",
          IllegalArgumentException.class, () -> SocketBinder.parseAddress(address));
    }
  }
}
