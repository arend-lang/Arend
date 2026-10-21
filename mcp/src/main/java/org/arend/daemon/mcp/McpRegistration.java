package org.arend.daemon.mcp;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.arend.daemon.Platform;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
 * <p>The command is {@code arend --mcp} when {@code arend} is on the PATH: the same on every machine,
 * so that the files can be committed, and it stays right when the jar behind it is rebuilt or moved.
 * Without one it is this machine's JVM and jar, which the caller has to say is not to be committed.
 *
 * <p>Earlier builds registered the server in the user's own config of every agent, as {@code arend -d}
 * started; {@link #unregisterUser} takes such a registration back out.
 */
public final class McpRegistration {
  public static final String SERVER_NAME = "arend-daemons";

  private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  /** VS Code's and Gemini's settings may hold comments; they are read, never written back. */
  private static final ObjectMapper LENIENT = new ObjectMapper().enable(JsonParser.Feature.ALLOW_COMMENTS);
  private static final long CLI_TIMEOUT_SECONDS = 30;
  private static final String CLI_MAIN = "org.arend.frontend.ConsoleMain";

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
   * {@code arend --mcp}, when {@code arend} is on the PATH; else this process's own JVM and classpath,
   * every path absolute, which only this machine has. No {@code -L}: one server serves the daemons
   * of every start, whatever each was given.
   */
  public static Command command() {
    Command portable = portableCommand();
    if (portable != null) return portable;
    List<String> classpath = new ArrayList<>();
    for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
      if (!entry.isEmpty()) classpath.add(Path.of(entry).toAbsolutePath().normalize().toString());
    }
    return new Command(command(Path.of(System.getProperty("java.home")), classpath, CLI_MAIN, "--mcp"), false);
  }

  /** {@code arend --mcp}, if there is an {@code arend} on the PATH; else null. */
  public static Command portableCommand() {
    return findExecutable("arend") == null ? null : new Command(List.of("arend", "--mcp"), true);
  }

  /**
   * The server as {@code mainClass} of {@code classpath} runs it on the JVM of {@code javaHome}. The
   * entries are taken as they are, so that one may be a directory's {@code *}.
   */
  public static List<String> command(Path javaHome, List<String> classpath, String mainClass, String... args) {
    List<String> command = new ArrayList<>();
    command.add(javaHome.resolve("bin").resolve("java").toString());
    command.add("-cp");
    command.add(String.join(File.pathSeparator, classpath));
    command.add(mainClass);
    command.addAll(List.of(args));
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

  /** Whether {@code server}, an agent's entry, runs {@code command}. Agents add fields of their own, an empty env among them. */
  static boolean runs(JsonNode server, List<String> command) {
    if (server == null || !server.isObject()) return false;
    List<String> registered = new ArrayList<>();
    registered.add(server.path("command").asText());
    server.path("args").forEach(arg -> registered.add(arg.asText()));
    return registered.equals(command);
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
            Claude::config),
        new ProjectConfig("Cursor", ".cursor/mcp.json", "mcpServers", McpRegistration::server,
            () -> Files.isDirectory(home().resolve(".cursor")), () -> cursor().config()),
        new ProjectConfig("VS Code", ".vscode/mcp.json", "servers", McpRegistration::server,
            () -> findExecutable("code") != null, VsCode::config),
        new ProjectConfig("Gemini CLI", ".gemini/settings.json", "mcpServers", McpRegistration::plainServer,
            () -> findExecutable("gemini") != null || Files.isDirectory(home().resolve(".gemini")),
            () -> home().resolve(".gemini").resolve("settings.json")),
        // Junie's IDE plugin does not show a server with a field it does not know, a type among them.
        new ProjectConfig("Junie", ".junie/mcp/mcp.json", "mcpServers", McpRegistration::plainServer,
            () -> Files.isDirectory(home().resolve(".junie")), () -> junie().config()));
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
          notes.add("Codex reads " + file + " only once it trusts the project: set trust_level = \"trusted\" under [projects.\""
              + projectDir.toAbsolutePath().normalize() + "\"] in " + userConfig + ".");
        }
      } catch (IOException e) {
        failures.add("could not register the " + SERVER_NAME + " MCP server in " + file + " (" + e.getMessage() + "); add to it:\n  "
            + String.join("\n  ", CodexConfig.table(command.argv())));
      }
    }
    if (!command.portable()) {
      notes.add("arend is not on the PATH, so the server is registered as " + String.join(" ", command.argv())
          + ", which only this machine has: keep these files out of version control, or put arend on the PATH and register again.");
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

  /** Removes {@link #SERVER_NAME} from every agent's config of the project in {@code projectDir}, keeping the rest. */
  public static Result unregisterProject(Path projectDir) {
    List<String> removed = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (ProjectConfig config : projectConfigs()) {
      Path file = projectDir.resolve(config.file());
      if (!hasServerIn(file, LENIENT, config.key())) continue;
      try {
        removeServer(file, config.key());
        removed.add(config.agent() + ": " + file);
      } catch (IOException e) {
        failures.add("could not remove the " + SERVER_NAME + " MCP server from " + file + " (" + e.getMessage() + "); remove \""
            + SERVER_NAME + "\" from its " + config.key() + " by hand");
      }
    }
    Path codex = projectDir.resolve(CODEX_PROJECT_CONFIG);
    if (CodexConfig.hasServer(codex)) {
      try {
        CodexConfig.removeServer(codex);
        removed.add("Codex: " + codex);
      } catch (IOException e) {
        failures.add("could not remove the " + SERVER_NAME + " MCP server from " + codex + " (" + e.getMessage() + "); remove its [mcp_servers."
            + SERVER_NAME + "] by hand");
      }
    }
    return new Result(removed, failures, List.of(), List.of());
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

  // ───────── the user's own configs, as earlier builds wrote them ─────────

  /** An agent's own config of the user's, from which a registration an earlier build made can be removed. */
  private interface UserConfig {
    String name();

    boolean installed();

    /** Whether it has a {@link #SERVER_NAME} of the user's, whatever that runs. */
    boolean hasServer();

    /** Removes the user's {@link #SERVER_NAME}; null when that worked, else what went wrong. */
    String unregister();

    /** What the user can do instead, when removing it failed. */
    String removeByHand();
  }

  /**
   * Removes {@link #SERVER_NAME} from the user's own config of every agent installed that has it,
   * whatever it runs: the registration that {@code arend -d} and the IDE's daemon made before
   * registering became a project's business.
   */
  public static Result unregisterUser() {
    List<String> removed = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (UserConfig agent : List.of(new Claude(), new Codex(), new Gemini(), cursor(), junie(), new VsCode())) {
      if (!agent.installed() || !agent.hasServer()) continue;
      String failure = agent.unregister();
      if (failure == null) {
        removed.add(agent.name());
      } else {
        failures.add("could not remove the " + SERVER_NAME + " MCP server from " + agent.name()
            + " (" + failure + "); " + agent.removeByHand());
      }
    }
    return new Result(removed, failures, List.of(), List.of());
  }

  /** An agent with a CLI of its own, which owns its config: the server is removed through it. */
  private abstract static class CliAgent implements UserConfig {
    private final String name;
    final Path cli;

    CliAgent(String name, Path cli) {
      this.name = name;
      this.cli = cli;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean installed() {
      return cli != null;
    }
  }

  private static final class Claude extends CliAgent {
    Claude() {
      super("Claude Code", findExecutable("claude", home().resolve(".claude").resolve("local")));
    }

    private static Path config() {
      String configDir = AgentHome.variable(home(), "CLAUDE_CONFIG_DIR");
      return configDir != null && !configDir.isEmpty() ? Path.of(configDir, ".claude.json") : home().resolve(".claude.json");
    }

    @Override
    public boolean hasServer() {
      return hasServerIn(config(), MAPPER, "mcpServers");
    }

    @Override
    public String unregister() {
      return run(cli, List.of("mcp", "remove", "--scope", "user", SERVER_NAME));
    }

    @Override
    public String removeByHand() {
      return "run: claude mcp remove --scope user " + SERVER_NAME;
    }
  }

  private static final class Codex extends CliAgent {
    Codex() {
      super("Codex", findExecutable("codex"));
    }

    @Override
    public boolean hasServer() {
      return run(cli, List.of("mcp", "get", SERVER_NAME, "--json")) == null;
    }

    @Override
    public String unregister() {
      return run(cli, List.of("mcp", "remove", SERVER_NAME));
    }

    @Override
    public String removeByHand() {
      return "run: codex mcp remove " + SERVER_NAME;
    }
  }

  private static final class Gemini extends CliAgent {
    Gemini() {
      super("Gemini CLI", findExecutable("gemini"));
    }

    @Override
    public boolean hasServer() {
      return hasServerIn(home().resolve(".gemini").resolve("settings.json"), LENIENT, "mcpServers");
    }

    @Override
    public String unregister() {
      return run(cli, List.of("mcp", "remove", "--scope", "user", SERVER_NAME));
    }

    @Override
    public String removeByHand() {
      return "run: gemini mcp remove --scope user " + SERVER_NAME;
    }
  }

  /** An agent with no CLI to remove a server through, whose {@code mcpServers} live in a JSON file of the user's: Cursor's and Junie's. */
  private record ConfigFile(String name, Path dir, Path config) implements UserConfig {
    @Override
    public boolean installed() {
      return Files.isDirectory(dir);
    }

    @Override
    public boolean hasServer() {
      return hasServerIn(config, MAPPER, "mcpServers");
    }

    @Override
    public String unregister() {
      try {
        removeServer(config, "mcpServers");
        return null;
      } catch (IOException e) {
        return e.getMessage();
      }
    }

    @Override
    public String removeByHand() {
      return "remove \"" + SERVER_NAME + "\" from the mcpServers of " + config;
    }
  }

  private static ConfigFile cursor() {
    Path dir = home().resolve(".cursor");
    return new ConfigFile("Cursor", dir, dir.resolve("mcp.json"));
  }

  private static ConfigFile junie() {
    Path dir = home().resolve(".junie");
    return new ConfigFile("Junie", dir, dir.resolve("mcp").resolve("mcp.json"));
  }

  private static final class VsCode extends CliAgent {
    VsCode() {
      super("VS Code", findExecutable("code"));
    }

    /** The default profile's mcp.json, where `code --add-mcp` puts a server. */
    private static Path config() {
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

    @Override
    public boolean hasServer() {
      return hasServerIn(config(), LENIENT, "servers");
    }

    /** By editing its mcp.json, which VS Code has no command for; one with comments is not rewritten, which would drop them. */
    @Override
    public String unregister() {
      try {
        removeServer(config(), "servers");
        return null;
      } catch (IOException e) {
        return e.getMessage();
      }
    }

    @Override
    public String removeByHand() {
      return "remove \"" + SERVER_NAME + "\" from the servers of " + config();
    }
  }

  // ───────── files ─────────

  /** Java's home, whose agents' configs these are; see {@link AgentHome}. */
  private static Path home() {
    return Path.of(System.getProperty("user.home"));
  }

  /** Whether the {@code key} object of {@code file} has a {@link #SERVER_NAME}; false when it cannot be read. */
  private static boolean hasServerIn(Path file, ObjectMapper mapper, String key) {
    try {
      return Files.isRegularFile(file) && mapper.readTree(file.toFile()).path(key).has(SERVER_NAME);
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Removes {@link #SERVER_NAME} from the {@code key} object of {@code file}, keeping the rest. Read
   * strictly: a file with comments would lose them, and is refused.
   */
  static void removeServer(Path file, String key) throws IOException {
    if (Files.isSymbolicLink(file)) file = file.toRealPath();
    if (!Files.exists(file)) return;
    JsonNode root = MAPPER.readTree(file.toFile());
    if (!(root.get(key) instanceof ObjectNode servers) || servers.remove(SERVER_NAME) == null) return;
    write(file, root);
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
    Path tmp = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
    try {
      MAPPER.writeValue(tmp.toFile(), root);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }

  // ───────── processes ─────────

  /**
   * {@code name} on the PATH, else in {@code ~/.local/bin} or one of {@code fallbacks}, where
   * installers put it. An IDE started from the desktop has the PATH of the session, which the
   * shell's profile has not extended, and {@code ~/.local/bin} is where Claude Code's and Codex's
   * installers put theirs.
   */
  private static Path findExecutable(String name, Path... fallbacks) {
    List<String> names = Platform.isWindows() ? List.of(name + ".exe", name + ".cmd", name + ".bat") : List.of(name);
    List<Path> dirs = new ArrayList<>();
    String path = System.getenv("PATH");
    if (path != null) {
      for (String dir : path.split(File.pathSeparator)) if (!dir.isEmpty()) dirs.add(Path.of(dir));
    }
    dirs.add(home().resolve(".local").resolve("bin"));
    dirs.addAll(List.of(fallbacks));
    for (Path dir : dirs) {
      for (String candidate : names) {
        Path file = dir.resolve(candidate);
        if (Files.isRegularFile(file) && Files.isExecutable(file)) return file;
      }
    }
    return null;
  }

  /**
   * Runs {@code cli} with {@code args} in the home directory, where no project's settings apply, and
   * on that home's configs ({@link AgentHome#pointAt}); null when it succeeded, else what went wrong.
   */
  static String run(Path cli, List<String> args) {
    List<String> command = new ArrayList<>();
    command.add(cli.toString());
    command.addAll(args);
    Path log = null;
    try {
      // To a file, not a pipe: a process blocked on a full pipe would outlive the timeout.
      log = Files.createTempFile("arend-mcp-register.", ".log");
      ProcessBuilder builder = new ProcessBuilder(command).directory(home().toFile()).redirectErrorStream(true)
          .redirectOutput(log.toFile());
      AgentHome.pointAt(home(), builder.environment());
      Process process = builder.start();
      process.getOutputStream().close();
      if (!process.waitFor(CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return cli.getFileName() + " did not answer within " + CLI_TIMEOUT_SECONDS + "s";
      }
      String output = Files.readString(log).strip();
      if (process.exitValue() == 0) return null;
      return output.isEmpty() ? "exit code " + process.exitValue() : output;
    } catch (IOException e) {
      return e.getMessage();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "interrupted";
    } finally {
      if (log != null) {
        try {
          Files.deleteIfExists(log);
        } catch (IOException ignored) {
        }
      }
    }
  }
}
