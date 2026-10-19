package org.arend.daemon.server;

import org.arend.daemon.client.DaemonClient;
import org.arend.daemon.wire.SocketAddress;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/** A request that fails in its runner: the client is the one place its trace goes, so it gets all of it. */
public class DaemonServerTraceTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private DaemonServer server;

  @After
  public void tearDown() {
    if (server != null) server.close();
  }

  @Test
  public void aRunnerThatThrowsSendsItsWholeTrace() throws IOException {
    assumeTrue(!System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
    SocketBinder.Bound bound = SocketBinder.bind(tempFolder.newFolder("d").toPath().resolve("daemon.sock"), "0123456789abcdef");
    server = new DaemonServer(bound, "t", List::of, Map.of(), "cli", request -> {
      throw new NullPointerException();
    }, () -> {}, args -> "", () -> {});
    server.start();

    StringBuilder err = new StringBuilder();
    try (DaemonClient client = DaemonClient.connect(SocketAddress.parse(bound.address()), "t")) {
      assertEquals(1, client.invoke("cli", Map.of("args", List.of("x")), frame -> {
        if ("stderr".equals(frame.get("kind"))) err.append(frame.get("data"));
      }));
    }
    assertTrue(err.toString(), err.toString().contains("java.lang.NullPointerException"));
    assertTrue("the trace must say where: " + err, err.toString().contains("at org.arend.daemon.server.DaemonServerTraceTest"));
  }
}
