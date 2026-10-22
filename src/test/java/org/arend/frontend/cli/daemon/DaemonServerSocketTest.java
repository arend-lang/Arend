package org.arend.frontend.cli.daemon;

import org.arend.daemon.LockFile;
import org.arend.daemon.server.SocketBinder;
import org.arend.daemon.wire.SocketAddress;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.daemon.client.DaemonClient;
import org.arend.daemon.server.DaemonServer;
import org.arend.frontend.cli.daemon.server.CliDaemonRunner;
import org.arend.frontend.cli.daemon.server.CliDispatcher;
import org.arend.frontend.cli.daemon.server.StreamRedirector;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * {@link DaemonServer} and {@link DaemonClient} over a real Unix domain socket, on a context
 * bootstrapped from a real library -- the things worth checking here are the ones that only exist
 * end to end: output arriving as frames in order, the command's own exit code, and a second
 * command on the same warm context not being contaminated by the first.
 */
public class DaemonServerSocketTest {
  /** What the daemon demands of every request, as the registry's copy of its lock would say. */
  private static final String TOKEN = "test-token";

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private DaemonServer server;
  private SocketAddress address;
  private Path libRoot;

  @Before
  public void setUp() throws IOException {
    assumeTrue("Unix domain sockets required",
        org.arend.daemon.Platform.supportsUnixSockets());

    libRoot = tempFolder.newFolder("lib").toPath();
    Files.createDirectories(libRoot.resolve("src"));
    Files.writeString(libRoot.resolve("arend.yaml"), "sourcesDir: src\nbinariesDir: bin\n",
        StandardCharsets.UTF_8);
    writeModule("Good", "\\func good : Nat => 0\n");
    writeModule("Bad", "\\func bad : Nat => nosuchthing\n");

    server = serverFor("state");
    address = SocketAddress.parse(boundAddress);
  }

  private String boundAddress;
  /** Counted down once a server has let its queue run out after a shutdown. */
  private final CountDownLatch stopped = new CountDownLatch(1);

  /** A server on the fixture library, bootstrapped with {@code extraArgs}. */
  private DaemonServer serverFor(String stateDir, String... extraArgs) throws IOException {
    String[] args = new String[extraArgs.length + 1];
    args[0] = libRoot.toString();
    System.arraycopy(extraArgs, 0, args, 1, extraArgs.length);
    CommandContext ctx = new ConsoleMain().runDaemonBootstrap(args);
    assertNotNull("daemon bootstrap failed", ctx);

    Path sock = tempFolder.newFolder(stateDir).toPath().resolve("daemon.sock");
    SocketBinder.Bound bound = SocketBinder.bind(sock, "0123456789abcde" + stateDir.charAt(0));
    boundAddress = bound.address();
    DaemonServer s = CliDaemonRunner.server(bound, TOKEN, ctx, List.of(libRoot.resolve("arend.yaml").toString()),
        CliDispatcher::run, stopped::countDown);
    s.start();
    return s;
  }

  @After
  public void tearDown() {
    if (server != null) server.close();
    // Starting a server installs a process-wide stdout/stderr shim; this JVM runs every other
    // test in the suite too, so it has to come back off.
    StreamRedirector.uninstall();
  }

  private void writeModule(String name, String body) throws IOException {
    Files.writeString(libRoot.resolve("src").resolve(name + ".ard"), body, StandardCharsets.UTF_8);
  }

  /** One request, with its streamed output collected in arrival order. */
  private record Result(int exitCode, String output) {}

  private Result invoke(String op, Map<String, Object> extra) throws IOException {
    List<String> chunks = new ArrayList<>();
    try (DaemonClient client = DaemonClient.connect(address, TOKEN)) {
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

  private Result cliIn(Path cwd, String... args) throws IOException {
    return invoke("cli", Map.of("args", List.of(args), "cwd", cwd.toString()));
  }

  /**
   * A relative path in a routed command belongs to the client's directory. The daemon's own cwd is
   * whatever shell started it, fixed for its lifetime, so resolving against that would make the
   * same command write its output somewhere else the moment a daemon exists.
   */
  @Test
  public void aRelativePathIsResolvedAgainstTheClientsDirectory() throws IOException {
    Path clientCwd = tempFolder.newFolder("client-cwd").toPath();
    Result result = cliIn(clientCwd, "--json", "-ss", "good", "--log-file", "diagnostics.log");
    assertEquals(result.output(), 0, result.exitCode());
    assertTrue("the log must land in the client's directory, not the daemon's",
        Files.isRegularFile(clientCwd.resolve("diagnostics.log")));
  }

  @Test
  public void pingAnswersWithTheWorkerState() throws IOException {
    List<String> states = new ArrayList<>();
    try (DaemonClient client = DaemonClient.connect(address, TOKEN)) {
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
    try (DaemonClient client = DaemonClient.connect(address, TOKEN)) {
      assertEquals(0, client.invoke("status", Map.of(), frames::add));
    }
    Map<String, Object> status = frames.stream()
        .filter(f -> "status".equals(f.get("kind"))).findFirst().orElseThrow();
    assertEquals(LockFile.PROTOCOL_VERSION, status.get("protocolVersion"));
    assertEquals(ProcessHandle.current().pid(), ((Number) status.get("pid")).longValue());
    assertTrue(status.get("lockedFlags") instanceof Map);
  }

  /**
   * A TCP port is anybody's to connect to, and so is a socket in somebody else's directory: the
   * token is what tells the user's own clients apart. Without it nothing runs, not even a ping.
   */
  @Test
  public void aRequestWithoutTheTokenIsRefusedAndItsConnectionClosed() throws IOException {
    for (String token : new String[] { null, "", "not-" + TOKEN }) {
      try (DaemonClient client = DaemonClient.connect(address, token)) {
        List<String> messages = new ArrayList<>();
        assertEquals(1, client.invoke("shutdown", Map.of(),
            f -> { if (f.get("message") != null) messages.add(String.valueOf(f.get("message"))); }));
        assertTrue(messages.toString(), messages.getFirst().contains("token"));
        try {
          client.invoke("ping", Map.of(), null);
          fail("the daemon must close a connection that did not present its token");
        } catch (IOException expected) {
        }
      }
    }
    // Still running: the refused shutdowns did nothing.
    try (DaemonClient client = DaemonClient.connect(address, TOKEN)) {
      assertEquals(0, client.invoke("ping", Map.of(), null));
    }
  }

  @Test
  public void anUnknownOpIsRefusedWithoutKillingTheConnection() throws IOException {
    try (DaemonClient client = DaemonClient.connect(address, TOKEN)) {
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
   * A positional that is neither a library nor a module path used to be dropped, leaving the
   * scope empty, so the daemon typechecked the whole library and reported success -- the opposite
   * verdict from the same argv under --no-daemon. And since the warm context is reused, that
   * failure must not reach the next command either.
   */
  @Test
  public void anUnparseablePositionalFailsWithoutWideningTheScopeOrLeaking() throws IOException {
    Result bad = cli("Bad-bogus");
    assertEquals(1, bad.exitCode());
    assertTrue(bad.output(), bad.output().contains("Bad-bogus"));
    assertFalse("the run must not have quietly typechecked the whole library",
        bad.output().contains("--- Typechecking lib ---"));
    assertEquals("a later command must not inherit the previous one's exit code",
        0, cli("Good").exitCode());
  }

  @Test
  public void aReplRequestIsRefused() throws IOException {
    Result result = cli("-i");
    assertEquals(1, result.exitCode());
    assertTrue(result.output(), result.output().contains("not supported in daemon mode"));
  }

  /**
   * {@code arend} answers -h and -v itself, before it looks for a daemon; a daemon answers a query
   * tool's help alone. A tool's help is served in full; that is not a failure.
   */
  @Test
  public void aToolsHelpSucceedsAndOtherHelpOrABadFlagDoesNot() throws IOException {
    Result help = cli("--help");
    assertEquals(1, help.exitCode());
    assertTrue(help.output(), help.output().contains("answered by `arend --help` itself"));
    assertEquals(1, cli("-v").exitCode());
    Result toolHelp = cli("-h", "-ss");
    assertEquals(toolHelp.output(), 0, toolHelp.exitCode());
    assertTrue(toolHelp.output(), toolHelp.output().contains("-ss <pattern>"));
    assertEquals(1, cli("--no-such-flag").exitCode());
  }

  /** A locked flag is reported as ignored rather than silently applied. */
  @Test
  public void aLockedFlagIsWarnedAbout() throws IOException {
    Result result = cli("-r", "Good");
    assertTrue(result.output(), result.output().contains("-r passed to a daemon-served command is ignored"));
  }

  /**
   * {@code refresh} resolves the served libraries again and typechecks nothing; nor does it report
   * the daemon's own bootstrap flags as ignored, which would be false and unactionable.
   */
  @Test
  public void refreshResolvesWithoutTypecheckingOrWarning() throws Exception {
    DaemonServer refreshServer = serverFor("refresh", "-r");
    try {
      SocketAddress refreshAddress = SocketAddress.parse(boundAddress);
      List<String> chunks = new ArrayList<>();
      try (DaemonClient client = DaemonClient.connect(refreshAddress, TOKEN)) {
        client.invoke("refresh", Map.of(), frame -> {
          Object data = frame.get("data");
          if (data != null) chunks.add(String.valueOf(data));
        });
      }
      String output = String.join("", chunks);
      assertTrue("refresh must resolve the library again: " + output, output.contains("[INFO] Resolved lib"));
      assertFalse("refresh must not typecheck: " + output, output.contains("Typechecking"));
      assertFalse("refresh must not report the bootstrap's own flags as ignored: " + output,
          output.contains("is ignored"));
    } finally {
      refreshServer.close();
    }
  }

  /**
   * Once the jar a daemon runs from has been rebuilt or replaced, its answers are a build the user no
   * longer has, if it can still read the classes it needs at all: it turns the request away, and stops.
   */
  @Test
  public void aDaemonWhoseJarChangedTurnsRequestsAwayAndStops() throws Exception {
    java.util.concurrent.atomic.AtomicBoolean replaced = new java.util.concurrent.atomic.AtomicBoolean();
    server.retireWhen(replaced::get, "the jar has changed");
    assertEquals(0, cli(libRoot.toString(), "-ss", "good").exitCode());

    replaced.set(true);
    Result turnedAway = cli(libRoot.toString(), "-ss", "good");
    assertEquals(1, turnedAway.exitCode());
    assertTrue(turnedAway.output(), turnedAway.output().contains("the jar has changed"));
    assertTrue("the daemon must stop", stopped.await(30, TimeUnit.SECONDS));
  }

  @Test
  public void shutdownStopsTheServer() throws Exception {
    try (DaemonClient client = DaemonClient.connect(address, TOKEN)) {
      assertEquals(0, client.invoke("shutdown", Map.of(), null));
    }
    assertTrue("the server must stop once its queue has run out", stopped.await(30, TimeUnit.SECONDS));
  }
}
