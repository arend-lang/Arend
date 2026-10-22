package org.arend.daemon.server;

import org.arend.daemon.LockFile;
import org.arend.daemon.client.DaemonClient;
import org.arend.daemon.wire.SocketAddress;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

/** What the IDE's daemon says of itself beside the CLI's: its host, and the requests it has answered, for the IDE to show. */
public class DaemonServerActivityTest {
  private static final String TOKEN = "t";

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private DaemonServer server;
  private SocketAddress address;

  @Before
  public void setUp() throws IOException {
    assumeTrue(org.arend.daemon.Platform.supportsUnixSockets());
    SocketBinder.Bound bound = SocketBinder.bind(tempFolder.newFolder("d").toPath().resolve("daemon.sock"), "0123456789abcdef");
    server = new DaemonServer(bound, TOKEN, () -> List.of("/lib/arend.yaml"), Map.of(), LockFile.HOST_INTELLIJ, request -> {
      if (request.args() == null) return 0;
      request.output().out("checked\n");
      return 3;
    }, () -> {}, args -> args == null ? "refresh" : String.join(" ", args), () -> {});
    server.start();
    address = SocketAddress.parse(bound.address());
  }

  @After
  public void tearDown() {
    if (server != null) server.close();
  }

  @Test
  public void theStatusSaysWhatRunsTheDaemon() throws IOException {
    List<Map<String, Object>> frames = new ArrayList<>();
    try (DaemonClient client = DaemonClient.connect(address, TOKEN)) {
      client.invoke("status", Map.of(), frames::add);
    }
    Map<String, Object> status = frames.getFirst();
    assertEquals(ProcessHandle.current().pid(), ((Number) status.get("pid")).longValue());
    assertEquals(LockFile.HOST_INTELLIJ, status.get("host"));
    assertEquals(List.of("/lib/arend.yaml"), status.get("libraries"));
  }

  /** Both answered, as the runner's description names them, newest first, and nothing left running. */
  @Test
  public void theActivitySaysWhatWasAnswered() throws IOException {
    try (DaemonClient client = DaemonClient.connect(address, TOKEN)) {
      assertEquals(3, client.invoke("cli", Map.of("args", List.of("/lib/arend.yaml", "Foo")), frame -> {}));
      assertEquals(0, client.invoke("refresh", Map.of(), frame -> {}));
    }
    DaemonServer.Activity activity = server.activity();
    assertEquals("IDLE", activity.state());
    assertNull(activity.current());
    assertEquals(2, activity.served());
    assertEquals(List.of("refresh", "/lib/arend.yaml Foo"), activity.recent().stream().map(DaemonServer.Finished::command).toList());
    assertEquals(List.of(0, 3), activity.recent().stream().map(DaemonServer.Finished::exitCode).toList());
  }
}
