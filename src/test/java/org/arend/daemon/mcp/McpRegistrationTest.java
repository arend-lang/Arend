package org.arend.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.daemon.LockFile;
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

  /** Codex's table is written, replaced and removed by itself: its subtables go with it, every other line stays. */
  @Test
  public void codexsTableIsTheOnlyPartTouched() throws IOException {
    Path file = tmp.getRoot().toPath().resolve("codex").resolve("config.toml");
    Files.createDirectories(file.getParent());
    String others = """
        model = "o3"   # kept as it is

        [mcp_servers.other]
        command = "x"
        """;
    String projects = """
        [projects."/p"]
        trust_level = "trusted"
        """;
    Files.writeString(file, others + "\n[mcp_servers.arend-daemons]\ncommand = \"old\"\nargs = []\n\n[mcp_servers.arend-daemons.env]\nA = \"1\"\n\n" + projects);
    assertTrue(CodexConfig.hasServer(file));

    CodexConfig.setServer(file, List.of("arend", "--mcp"));
    String written = Files.readString(file);
    assertEquals(others + "\n" + projects + "\n[mcp_servers.arend-daemons]\ncommand = \"arend\"\nargs = [\"--mcp\"]\n", written);

    CodexConfig.removeServer(file);
    assertEquals(others + "\n" + projects, Files.readString(file));
    assertFalse(CodexConfig.hasServer(file));
  }

  /** A server defined inline, or a file a line-wise edit cannot read safely, is refused and left as it is. */
  @Test
  public void aCodexConfigThatCannotBeEditedByLinesIsLeftAlone() throws IOException {
    Path file = tmp.getRoot().toPath().resolve("config.toml");
    for (String text : List.of("[mcp_servers]\narend-daemons = { command = \"x\" }\n",
                               "mcp_servers.arend-daemons.command = \"x\"\n",
                               "instructions = \"\"\"\n[mcp_servers.arend-daemons]\n\"\"\"\n")) {
      Files.writeString(file, text);
      assertThrows(text, IOException.class, () -> CodexConfig.setServer(file, List.of("arend", "--mcp")));
      assertEquals(text, Files.readString(file));
    }
    Files.writeString(file, "[mcp_servers]\narend-daemons = { command = \"x\" }\n");
    assertTrue("an inline definition still counts as registered", CodexConfig.hasServer(file));
  }

  /** A string with a backslash or a quote, as a Windows path is, is escaped. */
  @Test
  public void codexsCommandIsEscaped() {
    assertEquals(List.of("[mcp_servers.arend-daemons]", "command = \"C:\\\\jdk\\\\bin\\\\java\"", "args = [\"-Da=\\\"b\\\"\"]"),
        CodexConfig.table(List.of("C:\\jdk\\bin\\java", "-Da=\"b\"")));
  }

  /** Codex gets a project's own config, and is told to trust the project unless it does already. */
  @Test
  public void codexIsRegisteredForAProjectItMustTrust() throws IOException {
    withAgentsInstalled(() -> {
      Path home = Path.of(System.getProperty("user.home"));
      Files.createDirectories(home.resolve(".codex"));
      Path project = tmp.newFolder("project").toPath();
      McpRegistration.Command portable = new McpRegistration.Command(List.of("arend", "--mcp"), true);
      McpRegistration.Result result = McpRegistration.initProject(project, portable);
      assertTrue(result.done().toString(), result.done().contains("Codex: " + project.resolve(".codex/config.toml")));
      assertTrue(CodexConfig.hasServer(project.resolve(".codex/config.toml")));
      assertTrue(result.notes().toString(), result.notes().stream().anyMatch(note -> note.contains("trust_level")));
      assertTrue("a project's directory is no agent's own config", result.warnings().isEmpty());

      // As Codex writes it, in literal strings, and in basic ones.
      for (String trust : List.of("[projects.'" + project + "']\ntrust_level = 'trusted'\n",
                                  "[projects.\"" + project + "\"]\ntrust_level = \"trusted\"\n")) {
        Files.writeString(home.resolve(".codex").resolve("config.toml"), trust);
        assertTrue(trust, McpRegistration.initProject(project, portable).notes().stream().noneMatch(note -> note.contains("trust_level")));
      }

      assertTrue(McpRegistration.unregisterProject(project).done().stream()
          .anyMatch(done -> done.startsWith("Codex: " + project.resolve(".codex/config.toml") + "\n")));
      assertFalse(CodexConfig.hasServer(project.resolve(".codex/config.toml")));
    });
  }

  /**
   * Registered for the home directory, the server lands in the agents' own configs, read in every
   * directory: that is written, and said, even when the home is reached through a link.
   */
  @Test
  public void registeringTheHomeWarnsOfEveryDirectory() throws IOException {
    withAgentsInstalled(() -> {
      Path home = Path.of(System.getProperty("user.home"));
      Files.createDirectories(home.resolve(".codex"));
      McpRegistration.Command portable = new McpRegistration.Command(List.of("arend", "--mcp"), true);
      List<Path> spellings = new java.util.ArrayList<>(List.of(home));
      if (!Platform.isWindows()) spellings.add(Files.createSymbolicLink(tmp.getRoot().toPath().resolve("home-link"), home));
      for (Path dir : spellings) {
        McpRegistration.Result result = McpRegistration.initProject(dir, portable);
        assertTrue(result.failures().toString(), result.failures().isEmpty());
        assertTrue(McpRegistration.runs(MAPPER.readTree(home.resolve(".cursor").resolve("mcp.json").toFile())
            .get("mcpServers").get(McpRegistration.SERVER_NAME), portable.argv()));
        // Cursor and Codex were made installed here; Gemini CLI and Junie may be on this machine too.
        assertTrue(dir + ": " + result.warnings(), result.warnings().stream().anyMatch(warning -> warning.contains("every Cursor session")));
        assertTrue(result.warnings().stream().anyMatch(warning -> warning.contains("every Codex session")));
        assertTrue("Claude Code's own config is ~/.claude.json, not ~/.mcp.json, and VS Code's is in its profile",
            result.warnings().stream().noneMatch(warning -> warning.contains("Claude") || warning.contains("VS Code")));
        assertTrue("no trust is needed for the user's own config", result.notes().stream().noneMatch(note -> note.contains("trust_level")));
      }
    });
  }

  /** A removal says what each entry was, as it can be put back: JSON where the agent's config is JSON, the table for Codex. */
  @Test
  public void aRemovalSaysWhatItRemoved() throws IOException {
    withAgentsInstalled(() -> {
      Files.createDirectories(Path.of(System.getProperty("user.home")).resolve(".codex"));
      Path project = tmp.newFolder("project").toPath();
      McpRegistration.initProject(project, new McpRegistration.Command(List.of("arend", "--mcp"), true));
      McpRegistration.Result removed = McpRegistration.unregisterProject(project);
      assertTrue(removed.failures().toString(), removed.failures().isEmpty());
      assertTrue(removed.done().toString(), removed.done().contains("Claude Code: " + project.resolve(".mcp.json")
          + "\n  the entry was, in mcpServers: \"arend-daemons\": {\"type\":\"stdio\",\"command\":\"arend\",\"args\":[\"--mcp\"]}"));
      assertTrue(removed.done().toString(), removed.done().contains("Codex: " + project.resolve(".codex/config.toml")
          + "\n  the table was:\n    [mcp_servers.arend-daemons]\n    command = \"arend\"\n    args = [\"--mcp\"]"));
      assertTrue("a project's directory is no agent's own config", removed.warnings().isEmpty());
    });
  }

  /** Unregistering a project leaves the user's own configs alone, a registration for every directory among them. */
  @Test
  public void unregisteringAProjectTouchesNothingElse() throws IOException {
    withAgentsInstalled(() -> {
      Path home = Path.of(System.getProperty("user.home"));
      McpRegistration.Command portable = new McpRegistration.Command(List.of("arend", "--mcp"), true);
      McpRegistration.initProject(home, portable);
      Path project = tmp.newFolder("project").toPath();
      McpRegistration.initProject(project, portable);
      McpRegistration.unregisterProject(project);
      assertTrue(McpRegistration.runs(MAPPER.readTree(home.resolve(".cursor").resolve("mcp.json").toFile())
          .get("mcpServers").get(McpRegistration.SERVER_NAME), portable.argv()));
    });
  }

  /** Unregistering the home directory edits the agents' own configs, and says that this reaches every directory. */
  @Test
  public void unregisteringTheHomeWarnsOfEveryDirectory() throws IOException {
    withAgentsInstalled(() -> {
      Path home = Path.of(System.getProperty("user.home"));
      Files.createDirectories(home.resolve(".codex"));
      McpRegistration.initProject(home, new McpRegistration.Command(List.of("arend", "--mcp"), true));
      McpRegistration.Result removed = McpRegistration.unregisterProject(home);
      assertTrue(removed.warnings().toString(), removed.warnings().stream().anyMatch(warning -> warning.contains("every Cursor session")));
      assertTrue(removed.warnings().toString(), removed.warnings().stream().anyMatch(warning -> warning.contains("every Codex session")));
      assertTrue(removed.warnings().stream().noneMatch(warning -> warning.contains("Claude") || warning.contains("VS Code")));
      assertFalse(MAPPER.readTree(home.resolve(".cursor").resolve("mcp.json").toFile()).get("mcpServers").has(McpRegistration.SERVER_NAME));
    });
  }

  /** The arend on the PATH agents see is asked which daemons it serves: this build's, another protocol's, or none at all. */
  @Test
  public void theArendOnThePathIsAskedForItsProtocol() throws IOException {
    Assume.assumeFalse(Platform.isWindows());
    Path bin = tmp.newFolder("bin").toPath();
    McpRegistration.ArendCheck none = McpRegistration.checkArend(bin.toString());
    assertNull(none.arend());
    assertTrue(none.problem(), none.problem().contains("no arend on the PATH"));

    Path arend = bin.resolve("arend");
    List<List<String>> answers = List.of(
        List.of("Arend 1.12", McpRegistration.PROTOCOL_LINE + LockFile.PROTOCOL_VERSION),
        List.of("Arend 1.12"),
        List.of("Arend 1.12", McpRegistration.PROTOCOL_LINE + (LockFile.PROTOCOL_VERSION + 1)));
    List<String> problems = new java.util.ArrayList<>();
    for (List<String> answer : answers) {
      Files.writeString(arend, "#!/bin/sh\n[ \"$1\" = --version ] || exit 2\nprintf '" + String.join("\\n", answer) + "\\n'\n");
      assertTrue(arend.toFile().setExecutable(true));
      McpRegistration.ArendCheck check = McpRegistration.checkArend(bin.toString());
      assertEquals(arend, check.arend());
      problems.add(check.problem());
    }
    assertNull("this build's protocol", problems.get(0));
    assertTrue(problems.get(1), problems.get(1).contains("too old") && problems.get(1).contains("\"Arend 1.12\""));
    assertTrue(problems.get(2), problems.get(2).contains("speaks daemon protocol " + (LockFile.PROTOCOL_VERSION + 1)));
    assertTrue(McpRegistration.arendAdvice(McpRegistration.checkArend(tmp.getRoot().toString())).contains("put `arend` on the PATH the agents see"));
  }
}
