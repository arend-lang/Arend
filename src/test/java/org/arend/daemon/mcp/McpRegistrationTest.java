package org.arend.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** What {@code arend -d} registers as the {@code arend-daemons} server, and how it tells an agent has it. */
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

  /** The CLI's server, or the plugin's from a directory's *, left alone while its files are there. */
  @Test
  public void anotherArendServerThatStartsServesTheDaemons() throws IOException {
    Path java = tmp.newFolder("jdk", "bin").toPath().resolve("java");
    Files.createFile(java);
    assertTrue(java.toFile().setExecutable(true));
    Path jar = tmp.newFile("cli-full.jar").toPath();
    Path lib = tmp.newFolder("plugin", "lib").toPath();

    assertTrue(McpRegistration.servesDaemons(McpRegistration.server(
        List.of(java.toString(), "-cp", jar.toString(), "org.arend.frontend.ConsoleMain", "--mcp")), COMMAND));
    assertTrue(McpRegistration.servesDaemons(McpRegistration.server(
        List.of(java.toString(), "-cp", lib + File.separator + "*" + File.pathSeparator + jar, DaemonMcpServer.class.getName())), COMMAND));
  }

  @Test
  public void anArendServerWithFilesGoneOrAnotherServerIsReplaced() throws IOException {
    Path java = tmp.newFolder("jdk", "bin").toPath().resolve("java");
    Files.createFile(java);
    assertTrue(java.toFile().setExecutable(true));
    Path jar = tmp.newFile("cli-full.jar").toPath();
    String gone = tmp.getRoot().toPath().resolve("gone.jar").toString();

    assertFalse(McpRegistration.servesDaemons(McpRegistration.server(
        List.of(java.toString(), "-cp", jar + File.pathSeparator + gone, "org.arend.frontend.ConsoleMain", "--mcp")), COMMAND));
    assertFalse(McpRegistration.servesDaemons(McpRegistration.server(
        List.of(tmp.getRoot().toPath().resolve("no-java").toString(), "-cp", jar.toString(), "org.arend.frontend.ConsoleMain", "--mcp")), COMMAND));
    assertFalse(McpRegistration.servesDaemons(McpRegistration.server(
        List.of(java.toString(), "-cp", tmp.getRoot() + File.separator + "nowhere" + File.separator + "*", DaemonMcpServer.class.getName())), COMMAND));
    assertFalse(McpRegistration.servesDaemons(McpRegistration.server(
        List.of(java.toString(), "-cp", jar.toString(), "org.example.Other")), COMMAND));
    assertFalse(McpRegistration.servesDaemons(MAPPER.readTree("{\"command\":\"arend\",\"args\":[\"--mcp\"]}"), COMMAND));
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

  @Test
  public void theCommandRunsThisArendFromAnywhere() {
    List<String> command = McpRegistration.command();
    assertTrue(command.getFirst(), Path.of(command.getFirst()).isAbsolute());
    for (String entry : command.get(2).split(File.pathSeparator)) assertTrue(entry, Path.of(entry).isAbsolute());
    assertEquals(List.of("org.arend.frontend.ConsoleMain", "--mcp"), command.subList(3, command.size()));
  }
}
