package org.arend.frontend.cli.daemon;

import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.daemon.server.DaemonServer;
import org.arend.frontend.cli.daemon.server.SocketBinder;
import org.arend.frontend.cli.daemon.client.DaemonClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * {@link DaemonServer} and {@link DaemonClient} over a real Unix domain socket — the one path
 * every other daemon test bypasses.
 *
 * <p>The context here is a real one, bootstrapped from a real library on disk, because the
 * things worth checking are the ones that only exist end to end: that a command's output comes
 * back as frames in order, that its exit code is the command's own, and that a second command
 * on the same warm context is not contaminated by the first.
 */
public class DaemonServerSocketTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private DaemonServer server;
  private SocketBinder.Address address;
  private Path libRoot;

  @Before
  public void setUp() throws IOException {
    assumeTrue("Unix domain sockets required",
        !System.getProperty("os.name", "").toLowerCase().startsWith("windows"));

    libRoot = tempFolder.newFolder("lib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\nbinariesDir: bin\n",
        StandardCharsets.UTF_8);
    writeModule("Good", "\\func good : Nat => 0\n");
    writeModule("Bad", "\\func bad : Nat => nosuchthing\n");

    CommandContext ctx = new ConsoleMain()
        .runDaemonBootstrap(new String[] { libRoot.toString() });
    assertNotNull("daemon bootstrap failed", ctx);

    Path sock = tempFolder.newFolder("state").toPath().resolve("daemon.sock");
    SocketBinder.Bound bound = SocketBinder.bind(sock);
    address = SocketBinder.parseAddress(bound.address());
    server = new DaemonServer(bound, ctx, libRoot.resolve("arend.yaml").toString());
    server.start();
  }

  @After
  public void tearDown() {
    if (server != null) server.close();
  }

  private void writeModule(String name, String body) throws IOException {
    Files.writeString(libRoot.resolve("src").resolve(name + ".ard"), body, StandardCharsets.UTF_8);
  }

  /** One request, with its streamed output collected in arrival order. */
  private record Result(int exitCode, String output) {}

  private Result invoke(String op, Map<String, Object> extra) throws IOException {
    List<String> chunks = new ArrayList<>();
    try (DaemonClient client = DaemonClient.connect(address)) {
      int code = client.invoke(op, extra, frame -> {
        Object data = frame.get("data");
        if (data != null) chunks.add(String.valueOf(data));
      });
      return new Result(code, String.join("", chunks));
    }
  }

  private Result cli(String... args) throws IOException {
    return invoke("cli", Map.of("args", List.of(args)));
  }

  @Test
  public void pingAnswersWithTheWorkerState() throws IOException {
    List<String> states = new ArrayList<>();
    try (DaemonClient client = DaemonClient.connect(address)) {
      int code = client.invoke("ping", Map.of(), frame -> {
        if ("state".equals(frame.get("kind"))) states.add(String.valueOf(frame.get("state")));
      });
      assertEquals(0, code);
    }
    assertEquals(List.of("IDLE"), states);
  }

  @Test
  public void statusReportsTheProtocolVersionAndLockedFlags() throws IOException {
    List<Map<String, Object>> frames = new ArrayList<>();
    try (DaemonClient client = DaemonClient.connect(address)) {
      assertEquals(0, client.invoke("status", Map.of(), frames::add));
    }
    Map<String, Object> status = frames.stream()
        .filter(f -> "status".equals(f.get("kind"))).findFirst().orElseThrow();
    assertEquals(LockFile.PROTOCOL_VERSION, status.get("protocolVersion"));
    assertTrue(status.get("lockedFlags") instanceof Map);
  }

  @Test
  public void anUnknownOpIsRefusedWithoutKillingTheConnection() throws IOException {
    try (DaemonClient client = DaemonClient.connect(address)) {
      List<String> messages = new ArrayList<>();
      assertEquals(1, client.invoke("no-such-op", Map.of(),
          f -> { if (f.get("message") != null) messages.add(String.valueOf(f.get("message"))); }));
      assertTrue(messages.toString(), messages.getFirst().contains("unknown op"));
      // Same connection, still usable.
      assertEquals(0, client.invoke("ping", Map.of(), null));
    }
  }

  /** A command's stdout comes back as frames, in order, and its exit code is its own. */
  @Test
  public void aCommandStreamsItsOutputAndReportsItsExitCode() throws IOException {
    Result good = cli("Good");
    assertEquals(0, good.exitCode());
    assertTrue(good.output(), good.output().contains("--- Typechecking Good ---"));
    assertTrue(good.output(), good.output().contains("--- Done ("));
  }

  /**
   * The warm context is reused, so state from one command must not reach the next.
   */
  @Test
  public void oneCommandsFailureDoesNotLeakIntoTheNext() throws IOException {
    assertEquals(1, cli("Bad-bogus").exitCode());
    assertEquals("a later command must not inherit the previous one's exit code",
        0, cli("Good").exitCode());
  }

  /**
   * A positional that is not a library and not a module path used to be
   * dropped, leaving the scope empty, so the daemon typechecked the whole library and reported
   * success -- the opposite verdict from the same argv run with --no-daemon.
   */
  @Test
  public void anUnparseablePositionalFailsRatherThanWideningTheScope() throws IOException {
    Result result = cli("Bad-bogus");
    assertEquals(1, result.exitCode());
    assertTrue(result.output(), result.output().contains("Bad-bogus"));
    assertTrue("the run must not have quietly typechecked the whole library",
        !result.output().contains("--- Typechecking lib ---"));
  }

  @Test
  public void aReplRequestIsRefused() throws IOException {
    Result result = cli("-i");
    assertEquals(1, result.exitCode());
    assertTrue(result.output(), result.output().contains("not supported in daemon mode"));
  }

  /** parseArgs served the request in full; that is not a failure. */
  @Test
  public void helpSucceedsAndABadFlagDoesNot() throws IOException {
    assertEquals(0, cli("--help").exitCode());
    assertEquals(1, cli("--no-such-flag").exitCode());
  }

  /** A locked flag is reported as ignored rather than silently applied. */
  @Test
  public void aLockedFlagIsWarnedAbout() throws IOException {
    Result result = cli("-r", "Good");
    assertTrue(result.output(), result.output().contains("-r passed to a daemon-served command is ignored"));
  }

  @Test
  public void shutdownStopsTheServer() throws Exception {
    try (DaemonClient client = DaemonClient.connect(address)) {
      assertEquals(0, client.invoke("shutdown", Map.of(), null));
    }
    assertTrue("the worker must reach the shutdown latch",
        server.shutdownLatch().await(30, TimeUnit.SECONDS));
  }
}
