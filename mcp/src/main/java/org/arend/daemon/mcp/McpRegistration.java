package org.arend.daemon.mcp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.arend.daemon.LockFile;
import org.arend.daemon.Platform;
import org.arend.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Registers the {@code arend-daemons} MCP server for one Arend project, with the agents installed
 * that read a project's own config: Claude Code ({@code .mcp.json}), Cursor ({@code .cursor/mcp.json}),
 * VS Code ({@code .vscode/mcp.json}), Gemini CLI ({@code .gemini/settings.json}), Junie
 * ({@code .junie/mcp/mcp.json}) and Codex ({@code .codex/config.toml}). An agent started in another
 * directory does not see it, and nothing outside the project is written. Only when the user asks for
 * it -- {@code arend --mcp-init}, or the IDE's Daemon tab -- never as the side effect of starting a daemon.
 *
 * <p>For some agents the project's file, in the home directory, is the agent's own config of the
 * user's, which it reads in every directory. Writing it is the user's call; the result warns that
 * every session of that agent will then start the server.
 *
 * <p>The command is {@code arend --mcp}: the same on every machine, so that the files can be
 * committed, and it stays right when the jar behind it is rebuilt or moved. Registering it takes a
 * current {@code arend} on the PATH the agents see ({@link #checkArend}). The IDE can register its own
 * copy instead, when the user chooses to, which only this machine has.
 *
 * <p>Only a project's files are touched, by a registration and by its removal alike. A registration
 * in an agent's own config elsewhere is the user's to remove, through the agent.
 */
public final class McpRegistration {
  public static final String SERVER_NAME = "arend-daemons";

  /** What a registration that wrote nothing, and failed at nothing, says. */
  public static final String NO_AGENT = "No agent that reads a project's own MCP config is installed.";

  private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  /** VS Code's and Gemini's settings may hold comments; they are read, never written back. */
  private static final ObjectMapper LENIENT = new ObjectMapper().enable(JsonParser.Feature.ALLOW_COMMENTS);

  private McpRegistration() {}

  /**
   * What a registration or a removal did: the files it wrote, or the agents it removed the server
   * from; for each it could not, why and what the user can do instead; what it did that reaches
   * further than the user may think; and what else they should know.
   */
  public record Result(List<String> done, List<String> failures, List<String> warnings, List<String> notes) {}

  /** The command line the server is registered with, and whether it is the same on every machine. */
  public record Command(List<String> argv, boolean portable) {}

  // ───────── the command ─────────

  /**
   * {@code arend --mcp}: the same on every machine. No {@code -L}: one server serves the daemons of
   * every start, whatever each was given.
   */
  public static final Command PORTABLE = new Command(List.of("arend", "--mcp"), true);

  /** What {@code arend --version} says of the daemons a build serves, on its second line. */
  public static final String PROTOCOL_LINE = "daemon protocol ";

  private static final long VERSION_TIMEOUT_SECONDS = 15;

  /**
   * The {@code arend} an agent would start the server as, on the {@code path} it sees, and what is in
   * the way of registering {@link #PORTABLE}: null when nothing is.
   */
  public record ArendCheck(Path arend, String problem) {
    public boolean usable() {
      return problem == null;
    }
  }

  /**
   * Checks the {@code arend} on {@code path} -- the PATH agents and daemons are started with, which an
   * IDE started from the desktop need not have -- by asking it for its version: a build that does not
   * speak this one's daemon protocol, or says none, has no server for this build's daemons.
   */
  public static ArendCheck checkArend(String path) {
    Path arend = findOnPath(path, "arend");
    if (arend == null) return new ArendCheck(null, "there is no arend on the PATH the agents see");
    Path log = null;
    try {
      // To a file, not a pipe: a process blocked on a full pipe would outlive the timeout.
      log = Files.createTempFile("arend-version.", ".log");
      ProcessBuilder builder = new ProcessBuilder(arend.toString(), "--version").redirectErrorStream(true).redirectOutput(log.toFile());
      // The PATH the agents see, which the arend launcher may need to find its java on.
      if (path != null) builder.environment().put("PATH", path);
      Process process = builder.start();
      process.getOutputStream().close();
      if (!process.waitFor(VERSION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return new ArendCheck(arend, arend + " --version did not answer within " + VERSION_TIMEOUT_SECONDS + "s");
      }
      List<String> lines = Files.readAllLines(log);
      String protocol = lines.stream().filter(line -> line.startsWith(PROTOCOL_LINE)).findFirst().orElse(null);
      if (protocol == null) {
        String said = lines.isEmpty() ? "nothing" : lines.getFirst().strip();
        return new ArendCheck(arend, arend + " is too old to serve this build's daemons: its --version says only \"" + said + "\", with no daemon protocol");
      }
      String speaks = protocol.substring(PROTOCOL_LINE.length()).strip();
      if (!speaks.equals(String.valueOf(LockFile.PROTOCOL_VERSION))) {
        return new ArendCheck(arend, arend + " speaks daemon protocol " + speaks + ", and this build's daemons speak " + LockFile.PROTOCOL_VERSION);
      }
      return new ArendCheck(arend, null);
    } catch (IOException e) {
      return new ArendCheck(arend, arend + " --version could not be run (" + e.getMessage() + ")");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new ArendCheck(arend, "interrupted");
    } finally {
      if (log != null) {
        try {
          Files.deleteIfExists(log);
        } catch (IOException ignored) {
        }
      }
    }
  }

  /** What to do about {@code check}'s problem, said the same way by the CLI and the IDE. */
  public static String arendAdvice(ArendCheck check) {
    return "Agents start the " + SERVER_NAME + " MCP server as `arend --mcp`, but " + check.problem()
        + ". Install a current Arend CLI and put `arend` on the PATH the agents see, then register again.";
  }

  /**
   * The server as {@code mainClass} of {@code classpath} runs it on the JVM of {@code javaHome}. The
   * entries are taken as they are, so that one may be a directory's {@code *}.
   */
  public static List<String> command(Path javaHome, List<String> classpath, String mainClass) {
    List<String> command = new ArrayList<>();
    command.add(javaHome.resolve("bin").resolve("java").toString());
    command.add("-cp");
    command.add(String.join(File.pathSeparator, classpath));
    command.add(mainClass);
    return command;
  }

  /** The stdio server running {@code command}, as the {@code mcpServers} of Claude Code and Cursor, and VS Code's {@code servers}, have it. */
  static ObjectNode server(List<String> command) {
    ObjectNode server = MAPPER.createObjectNode();
    server.put("type", "stdio");
    server.put("command", command.getFirst());
    command.subList(1, command.size()).forEach(server.putArray("args")::add);
    return server;
  }

  /** The server running {@code command} with nothing but its command and arguments, as Gemini CLI and Junie take it. */
  static ObjectNode plainServer(List<String> command) {
    ObjectNode server = server(command);
    server.remove("type");
    return server;
  }

  // ───────── a project's own configs ─────────

  /**
   * An agent's config of a project: the file under the project's directory, the key of its servers,
   * the entry it takes, whether the agent is installed on this machine, and the agent's own config of
   * the user's, which the project's file is when the project is the home directory.
   */
  record ProjectConfig(String agent, String file, String key, Function<List<String>, ObjectNode> entry, BooleanSupplier installed,
                       Supplier<Path> userConfig) {}

  static List<ProjectConfig> projectConfigs() {
    return List.of(
        new ProjectConfig("Claude Code", ".mcp.json", "mcpServers", McpRegistration::server,
            () -> findExecutable("claude", home().resolve(".claude").resolve("local")) != null || Files.isDirectory(home().resolve(".claude")),
            McpRegistration::claudeUserConfig),
        new ProjectConfig("Cursor", ".cursor/mcp.json", "mcpServers", McpRegistration::server,
            () -> Files.isDirectory(home().resolve(".cursor")), () -> home().resolve(".cursor").resolve("mcp.json")),
        new ProjectConfig("VS Code", ".vscode/mcp.json", "servers", McpRegistration::server,
            () -> findExecutable("code") != null, McpRegistration::vsCodeUserConfig),
        new ProjectConfig("Gemini CLI", ".gemini/settings.json", "mcpServers", McpRegistration::plainServer,
            () -> findExecutable("gemini") != null || Files.isDirectory(home().resolve(".gemini")),
            () -> home().resolve(".gemini").resolve("settings.json")),
        // Junie's IDE plugin does not show a server with a field it does not know, a type among them.
        new ProjectConfig("Junie", ".junie/mcp/mcp.json", "mcpServers", McpRegistration::plainServer,
            () -> Files.isDirectory(home().resolve(".junie")), () -> home().resolve(".junie").resolve("mcp").resolve("mcp.json")));
  }

  /** Codex's config of a project, TOML rather than JSON: read for a project Codex trusts only. */
  static final String CODEX_PROJECT_CONFIG = ".codex/config.toml";

  /** Where Codex keeps the user's own config: {@code CODEX_HOME}, else {@code ~/.codex}. */
  static Path codexHome() {
    String dir = AgentHome.variable(home(), "CODEX_HOME");
    return dir != null && !dir.isEmpty() ? Path.of(dir) : home().resolve(".codex");
  }

  private static boolean codexInstalled() {
    return findExecutable("codex") != null || Files.isDirectory(codexHome());
  }

  /**
   * Registers {@code command} as {@link #SERVER_NAME} in the configs of the project in {@code projectDir}
   * of every agent installed, keeping their other servers and settings; a registration of the name
   * there is replaced. A file that is an agent's own config of the user's is written too, with a
   * warning of what that does.
   */
  public static Result initProject(Path projectDir, Command command) {
    List<String> written = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    List<String> notes = new ArrayList<>();
    for (ProjectConfig config : projectConfigs()) {
      if (!config.installed().getAsBoolean()) continue;
      Path file = projectDir.resolve(config.file());
      try {
        setServer(file, config.key(), config.entry().apply(command.argv()));
        written.add(config.agent() + ": " + file);
        if (canonical(file).equals(canonical(config.userConfig().get()))) warnings.add(userWide(config.agent(), file, projectDir));
      } catch (IOException e) {
        failures.add("could not register the " + SERVER_NAME + " MCP server in " + file + " (" + e.getMessage() + "); add to its "
            + config.key() + ": \"" + SERVER_NAME + "\": " + config.entry().apply(command.argv()));
      }
    }
    if (codexInstalled()) {
      Path file = projectDir.resolve(CODEX_PROJECT_CONFIG);
      Path userConfig = codexHome().resolve("config.toml");
      try {
        CodexConfig.setServer(file, command.argv());
        written.add("Codex: " + file);
        if (canonical(file).equals(canonical(userConfig))) {
          warnings.add(userWide("Codex", file, projectDir));
        } else if (!CodexConfig.trusts(userConfig, projectDir)) {
          notes.add("Codex reads " + file + " only for a project it trusts: trust " + projectDir.toAbsolutePath().normalize()
              + " when Codex asks, started there, which it records in " + userConfig + " as trust_level = 'trusted' under [projects.'"
              + projectDir.toAbsolutePath().normalize() + "'].");
        }
      } catch (IOException e) {
        failures.add("could not register the " + SERVER_NAME + " MCP server in " + file + " (" + e.getMessage() + "); add to it:\n  "
            + String.join("\n  ", CodexConfig.table(command.argv())));
      }
    }
    if (!command.portable()) {
      notes.add("The server is registered as " + String.join(" ", command.argv()) + ", which only this machine has: keep"
          + " these files out of version control, or put a current arend on the PATH and register again.");
    }
    return new Result(written, failures, warnings, notes);
  }

  /** The warning that {@code file}, written as the config of the project in {@code projectDir}, is {@code agent}'s own config of the user's. */
  private static String userWide(String agent, Path file, Path projectDir) {
    return file + " is " + agent + "'s own config of the user's, read in every directory: every " + agent
        + " session, in any project, now starts the " + SERVER_NAME + " MCP server (a JVM of its own) and carries the"
        + " descriptions of its tools. To have it in this project only, register a project's directory instead;"
        + " `arend --mcp-unregister " + projectDir.toAbsolutePath().normalize() + "` takes this registration back out.";
  }

  /**
   * Removes {@link #SERVER_NAME} from every agent's config of the project in {@code projectDir}, keeping
   * the rest, and from nothing else. Each removal says what the entry was, so that it can be put back;
   * a file that is an agent's own config of the user's is edited too, with a warning of what that does.
   */
  public static Result unregisterProject(Path projectDir) {
    List<String> removed = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    for (ProjectConfig config : projectConfigs()) {
      Path file = projectDir.resolve(config.file());
      if (!hasServerIn(file, config.key())) continue;
      try {
        JsonNode entry = removeServer(file, config.key());
        removed.add(config.agent() + ": " + file + "\n  the entry was, in " + config.key() + ": \"" + SERVER_NAME + "\": " + entry);
        if (canonical(file).equals(canonical(config.userConfig().get()))) warnings.add(userWideRemoval(config.agent(), file));
      } catch (IOException e) {
        failures.add("could not remove the " + SERVER_NAME + " MCP server from " + file + " (" + e.getMessage() + "); remove \""
            + SERVER_NAME + "\" from its " + config.key() + " by hand");
      }
    }
    Path codex = projectDir.resolve(CODEX_PROJECT_CONFIG);
    if (CodexConfig.hasServer(codex)) {
      try {
        List<String> table = CodexConfig.removeServer(codex);
        removed.add("Codex: " + codex + "\n  the table was:\n    " + String.join("\n    ", table));
        if (canonical(codex).equals(canonical(codexHome().resolve("config.toml")))) warnings.add(userWideRemoval("Codex", codex));
      } catch (IOException e) {
        failures.add("could not remove the " + SERVER_NAME + " MCP server from " + codex + " (" + e.getMessage() + "); remove its [mcp_servers."
            + SERVER_NAME + "] by hand");
      }
    }
    return new Result(removed, failures, warnings, List.of());
  }

  /** The warning that {@code file}, a project's config the server was removed from, is {@code agent}'s own config of the user's. */
  private static String userWideRemoval(String agent, Path file) {
    return file + " is " + agent + "'s own config of the user's, read in every directory: the " + SERVER_NAME
        + " MCP server is gone from every " + agent + " session, in any project, not only from this directory's.";
  }

  /**
   * {@code path} with every link resolved, as far as it exists, so that two spellings of one file --
   * a home reached through a link, a relative directory -- compare equal; the part that does not
   * exist yet is taken as it is.
   */
  static Path canonical(Path path) {
    Path absolute = path.toAbsolutePath().normalize();
    Path existing = absolute;
    while (existing != null && !Files.exists(existing)) existing = existing.getParent();
    if (existing == null) return absolute;
    try {
      return existing.toRealPath().resolve(existing.relativize(absolute));
    } catch (IOException e) {
      return absolute;
    }
  }

  // ───────── the user's own configs, which a project's file may be ─────────

  /** Claude Code's own config of the user's: never a project's {@code .mcp.json}, but said for every agent alike. */
  private static Path claudeUserConfig() {
    String configDir = AgentHome.variable(home(), "CLAUDE_CONFIG_DIR");
    return configDir != null && !configDir.isEmpty() ? Path.of(configDir, ".claude.json") : home().resolve(".claude.json");
  }

  /** VS Code's default profile's mcp.json, where `code --add-mcp` puts a server. */
  private static Path vsCodeUserConfig() {
    Path user;
    if (Platform.isWindows()) {
      String appData = AgentHome.variable(home(), "APPDATA");
      user = Path.of(appData != null ? appData : home().resolve("AppData").resolve("Roaming").toString(), "Code", "User");
    } else if (Platform.isMac()) {
      user = home().resolve("Library").resolve("Application Support").resolve("Code").resolve("User");
    } else {
      String configHome = AgentHome.variable(home(), "XDG_CONFIG_HOME");
      user = (configHome != null && !configHome.isEmpty() ? Path.of(configHome) : home().resolve(".config")).resolve("Code").resolve("User");
    }
    return user.resolve("mcp.json");
  }

  // ───────── files ─────────

  /** Java's home, whose agents' configs these are; see {@link AgentHome}. */
  private static Path home() {
    return Path.of(System.getProperty("user.home"));
  }

  /** Whether the {@code key} object of {@code file}, read leniently, has a {@link #SERVER_NAME}; false when it cannot be read. */
  private static boolean hasServerIn(Path file, String key) {
    try {
      return Files.isRegularFile(file) && LENIENT.readTree(file.toFile()).path(key).has(SERVER_NAME);
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Removes {@link #SERVER_NAME} from the {@code key} object of {@code file}, keeping the rest; the entry
   * removed, or null when there was none. Read strictly: a file with comments would lose them, and is refused.
   */
  static JsonNode removeServer(Path file, String key) throws IOException {
    if (Files.isSymbolicLink(file)) file = file.toRealPath();
    if (!Files.exists(file)) return null;
    JsonNode root = MAPPER.readTree(file.toFile());
    if (!(root.get(key) instanceof ObjectNode servers)) return null;
    JsonNode entry = servers.remove(SERVER_NAME);
    if (entry != null) write(file, root);
    return entry;
  }

  /**
   * Sets {@code server} as {@link #SERVER_NAME} in the {@code key} object of {@code file}, keeping the
   * rest. Read strictly too: a file with comments, which VS Code's and Gemini's may have, is refused.
   */
  static void setServer(Path file, String key, ObjectNode server) throws IOException {
    // A config kept elsewhere and linked here is written where it is kept, the link left as it is.
    if (Files.isSymbolicLink(file)) file = file.toRealPath();
    ObjectNode root;
    if (Files.exists(file)) {
      JsonNode read = MAPPER.readTree(file.toFile());
      if (!(read instanceof ObjectNode object)) throw new IOException(file + " is not a JSON object");
      root = object;
    } else {
      root = MAPPER.createObjectNode();
    }
    JsonNode servers = root.get(key);
    if (servers == null || servers.isNull()) servers = root.putObject(key);
    if (!(servers instanceof ObjectNode serversObject)) throw new IOException("the " + key + " of " + file + " is not a JSON object");
    serversObject.set(SERVER_NAME, server);
    write(file, root);
  }

  /** Writes {@code root} to {@code file} whole or not at all. */
  private static void write(Path file, JsonNode root) throws IOException {
    Files.createDirectories(file.getParent());
    FileUtils.writeAtomically(file, scratch -> MAPPER.writeValue(scratch.toFile(), root));
  }

  // ───────── installed agents ─────────

  /**
   * {@code name} on the PATH, else in {@code ~/.local/bin} or one of {@code fallbacks}, where
   * installers put it. An IDE started from the desktop has the PATH of the session, which the
   * shell's profile has not extended, and {@code ~/.local/bin} is where Claude Code's and Codex's
   * installers put theirs.
   */
  private static Path findExecutable(String name, Path... fallbacks) {
    List<Path> dirs = new ArrayList<>(dirsOf(System.getenv("PATH")));
    dirs.add(home().resolve(".local").resolve("bin"));
    dirs.addAll(List.of(fallbacks));
    return findIn(dirs, name);
  }

  /** {@code name} on {@code path}, a PATH variable's value, and nowhere else. */
  static Path findOnPath(String path, String name) {
    return findIn(dirsOf(path), name);
  }

  private static List<Path> dirsOf(String path) {
    List<Path> dirs = new ArrayList<>();
    if (path != null) {
      for (String dir : path.split(File.pathSeparator)) if (!dir.isEmpty()) dirs.add(Path.of(dir));
    }
    return dirs;
  }

  private static Path findIn(List<Path> dirs, String name) {
    List<String> names = Platform.isWindows() ? List.of(name + ".exe", name + ".cmd", name + ".bat") : List.of(name);
    for (Path dir : dirs) {
      for (String candidate : names) {
        Path file = dir.resolve(candidate);
        if (Files.isRegularFile(file) && Files.isExecutable(file)) return file;
      }
    }
    return null;
  }
}
