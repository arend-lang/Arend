package org.arend.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.daemon.Platform;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** How {@code arend --mcp-init} registers the {@code arend-daemons} server for a project, and how it is taken back out. */
public class McpRegistrationTest {
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final List<String> COMMAND = List.of("/jdk/bin/java", "-cp", "/arend.jar", "org.arend.frontend.ConsoleMain", "--mcp");

  @Test
  public void theServerIsAStdioCommand() {
    assertEquals("{\"type\":\"stdio\",\"command\":\"/jdk/bin/java\",\"args\":[\"-cp\",\"/arend.jar\",\"org.arend.frontend.ConsoleMain\",\"--mcp\"]}",
        McpRegistration.server(COMMAND).toString());
  }

  /** As Junie's IDE plugin takes it: a type, which its CLI writes, hides the server from the plugin. */
  @Test
  public void junieGetsTheCommandAlone() {
    assertEquals("{\"command\":\"/jdk/bin/java\",\"args\":[\"-cp\",\"/arend.jar\",\"org.arend.frontend.ConsoleMain\",\"--mcp\"]}",
        McpRegistration.plainServer(COMMAND).toString());
    assertTrue(McpRegistration.runs(McpRegistration.plainServer(COMMAND), COMMAND));
  }

  /** As Junie's mcp.json is, before the first server: its directory may not be there yet. */
  @Test
  public void aConfigDirectoryIsCreated() throws IOException {
    Path file = tmp.getRoot().toPath().resolve("mcp").resolve("mcp.json");
    McpRegistration.setServer(file, "mcpServers", McpRegistration.plainServer(COMMAND));
    assertTrue(McpRegistration.runs(MAPPER.readTree(file.toFile()).get("mcpServers").get(McpRegistration.SERVER_NAME), COMMAND));
  }

  /** As agents write it back, with fields of their own: the same command is the same registration. */
  @Test
  public void theSameCommandIsRegisteredAlready() throws IOException {
    assertTrue(McpRegistration.runs(MAPPER.readTree("{\"type\":\"stdio\",\"command\":\"/jdk/bin/java\","
        + "\"args\":[\"-cp\",\"/arend.jar\",\"org.arend.frontend.ConsoleMain\",\"--mcp\"],\"env\":{},\"cwd\":null}"), COMMAND));
  }

  @Test
  public void anotherCommandOrNoneIsNot() throws IOException {
    assertFalse(McpRegistration.runs(null, COMMAND));
    assertFalse(McpRegistration.runs(MAPPER.readTree("\"arend --mcp\""), COMMAND));
    assertFalse(McpRegistration.runs(MAPPER.readTree("{\"command\":\"arend\",\"args\":[\"--mcp\"]}"), COMMAND));
  }

  /** As --mcp-unregister edits Cursor's mcp.json: only the server goes. */
  @Test
  public void removingTheServerKeepsTheRest() throws IOException {
    Path file = tmp.getRoot().toPath().resolve("mcp.json");
    Files.writeString(file, "{\"mcpServers\":{\"other\":{\"command\":\"x\"}},\"setting\":1}");
    McpRegistration.setServer(file, "mcpServers", McpRegistration.server(COMMAND));
    McpRegistration.removeServer(file, "mcpServers");
    JsonNode root = MAPPER.readTree(file.toFile());
    assertFalse(root.get("mcpServers").has(McpRegistration.SERVER_NAME));
    assertEquals("x", root.get("mcpServers").get("other").get("command").asText());
    assertEquals(1, root.get("setting").asInt());
  }

  @Test
  public void removingFromNoFileOrNoServerChangesNothing() throws IOException {
    Path missing = tmp.getRoot().toPath().resolve("missing.json");
    McpRegistration.removeServer(missing, "mcpServers");
    assertFalse(Files.exists(missing));

    Path file = tmp.getRoot().toPath().resolve("mcp.json");
    String text = "{ \"mcpServers\" : { \"other\" : { } } }";
    Files.writeString(file, text);
    McpRegistration.removeServer(file, "mcpServers");
    assertEquals(text, Files.readString(file));
  }

  /** VS Code's mcp.json may have comments, which rewriting would drop: it is left for the user to edit. */
  @Test
  public void aConfigWithCommentsIsNotRewritten() throws IOException {
    Path file = tmp.getRoot().toPath().resolve("mcp.json");
    String text = "{\n  // mine\n  \"servers\": {\"arend-daemons\": {\"command\": \"x\"}}\n}";
    Files.writeString(file, text);
    assertThrows(IOException.class, () -> McpRegistration.removeServer(file, "servers"));
    assertEquals(text, Files.readString(file));
  }

  /** As Cursor's mcp.json is edited: its other servers and settings stay. */
  @Test
  public void aConfigKeepsItsOtherServers() throws IOException {
    Path file = tmp.getRoot().toPath().resolve("mcp.json");
    Files.writeString(file, "{\"mcpServers\":{\"other\":{\"command\":\"x\"}},\"setting\":1}");
    McpRegistration.setServer(file, "mcpServers", McpRegistration.server(COMMAND));
    JsonNode root = MAPPER.readTree(file.toFile());
    assertEquals(1, root.get("setting").asInt());
    assertEquals("x", root.get("mcpServers").get("other").get("command").asText());
    assertTrue(McpRegistration.runs(root.get("mcpServers").get(McpRegistration.SERVER_NAME), COMMAND));
  }

  @Test
  public void aMissingConfigIsCreated() throws IOException {
    Path file = tmp.getRoot().toPath().resolve("mcp.json");
    McpRegistration.setServer(file, "mcpServers", McpRegistration.server(COMMAND));
    assertTrue(McpRegistration.runs(MAPPER.readTree(file.toFile()).get("mcpServers").get(McpRegistration.SERVER_NAME), COMMAND));
  }

  @Test
  public void aConfigThatIsNotJsonIsLeftAlone() throws IOException {
    Path file = tmp.getRoot().toPath().resolve("mcp.json");
    Files.writeString(file, "not json");
    assertThrows(IOException.class, () -> McpRegistration.setServer(file, "mcpServers", McpRegistration.server(COMMAND)));
    assertEquals("not json", Files.readString(file));
  }

  /** A home with Claude Code's and Cursor's directories, so that they count as installed, for the length of {@code body}. */
  private void withAgentsInstalled(IORunnable body) throws IOException {
    Path home = tmp.newFolder("home").toPath();
    Files.createDirectories(home.resolve(".claude"));
    Files.createDirectories(home.resolve(".cursor"));
    String realHome = System.getProperty("user.home");
    System.setProperty("user.home", home.toString());
    try {
      body.run();
    } finally {
      System.setProperty("user.home", realHome);
    }
  }

  private interface IORunnable {
    void run() throws IOException;
  }

  /**
   * The project's own configs, and nothing else: an agent started elsewhere does not see the server,
   * and the command written is the one given, which `arend --mcp` is when arend is on the PATH.
   */
  @Test
  public void aProjectIsRegisteredInItsOwnConfigs() throws IOException {
    withAgentsInstalled(() -> {
      Path project = tmp.newFolder("project").toPath();
      McpRegistration.Command portable = new McpRegistration.Command(List.of("arend", "--mcp"), true);
      McpRegistration.Result result = McpRegistration.initProject(project, portable);
      assertTrue(result.failures().toString(), result.failures().isEmpty());
      JsonNode claude = MAPPER.readTree(project.resolve(".mcp.json").toFile());
      assertTrue(McpRegistration.runs(claude.get("mcpServers").get(McpRegistration.SERVER_NAME), portable.argv()));
      JsonNode cursor = MAPPER.readTree(project.resolve(".cursor").resolve("mcp.json").toFile());
      assertTrue(McpRegistration.runs(cursor.get("mcpServers").get(McpRegistration.SERVER_NAME), portable.argv()));
      assertFalse("nothing of the user's own is written", Files.exists(Path.of(System.getProperty("user.home")).resolve(".claude.json")));
      assertTrue(result.notes().stream().noneMatch(note -> note.contains("version control")));

      McpRegistration.Result removed = McpRegistration.unregisterProject(project);
      assertTrue(removed.done().toString(), removed.done().size() >= 2);
      assertFalse(MAPPER.readTree(project.resolve(".mcp.json").toFile()).get("mcpServers").has(McpRegistration.SERVER_NAME));
    });
  }

  /** A command naming this machine's java and jar is said not to belong in version control. */
  @Test
  public void aCommandOfThisMachineIsSaidToBeSo() throws IOException {
    withAgentsInstalled(() -> {
      Path project = tmp.newFolder("project").toPath();
      McpRegistration.Result result = McpRegistration.initProject(project, new McpRegistration.Command(COMMAND, false));
      assertTrue(result.notes().toString(), result.notes().stream().anyMatch(note -> note.contains("version control")));
    });
  }

  /**
   * An agent's CLI works on the configs of the home this process was given, not on the user's: run
   * with the user's HOME, --mcp-unregister under a test's home removed the server from the user's real
   * Codex config.
   */
  @Test
  public void anAgentsCliRunsOnTheHomeGiven() throws IOException {
    Assume.assumeFalse(Platform.isWindows());
    Path cli = tmp.getRoot().toPath().resolve("agent");
    Files.writeString(cli, "#!/bin/sh\nprintf '%s\\n%s\\n' \"$HOME\" \"$CODEX_HOME\" > \"$1\"\n");
    assertTrue(cli.toFile().setExecutable(true));
    Path seen = tmp.getRoot().toPath().resolve("seen");
    withAgentsInstalled(() -> {
      Path home = Path.of(System.getProperty("user.home"));
      assertNull(McpRegistration.run(cli, List.of(seen.toString())));
      assertEquals(List.of(home.toString(), home.resolve(".codex").toString()), Files.readAllLines(seen));
    });
  }

  /** Without an arend on the PATH the command is this one's own, every path absolute so that it runs from anywhere. */
  @Test
  public void theCommandIsArendOrThisOneFromAnywhere() {
    McpRegistration.Command command = McpRegistration.command();
    if (command.portable()) {
      assertEquals(List.of("arend", "--mcp"), command.argv());
      return;
    }
    List<String> argv = command.argv();
    assertTrue(argv.getFirst(), Path.of(argv.getFirst()).isAbsolute());
    for (String entry : argv.get(2).split(File.pathSeparator)) assertTrue(entry, Path.of(entry).isAbsolute());
    assertEquals(List.of("org.arend.frontend.ConsoleMain", "--mcp"), argv.subList(3, argv.size()));
  }
}
