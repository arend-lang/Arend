package org.arend.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.server.SocketBinder;
import org.arend.frontend.cli.CommandContext;
import org.arend.daemon.server.DaemonServer;
import org.arend.frontend.cli.daemon.server.CliDaemonRunner;
import org.arend.frontend.cli.daemon.server.StreamRedirector;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * {@code arend --mcp} against two real {@link DaemonServer}s whose command runners only record
 * what reached them: which daemon a tool call goes to, and what it is asked to do.
 */
public class DaemonMcpServerTest {
  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private Path libA, libB, shared, libDir;
  private final List<DaemonServer> daemons = new ArrayList<>();
  /** "A argv..." / "B argv...", in the order the daemons ran them. */
  private final List<String> received = new CopyOnWriteArrayList<>();
  private final CountDownLatch holdStarted = new CountDownLatch(1);

  private PrintStream toServer;
  private BufferedReader fromServer;
  private Thread serving;

  @Before
  public void setUp() throws IOException {
    assumeTrue("Unix domain sockets required",
        !System.getProperty("os.name", "").toLowerCase().startsWith("windows"));
    Path config = tempFolder.newFolder("config").toPath();
    System.setProperty(DaemonPaths.CONFIG_DIR_PROPERTY, config.toString());
    libA = library(tempFolder.getRoot().toPath().resolve("liba"));
    libB = library(tempFolder.getRoot().toPath().resolve("libb"));
    libDir = config.resolve("libs");
    shared = library(libDir.resolve("sharedlib"));
    startDaemon("A", libA, 1);
    startDaemon("B", libB, 2, shared);
  }

  @After
  public void tearDown() throws Exception {
    if (toServer != null) toServer.close();
    if (serving != null) serving.join(5000);
    daemons.forEach(DaemonServer::close);
    StreamRedirector.uninstall();
    System.clearProperty(DaemonPaths.CONFIG_DIR_PROPERTY);
  }

  private static Path library(Path root) throws IOException {
    Files.createDirectories(root.resolve("src"));
    Files.writeString(root.resolve("arend.yaml"), "sourcesDir: src\n", StandardCharsets.UTF_8);
    return root;
  }

  private static String config(Path root) {
    return DaemonPaths.resolve(root).libraryConfig.toString();
  }

  /** A daemon owning {@code root} and having loaded {@code sharedLoaded}, findable the way a real one is. */
  private void startDaemon(String name, Path root, long startedAt, Path... sharedLoaded) throws IOException {
    DaemonPaths paths = DaemonPaths.resolve(root);
    paths.ensureArendDir();
    SocketBinder.Bound bound = SocketBinder.bind(paths.socketFile, paths.libraryHash);
    String token = LockFile.newToken();
    DaemonServer daemon = CliDaemonRunner.server(bound, token, new CommandContext(), List.of(paths.libraryConfig.toString()),
        (context, args, cwd) -> {
          received.add(name + " " + String.join(" ", args));
          if (List.of(args).contains("Hold")) {
            holdStarted.countDown();
            long deadline = System.currentTimeMillis() + 30_000;
            while (!context.cancellation.isCanceled() && System.currentTimeMillis() < deadline) {
              try { Thread.sleep(5); } catch (InterruptedException e) { return 1; }
            }
            return 0;
          }
          System.out.println("ran on " + name);
          return 0;
        }, () -> {});
    daemon.start();
    daemons.add(daemon);

    List<String> sharedConfigs = new ArrayList<>();
    for (Path library : sharedLoaded) sharedConfigs.add(config(library));
    LockFile lock = new LockFile(ProcessHandle.current().pid(), paths.libraryConfig.toString(), paths.libraryHash,
        List.of(paths.libraryConfig.toString()), sharedConfigs, LockFile.PROTOCOL_VERSION, startedAt, bound.address(), LockFile.HOST_CLI,
        LockFile.UNKNOWN_START, token, null);
    lock.writeEverywhere();
  }

  /** Starts the MCP server as an agent would, from {@code workingDir}. */
  private void serve(Path workingDir) throws IOException {
    PipedOutputStream requests = new PipedOutputStream();
    PipedInputStream serverIn = new PipedInputStream(requests, 1 << 16);
    PipedInputStream replies = new PipedInputStream(1 << 20);
    PrintStream serverOut = new PrintStream(new PipedOutputStream(replies), true, StandardCharsets.UTF_8);
    toServer = new PrintStream(requests, true, StandardCharsets.UTF_8);
    fromServer = new BufferedReader(new InputStreamReader(replies, StandardCharsets.UTF_8));
    DaemonMcpServer server = new DaemonMcpServer(serverIn, serverOut, List.of(libDir), workingDir);
    serving = new Thread(server::serve, "mcp-under-test");
    serving.setDaemon(true);
    serving.start();
  }

  private JsonNode rpc(int id, String method, String params) throws IOException {
    toServer.println("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + params + "}");
    return reply(id);
  }

  private synchronized JsonNode reply(int id) throws IOException {
    while (true) {
      JsonNode reply = MAPPER.readTree(fromServer.readLine());
      if (reply.path("id").asInt(-1) == id) return reply;
    }
  }

  private JsonNode call(int id, String tool, String arguments) throws IOException {
    return rpc(id, "tools/call", "{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}").get("result");
  }

  private static String text(JsonNode result) {
    List<String> texts = new ArrayList<>();
    for (JsonNode c : result.get("content")) texts.add(c.get("text").asText());
    return String.join("\n", texts);
  }

  private static String json(Path path) {
    return "\"" + path.toString().replace("\\", "\\\\") + "\"";
  }

  /** A line rewritten with `\r` keeps its last state; every line break stays, a leading one too. */
  @Test
  public void carriageReturnsCollapseToTheLastStateOfEachLine() {
    assertEquals("\n\n[2/2] B\ndone\n", DaemonMcpServer.collapseCarriageReturns("\n\n[1/2] A\r[2/2] B\r\ndone\n"));
  }

  @Test
  public void toolsTakeTheLibraryTheyAreAbout() throws IOException {
    serve(tempFolder.getRoot().toPath());
    JsonNode init = rpc(1, "initialize", "{\"protocolVersion\":\"2025-06-18\",\"capabilities\":{}}").get("result");
    assertEquals("arend-daemons", init.get("serverInfo").get("name").asText());

    List<String> names = new ArrayList<>();
    for (JsonNode tool : rpc(2, "tools/list", "{}").get("result").get("tools")) {
      names.add(tool.get("name").asText());
      if (!tool.get("name").asText().equals("daemon_list") && !DaemonMcpSkills.handles(tool.get("name").asText())) {
        assertTrue(tool.get("name").asText(), tool.get("inputSchema").get("properties").has("library"));
      }
    }
    assertEquals(List.of("typecheck", "symbol_search", "proof_search", "find_usages", "class_hierarchy", "scope",
        "daemon_list", "daemon_ping", "daemon_status", "daemon_refresh", "daemon_stop", "skills_list", "skills_install",
        "skills_remove"), names);
  }

  /** The call goes to the daemon owning the library, limited to it by a leading positional. */
  @Test
  public void aCallGoesToTheDaemonHoldingItsLibrary() throws IOException {
    serve(tempFolder.getRoot().toPath());
    JsonNode result = call(1, "typecheck", "{\"library\":" + json(libB) + ",\"targets\":[\"Foo\"],\"show_times\":true}");
    assertFalse(text(result), result.get("isError").asBoolean());
    assertEquals("ran on B", text(result).trim());
    assertEquals(List.of("B " + config(libB) + " Foo --show-times"), received);

    call(2, "find_usages", "{\"library\":" + json(libA) + ",\"args\":[\"M:f\"]}");
    assertEquals("A " + config(libA) + " -fu M:f", received.get(1));
  }

  /** A shared library is held by whichever daemon loaded it, and is no daemon's to stop. */
  @Test
  public void aSharedLibraryGoesToTheDaemonThatLoadedIt() throws IOException {
    serve(tempFolder.getRoot().toPath());
    call(1, "proof_search", "{\"library\":\"sharedlib\",\"args\":[\"Nat\"]}");
    assertEquals(List.of("B " + config(shared) + " -ps Nat"), received);

    JsonNode stop = call(2, "daemon_stop", "{\"library\":\"sharedlib\"}");
    assertTrue(text(stop), stop.get("isError").asBoolean());
  }

  /** A name means the library of that name a running daemon serves, not a like-named one found from here. */
  @Test
  public void aNameMeansTheLibraryARunningDaemonServes() throws IOException {
    library(libDir.resolve("proj"));
    Path proj = library(tempFolder.getRoot().toPath().resolve("elsewhere").resolve("proj"));
    startDaemon("C", proj, 3);
    serve(tempFolder.getRoot().toPath());
    call(1, "symbol_search", "{\"library\":\"proj\",\"args\":[\"Nat\"]}");
    assertEquals(List.of("C " + config(proj) + " -ss Nat"), received);
  }

  /** Two daemons owning like-named directories: the name decides nothing, so nothing runs, and that is said. */
  @Test
  public void aNameTwoDaemonsOwnRunsNothing() throws IOException {
    Path first = library(tempFolder.getRoot().toPath().resolve("one").resolve("proj"));
    Path second = library(tempFolder.getRoot().toPath().resolve("two").resolve("proj"));
    startDaemon("D", second, 4);
    startDaemon("C", first, 3);
    serve(tempFolder.getRoot().toPath());
    JsonNode result = call(1, "symbol_search", "{\"library\":\"proj\",\"args\":[\"Nat\"]}");
    assertTrue(received.isEmpty());
    assertTrue(result.get("isError").asBoolean());
    assertEquals("[WARN] several running daemons serve a library named 'proj': " + config(first) + ", "
        + config(second) + "; nothing was run. Give the path of the one you mean.", text(result));

    call(2, "symbol_search", "{\"library\":" + json(second) + ",\"args\":[\"Nat\"]}");
    assertEquals(List.of("D " + config(second) + " -ss Nat"), received);
  }

  /** A name means a project a daemon owns, before a zip of that name an older daemon loaded, and says so. */
  @Test
  public void aNameMeansTheProjectBeforeAZip() throws IOException {
    Path zip = Files.writeString(libDir.resolve("proj.zip"), "");
    startDaemon("C", library(tempFolder.getRoot().toPath().resolve("libc")), 3, zip);
    Path proj = library(tempFolder.getRoot().toPath().resolve("elsewhere").resolve("proj"));
    startDaemon("D", proj, 4);
    serve(tempFolder.getRoot().toPath());
    JsonNode result = call(1, "symbol_search", "{\"library\":\"proj\",\"args\":[\"Nat\"]}");
    assertEquals(List.of("D " + config(proj) + " -ss Nat"), received);
    assertFalse(text(result), result.get("isError").asBoolean());
    assertEquals("[WARN] a library named 'proj' is both a project a daemon owns and a zip or a ~/.arend/libs"
        + " library a daemon loaded: " + config(zip) + "; using the project, " + config(proj)
        + ". Give a path to choose another.\nran on D", text(result).trim());
  }

  /** With no daemon owning a project of the name, the longest-running one that loaded a zip of it gets it. */
  @Test
  public void aZipGoesToTheLongestRunningDaemonThatLoadedIt() throws IOException {
    Path zip = Files.writeString(libDir.resolve("proj.zip"), "");
    startDaemon("D", library(tempFolder.getRoot().toPath().resolve("libd")), 4, zip);
    startDaemon("C", library(tempFolder.getRoot().toPath().resolve("libc")), 3, zip);
    serve(tempFolder.getRoot().toPath());
    JsonNode result = call(1, "symbol_search", "{\"library\":\"proj\",\"args\":[\"Nat\"]}");
    assertEquals(List.of("C " + config(zip) + " -ss Nat"), received);
    assertFalse("one zip loaded twice is no ambiguity: " + text(result), text(result).contains("[WARN]"));
  }

  /** With no library, the agent's own directory decides; failing that, an ambiguity is reported. */
  @Test
  public void withoutALibraryTheWorkingDirectoryDecides() throws IOException {
    serve(libA);
    call(1, "typecheck", "{}");
    assertEquals(List.of("A " + config(libA)), received);
    toServer.close();
    serving.interrupt();

    serve(tempFolder.getRoot().toPath());
    JsonNode ambiguous = call(2, "typecheck", "{}");
    assertTrue(ambiguous.get("isError").asBoolean());
    assertTrue(text(ambiguous), text(ambiguous).contains("several daemons are running"));
  }

  @Test
  public void aLibraryNoDaemonHoldsIsReported() throws IOException {
    serve(tempFolder.getRoot().toPath());
    Path other = library(tempFolder.getRoot().toPath().resolve("otherlib"));
    JsonNode result = call(1, "typecheck", "{\"library\":" + json(other) + "}");
    assertTrue(result.get("isError").asBoolean());
    assertTrue(text(result), text(result).contains("start one with `arend -d"));

    JsonNode missing = call(2, "typecheck", "{\"library\":\"nosuchlib\"}");
    assertTrue(text(missing), text(missing).contains("cannot find the library"));
    assertTrue(received.isEmpty());
  }

  @Test
  public void theDaemonsAreListed() throws IOException {
    serve(tempFolder.getRoot().toPath());
    String list = text(call(1, "daemon_list", "{}"));
    assertTrue(list, list.indexOf(config(libA)) < list.indexOf(config(libB)));
    assertTrue(list, list.contains("also loaded from ~/.arend/libs:\n    " + config(shared)));

    String status = text(call(2, "daemon_status", "{\"library\":" + json(libB) + "}"));
    assertTrue(status, status.contains("\"state\" : \"IDLE\""));
    assertTrue(status, status.contains(config(libB)));
  }

  @Test
  public void aCancelledCallIsCancelledOnTheDaemon() throws Exception {
    serve(tempFolder.getRoot().toPath());
    CompletableFuture<JsonNode> pending = CompletableFuture.supplyAsync(() -> {
      try {
        return call(7, "typecheck", "{\"library\":" + json(libA) + ",\"targets\":[\"Hold\"]}");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    assertTrue(holdStarted.await(10, TimeUnit.SECONDS));
    toServer.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":7}}");
    JsonNode result = pending.get(10, TimeUnit.SECONDS);
    assertTrue(result.get("isError").asBoolean());
    assertTrue(text(result), text(result).contains("cancelled"));
  }

  /** A cancel right behind its call -- ahead of the call's daemon connection, or of its request -- still lands. */
  @Test
  public void aCancelRightBehindItsCallIsNotLost() throws Exception {
    serve(tempFolder.getRoot().toPath());
    toServer.println("{\"jsonrpc\":\"2.0\",\"id\":8,\"method\":\"tools/call\",\"params\":{\"name\":\"typecheck\","
        + "\"arguments\":{\"library\":" + json(libA) + ",\"targets\":[\"Hold\"]}}}");
    toServer.println("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/cancelled\",\"params\":{\"requestId\":8}}");
    CompletableFuture<JsonNode> pending = CompletableFuture.supplyAsync(() -> {
      try {
        return reply(8).get("result");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    // Lost, the cancel would leave Hold running out its 30 seconds.
    JsonNode result = pending.get(10, TimeUnit.SECONDS);
    assertTrue(text(result), text(result).contains("cancelled"));
  }
}
