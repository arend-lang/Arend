package org.arend.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * The skill tools against a local arend-skills repository, its files and the list of them served as
 * raw.githubusercontent.com and GitHub's trees API serve them, installing into a temporary home.
 */
public class DaemonMcpSkillsTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private HttpServer server;
  private Path repo;
  /** What the trees API lists instead of the files on disk, when a test says so. */
  private String tree;
  private Path home;
  private DaemonMcpSkills skills;

  @Before
  public void setUp() throws IOException {
    repo = tmp.newFolder("repo").toPath();
    home = tmp.newFolder("home").toPath();
    skill("one", "---\nname: one\ndescription: The first.\n---\nBody\n");
    skill("two", "---\nname: two\ndescription: The second.\n---\nBody\n");
    Files.createDirectories(repo.resolve("main").resolve("two").resolve("scripts"));
    Files.writeString(repo.resolve("main").resolve("two").resolve("scripts").resolve("run.sh"), "echo\n");
    // Not skills: a file at the top, and a directory without a SKILL.md.
    Files.writeString(repo.resolve("main").resolve("README.md"), "# arend-skills\n");
    Files.createDirectories(repo.resolve("main").resolve("docs"));
    Files.writeString(repo.resolve("main").resolve("docs").resolve("notes.md"), "notes\n");

    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/raw/", exchange -> {
      Path file = repo.resolve(exchange.getRequestURI().getPath().substring("/raw/".length())).normalize();
      respond(exchange, file.startsWith(repo) && Files.isRegularFile(file) ? Files.readAllBytes(file) : null);
    });
    server.createContext("/tree/", exchange -> {
      Path root = repo.resolve(exchange.getRequestURI().getPath().substring("/tree/".length())).normalize();
      respond(exchange, root.startsWith(repo) && Files.isDirectory(root) ? listing(root).getBytes(StandardCharsets.UTF_8) : null);
    });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    System.setProperty(DaemonMcpSkills.URL_PROPERTY, base + "/raw/{ref}/");
    System.setProperty(DaemonMcpSkills.TREE_PROPERTY, base + "/tree/{ref}");
    skills = new DaemonMcpSkills(home);
  }

  @After
  public void tearDown() {
    System.clearProperty(DaemonMcpSkills.URL_PROPERTY);
    System.clearProperty(DaemonMcpSkills.TREE_PROPERTY);
    if (server != null) server.stop(0);
  }

  private void skill(String name, String text) throws IOException {
    Path dir = repo.resolve("main").resolve(name);
    Files.createDirectories(dir);
    Files.writeString(dir.resolve("SKILL.md"), text);
  }

  private static void respond(HttpExchange exchange, byte[] body) throws IOException {
    exchange.sendResponseHeaders(body == null ? 404 : 200, body == null ? -1 : body.length);
    if (body != null) exchange.getResponseBody().write(body);
    exchange.close();
  }

  /** The files under {@code root} as GitHub's recursive tree lists them, or {@link #tree} when a test set it. */
  private String listing(Path root) throws IOException {
    if (tree != null) return tree;
    ArrayNode entries = MAPPER.createArrayNode();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : (Iterable<Path>) paths.sorted()::iterator) {
        if (path.equals(root)) continue;
        entries.addObject().put("path", root.relativize(path).toString().replace('\\', '/'))
            .put("type", Files.isDirectory(path) ? "tree" : "blob");
      }
    }
    ObjectNode listing = MAPPER.createObjectNode();
    listing.set("tree", entries);
    return listing.put("truncated", false).toString();
  }

  private String call(String tool, String args, String client) throws Exception {
    return skills.call(tool, MAPPER.readTree(args), client);
  }

  private Path claudeSkills() {
    return home.resolve(".claude").resolve("skills");
  }

  @Test
  public void theClientSaysWhichAgentItIs() {
    assertEquals("claude", skills.agentOfClient("claude-code").id());
    assertEquals("codex", skills.agentOfClient("codex-mcp-client").id());
    assertEquals("cursor", skills.agentOfClient("cursor-vscode").id());
    assertEquals("vscode", skills.agentOfClient("Visual Studio Code").id());
    assertEquals(null, skills.agentOfClient("something-else"));
    assertEquals(home.resolve(".agents").resolve("skills"), skills.agentOfClient("codex-mcp-client").skills());
  }

  @Test
  public void installListAndRemove() throws Exception {
    String list = call(DaemonMcpSkills.LIST, "{}", "claude-code");
    assertTrue(list, list.contains("one -- not installed\n    The first."));
    assertFalse(list, list.contains("docs") || list.contains("README"));

    String installed = call(DaemonMcpSkills.INSTALL, "{}", "claude-code");
    assertTrue(installed, installed.contains("one, two"));
    assertEquals("echo\n", Files.readString(claudeSkills().resolve("two").resolve("scripts").resolve("run.sh")));
    JsonNode marker = MAPPER.readTree(claudeSkills().resolve("one").resolve(DaemonMcpSkills.MARKER).toFile());
    assertEquals("main", marker.get("ref").asText());
    assertTrue(call(DaemonMcpSkills.LIST, "{\"ref\":\"main\"}", "claude-code").contains("one -- installed"));

    // A second install replaces the copy, with what the repository has now.
    skill("one", "---\nname: one\ndescription: The first, again.\n---\nBody\n");
    call(DaemonMcpSkills.INSTALL, "{\"ref\":\"main\",\"names\":[\"one\"]}", "claude-code");
    assertTrue(Files.readString(claudeSkills().resolve("one").resolve("SKILL.md")).contains("again"));

    assertEquals("Removed for Claude Code: two.", call(DaemonMcpSkills.REMOVE, "{\"names\":[\"two\"]}", "claude-code"));
    assertFalse(Files.exists(claudeSkills().resolve("two")));
    assertEquals("Removed for Claude Code: one.", call(DaemonMcpSkills.REMOVE, "{}", "claude-code"));
    assertEquals(0, Files.list(claudeSkills()).count());
  }

  /** A skill the user wrote is never overwritten nor removed, even with a name the repository uses. */
  @Test
  public void theUsersOwnSkillIsLeftAlone() throws Exception {
    Path own = claudeSkills().resolve("one");
    Files.createDirectories(own);
    Files.writeString(own.resolve("SKILL.md"), "mine");

    String installed = call(DaemonMcpSkills.INSTALL, "{\"ref\":\"main\"}", "claude-code");
    assertTrue(installed, installed.contains("Left alone, as skills of the user's own: one."));
    assertTrue(call(DaemonMcpSkills.LIST, "{\"ref\":\"main\"}", "claude-code").contains("one -- a skill of this name the user has"));
    String removed = call(DaemonMcpSkills.REMOVE, "{\"names\":[\"one\",\"two\"]}", "claude-code");
    assertTrue(removed, removed.contains("Left alone, as not installed from here: one."));
    assertEquals("mine", Files.readString(own.resolve("SKILL.md")));
  }

  @Test
  public void anAgentNamedWinsOverTheClient() throws Exception {
    call(DaemonMcpSkills.INSTALL, "{\"ref\":\"main\",\"agent\":\"junie\",\"names\":[\"one\"]}", "claude-code");
    assertTrue(Files.isRegularFile(home.resolve(".junie").resolve("skills").resolve("one").resolve("SKILL.md")));
    assertFalse(Files.exists(claudeSkills()));
  }

  @Test
  public void anUnknownClientMustNameItsAgent() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> call(DaemonMcpSkills.LIST, "{}", "some-client"));
    assertTrue(e.getMessage(), e.getMessage().contains("'agent'"));
  }

  /** The list comes from the network, and its paths become paths on disk: one leaving the skill's directory is refused. */
  @Test
  public void aListingReachingOutsideIsRefused() throws Exception {
    tree = "{\"tree\":[{\"path\":\"one/SKILL.md\",\"type\":\"blob\"},{\"path\":\"one/../../escape\",\"type\":\"blob\"}]}";
    IOException e = assertThrows(IOException.class, () -> call(DaemonMcpSkills.INSTALL, "{}", "claude-code"));
    assertTrue(e.getMessage(), e.getMessage().contains("not a relative file path"));
    assertFalse(Files.exists(home.resolve("escape")));

    tree = "{\"tree\":[{\"path\":\"../x/SKILL.md\",\"type\":\"blob\"}]}";
    assertThrows(IOException.class, () -> call(DaemonMcpSkills.INSTALL, "{}", "claude-code"));
  }

  @Test
  public void theDescriptionIsTheFrontmatters() {
    assertEquals("Plain.", DaemonMcpSkills.description("---\nname: x\ndescription: Plain.\n---\nBody\n"));
    assertEquals("Quoted: yes", DaemonMcpSkills.description("---\ndescription: \"Quoted: yes\"\nname: x\n---\n"));
    assertEquals("Folded over two lines.", DaemonMcpSkills.description("---\ndescription: >\n  Folded over\n  two lines.\nname: x\n---\n"));
    assertEquals("", DaemonMcpSkills.description("# no frontmatter\ndescription: not this\n"));
  }

  @Test
  public void aRefWithoutSkillsSaysSo() {
    IOException e = assertThrows(IOException.class, () -> call(DaemonMcpSkills.LIST, "{\"ref\":\"old\"}", "claude-code"));
    assertTrue(e.getMessage(), e.getMessage().startsWith("The arend-skills repository has no skills on 'old'"));
  }

  @Test
  public void anUnknownSkillIsNamed() {
    IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
        () -> call(DaemonMcpSkills.INSTALL, "{\"ref\":\"main\",\"names\":[\"nope\"]}", "claude-code"));
    assertTrue(e.getMessage(), e.getMessage().contains("no skill 'nope'"));
  }
}
