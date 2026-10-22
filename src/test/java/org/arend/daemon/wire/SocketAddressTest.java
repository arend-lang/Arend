package org.arend.daemon.wire;

import org.arend.daemon.wire.SocketAddress;
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

  /**
   * A daemon binds the loopback interface and nothing else, so a lock naming another host was not
   * written by one: following it would send the user's command line to wherever it points.
   */
  @Test
  public void anAddressOffThisMachineIsRejected() {
    for (String address : List.of("tcp:example.com:443", "tcp:10.0.0.1:4242", "tcp:0.0.0.0:4242",
        "tcp:127.0.0.1.example.com:4242", "tcp:1270.0.0.1:1", "tcp:127.0.0.256:1", "tcp:::2:1")) {
      assertThrows("must be rejected: <" + address + ">",
          IllegalArgumentException.class, () -> SocketAddress.parse(address));
    }
    assertEquals(new SocketAddress.Tcp("localhost", 7), SocketAddress.parse("tcp:localhost:7"));
    assertEquals(new SocketAddress.Tcp("127.1.2.3", 7), SocketAddress.parse("tcp:127.1.2.3:7"));
  }
}
