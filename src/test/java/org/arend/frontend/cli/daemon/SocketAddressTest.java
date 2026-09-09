package org.arend.frontend.cli.daemon;

import org.arend.frontend.cli.daemon.wire.SocketAddress;
import org.junit.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * The daemon's address is round-tripped through the lock file, where anything may have happened
 * to it, so every malformed form has to be rejected the same way: callers recover by catching
 * IllegalArgumentException and ignoring the lock.
 */
public class SocketAddressTest {
  @Test
  public void aWellFormedAddressParses() {
    assertEquals(new SocketAddress.Uds(Path.of("/tmp/a.sock")),
        SocketAddress.parse("uds:/tmp/a.sock"));
    assertEquals(new SocketAddress.Tcp("127.0.0.1", 4242),
        SocketAddress.parse("tcp:127.0.0.1:4242"));
    assertEquals(new SocketAddress.Tcp("::1", 1), SocketAddress.parse("tcp:::1:1"));
  }

  /**
   * {@code "tcp:host"} used to raise StringIndexOutOfBoundsException, which the callers' guards
   * do not catch, so a damaged lock file crashed the client instead of being ignored.
   */
  @Test
  public void everyMalformedAddressIsRejectedTheSameWay() {
    List<String> malformed = List.of(
        "", "uds:", "tcp:", "tcp:host", "tcp:host:", "tcp::", "tcp:host:notaport",
        "tcp:host:0", "tcp:host:65536", "tcp:host:-1", "pipe:/tmp/x", "/tmp/x.sock");
    for (String address : malformed) {
      assertThrows("must be rejected: <" + address + ">",
          IllegalArgumentException.class, () -> SocketAddress.parse(address));
    }
  }
}
