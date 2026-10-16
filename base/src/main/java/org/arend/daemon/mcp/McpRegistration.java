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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Registers the {@code arend-daemons} MCP server for the user with every agent found on the
 * machine -- Claude Code, Codex, Gemini CLI, Cursor, Junie and VS Code -- so that an agent started
 * in any directory has every running daemon at hand. The server's command runs this very {@code arend},
 * or the IntelliJ plugin's jars, whether or not {@code arend} is on the agent's PATH, and finds the
 * daemons through the registry, so one registration serves them all, the IDE's among them.
 *
 * <p>An agent with a CLI of its own is registered through it, since it owns its config; its config
 * is only read, to leave it alone when it already runs an Arend server that starts. Cursor and Junie have
 * none, and get their JSON config edited, its other servers kept. An agent not installed is
 * skipped, and nothing here fails a daemon start: a failure is a warning. {@link #unregister} takes
 * the server back out of every agent that has it.
 */
public final class McpRegistration {
  public static final String SERVER_NAME = "arend-daemons";

  private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  /** VS Code's and Gemini's settings may hold comments; they are read, never written back. */
  private static final ObjectMapper LENIENT = new ObjectMapper().enable(JsonParser.Feature.ALLOW_COMMENTS);
  private static final long CLI_TIMEOUT_SECONDS = 30;
  private static final String CLI_MAIN = "org.arend.frontend.ConsoleMain";

  private McpRegistration() {}

  /** An agent the server can be registered with. */
  private interface Agent {
    String name();

    /** Whether the agent is installed here. */
    boolean installed();

    /** Whether it already runs {@code command}, or another Arend server that starts, as the user's {@link #SERVER_NAME}. */
    boolean registered(List<String> command);

    /** Registers {@code command}; null when that worked, else what went wrong. */
    String register(List<String> command);

    /** What the user can do instead, when registering failed. */
    String byHand(List<String> command);

    /** Whether it has a {@link #SERVER_NAME} of the user's, whatever that runs. */
    boolean hasServer();

    /** Removes the user's {@link #SERVER_NAME}; null when that worked, else what went wrong. */
    String unregister();

    /** What the user can do instead, when removing it failed. */
    String removeByHand();
  }

  /**
   * What a registration did: the agents it registered the server with, and, for each it could not,
   * why and what the user can do instead.
   */
  public record Result(List<String> registered, List<String> failures) {}

  /**
   * Registers {@code command}, the server's command line, with every agent installed that does not
   * run it already.
   */
  public static Result register(List<String> command) {
    List<String> registered = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (Agent agent : List.of(new Claude(), new Codex(), new Gemini(), cursor(), junie(), new VsCode())) {
      if (!agent.installed() || agent.registered(command)) continue;
      String failure = agent.register(command);
      if (failure == null) {
        registered.add(agent.name());
      } else {
        failures.add("could not register the " + SERVER_NAME + " MCP server with " + agent.name()
            + " (" + failure + "); " + agent.byHand(command));
      }
    }
    return new Result(registered, failures);
  }

  /** Removes {@link #SERVER_NAME} from every agent installed that has it, whatever it runs: the name is the CLI's and the plugin's. */
  public static Result unregister() {
    List<String> removed = new ArrayList<>();
    List<String> failures = new ArrayList<>();
    for (Agent agent : List.of(new Claude(), new Codex(), new Gemini(), cursor(), junie(), new VsCode())) {
      if (!agent.installed() || !agent.hasServer()) continue;
      String failure = agent.unregister();
      if (failure == null) {
        removed.add(agent.name());
      } else {
        failures.add("could not remove the " + SERVER_NAME + " MCP server from " + agent.name()
            + " (" + failure + "); " + agent.removeByHand());
      }
    }
    return new Result(removed, failures);
  }

  /**
   * {@code arend --mcp} as this process runs {@code arend}: its JVM and its classpath -- the
   * original, not the copy a daemon runs from, which the next start of a daemon replaces -- every
   * path absolute, since the agent runs it from wherever it was started. No {@code -L}: one server
   * serves the daemons of every start, whatever each was given.
   */
  public static List<String> command() {
    List<String> classpath = new ArrayList<>();
    for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
      if (!entry.isEmpty()) classpath.add(Path.of(entry).toAbsolutePath().normalize().toString());
    }
    return command(Path.of(System.getProperty("java.home")), classpath, CLI_MAIN, "--mcp");
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

  /** The stdio server running {@code command}, as the {@code mcpServers} of Claude Code and Cursor have it. */
  static ObjectNode server(List<String> command) {
    ObjectNode server = MAPPER.createObjectNode();
    server.put("type", "stdio");
    server.put("command", command.getFirst());
    command.subList(1, command.size()).forEach(server.putArray("args")::add);
    return server;
  }

  /** The server running {@code command} with nothing but its command and arguments. */
  static ObjectNode plainServer(List<String> command) {
    ObjectNode server = server(command);
    server.remove("type");
    return server;
  }

  /**
   * Whether {@code server}, an agent's entry, runs {@code command}. Agents add fields of their own,
   * an empty env among them: the command is what matters.
   */
  static boolean runs(JsonNode server, List<String> command) {
    List<String> registered = commandOf(server);
    return registered != null && registered.equals(command);
  }

  /**
   * Whether {@code server}, an agent's entry, runs {@code command} or another Arend server that
   * starts: another {@code arend}'s {@code --mcp}, or the IntelliJ plugin's, with its JVM and every
   * entry of its classpath there. Either finds every daemon through the registry, so the CLI and
   * the plugin leave each other's registration alone rather than take it back in turn; one whose
   * files are gone, as after a {@code gradle clean} or an update, is replaced.
   */
  static boolean servesDaemons(JsonNode server, List<String> command) {
    List<String> registered = commandOf(server);
    if (registered == null) return false;
    if (registered.equals(command)) return true;
    if (registered.size() < 4 || !registered.get(1).equals("-cp")) return false;
    List<String> main = registered.subList(3, registered.size());
    if (!main.equals(List.of(CLI_MAIN, "--mcp")) && !main.equals(List.of(DaemonMcpServer.class.getName()))) return false;
    if (!Files.isExecutable(Path.of(registered.getFirst()))) return false;
    for (String entry : registered.get(2).split(File.pathSeparator)) {
      // A directory's * is every jar in it: the directory is what has to be there.
      boolean wildcard = entry.endsWith("*");
      String file = wildcard ? entry.substring(0, entry.length() - 1) : entry;
      try {
        Path path = Path.of(file);
        if (wildcard ? !Files.isDirectory(path) : !Files.exists(path)) return false;
      } catch (InvalidPathException e) {
        return false;
      }
    }
    return true;
  }

  /** The command line of {@code server}, an agent's entry; null when it is not an entry. */
  private static List<String> commandOf(JsonNode server) {
    if (server == null || !server.isObject()) return null;
    List<String> registered = new ArrayList<>();
    registered.add(server.path("command").asText());
    server.path("args").forEach(arg -> registered.add(arg.asText()));
    return registered;
  }

  // ───────── agents ─────────

  /** An agent with a CLI of its own, installed when that is found. */
  private abstract static class CliAgent implements Agent {
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
      String configDir = System.getenv("CLAUDE_CONFIG_DIR");
      return configDir != null && !configDir.isEmpty() ? Path.of(configDir, ".claude.json") : home().resolve(".claude.json");
    }

    @Override
    public boolean registered(List<String> command) {
      return registeredIn(config(), MAPPER, "mcpServers", command);
    }

    @Override
    public String register(List<String> command) {
      // add refuses a name it has; a registration that is not this one is replaced.
      run(cli, List.of("mcp", "remove", "--scope", "user", SERVER_NAME));
      return run(cli, addArgs(command));
    }

    /** The command as arguments, not as JSON: on Windows Java leaves the quotes inside an argument unescaped. */
    private static List<String> addArgs(List<String> command) {
      List<String> args = new ArrayList<>(List.of("mcp", "add", "--scope", "user", SERVER_NAME, "--"));
      args.addAll(command);
      return args;
    }

    @Override
    public String byHand(List<String> command) {
      return "run: claude " + String.join(" ", addArgs(command));
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
    public boolean registered(List<String> command) {
      // Codex's config is TOML: Codex reads it back for us.
      Output get = runForOutput(cli, List.of("mcp", "get", SERVER_NAME, "--json"));
      if (get.failure() != null) return false;
      try {
        return servesDaemons(MAPPER.readTree(get.text()).get("transport"), command);
      } catch (IOException e) {
        return false;
      }
    }

    @Override
    public String register(List<String> command) {
      // add replaces a server of the name.
      return run(cli, addArgs(command));
    }

    private static List<String> addArgs(List<String> command) {
      List<String> args = new ArrayList<>(List.of("mcp", "add", SERVER_NAME, "--"));
      args.addAll(command);
      return args;
    }

    @Override
    public String byHand(List<String> command) {
      return "run: codex " + String.join(" ", addArgs(command));
    }

    @Override
    public boolean hasServer() {
      return runForOutput(cli, List.of("mcp", "get", SERVER_NAME, "--json")).failure() == null;
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

    private static Path config() {
      return home().resolve(".gemini").resolve("settings.json");
    }

    @Override
    public boolean registered(List<String> command) {
      return registeredIn(config(), LENIENT, "mcpServers", command);
    }

    @Override
    public String register(List<String> command) {
      // add replaces a server of the name; the arguments go after --, since they start with a dash.
      return run(cli, addArgs(command));
    }

    private static List<String> addArgs(List<String> command) {
      List<String> args = new ArrayList<>(List.of("mcp", "add", "--scope", "user", SERVER_NAME, command.getFirst(), "--"));
      args.addAll(command.subList(1, command.size()));
      return args;
    }

    @Override
    public String byHand(List<String> command) {
      return "run: gemini " + String.join(" ", addArgs(command));
    }

    @Override
    public boolean hasServer() {
      return hasServerIn(config(), LENIENT, "mcpServers");
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

  /**
   * An agent with no CLI to register a server through, whose {@code mcpServers} live in a JSON file
   * of the user's: Cursor's and Junie's. Installed when its directory under the home is there.
   */
  private record ConfigFile(String name, Path dir, Path config, Function<List<String>, ObjectNode> entry) implements Agent {
    @Override
    public boolean installed() {
      return Files.isDirectory(dir);
    }

    @Override
    public boolean registered(List<String> command) {
      return registeredIn(config, MAPPER, "mcpServers", command);
    }

    @Override
    public String register(List<String> command) {
      try {
        setServer(config, "mcpServers", entry.apply(command));
        return null;
      } catch (IOException e) {
        return e.getMessage();
      }
    }

    @Override
    public String byHand(List<String> command) {
      return "add to the mcpServers of " + config + ": \"" + SERVER_NAME + "\": " + entry.apply(command);
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
    return new ConfigFile("Cursor", dir, dir.resolve("mcp.json"), McpRegistration::server);
  }

  /**
   * Junie's, the IDE plugin's and the CLI's alike. The plugin does not show a server with a field it
   * does not know, a type among them.
   */
  private static ConfigFile junie() {
    Path dir = home().resolve(".junie");
    return new ConfigFile("Junie", dir, dir.resolve("mcp").resolve("mcp.json"), McpRegistration::plainServer);
  }

  private static final class VsCode extends CliAgent {
    VsCode() {
      super("VS Code", findExecutable("code"));
    }

    /** The default profile's mcp.json, where `code --add-mcp` puts a server. */
    private static Path config() {
      Path user;
      if (Platform.isWindows()) {
        String appData = System.getenv("APPDATA");
        user = Path.of(appData != null ? appData : home().resolve("AppData").resolve("Roaming").toString(), "Code", "User");
      } else if (Platform.isMac()) {
        user = home().resolve("Library").resolve("Application Support").resolve("Code").resolve("User");
      } else {
        String configHome = System.getenv("XDG_CONFIG_HOME");
        user = (configHome != null && !configHome.isEmpty() ? Path.of(configHome) : home().resolve(".config")).resolve("Code").resolve("User");
      }
      return user.resolve("mcp.json");
    }

    @Override
    public boolean registered(List<String> command) {
      return registeredIn(config(), LENIENT, "servers", command);
    }

    @Override
    public String register(List<String> command) {
      // Java leaves the quotes inside an argument unescaped on Windows, and the CLI would see them stripped.
      String json = Platform.isWindows() ? json(command).replace("\"", "\\\"") : json(command);
      String failure = run(cli, List.of("--add-mcp", json));
      if (failure != null) return failure;
      // Whether --add-mcp replaces a server of the name is VS Code's to say: see that it did.
      return registered(command) ? null : config() + " still has another " + SERVER_NAME;
    }

    private static String json(List<String> command) {
      ObjectNode server = MAPPER.createObjectNode().put("name", SERVER_NAME);
      server.setAll(server(command));
      return server.toString();
    }

    @Override
    public String byHand(List<String> command) {
      return "run: code --add-mcp '" + json(command) + "'";
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

  private static Path home() {
    return Path.of(System.getProperty("user.home"));
  }

  /** Whether the {@code key} object of {@code file} serves the daemons as {@link #SERVER_NAME} (see {@link #servesDaemons}); false when it cannot be read. */
  private static boolean registeredIn(Path file, ObjectMapper mapper, String key, List<String> command) {
    try {
      return Files.isRegularFile(file) && servesDaemons(mapper.readTree(file.toFile()).path(key).get(SERVER_NAME), command);
    } catch (IOException e) {
      return false;
    }
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

  /** Sets {@code server} as {@link #SERVER_NAME} in the {@code key} object of {@code file}, keeping the rest. */
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
    List<String> names = Platform.isWindows() ? List.of(name + ".exe", name + ".cmd") : List.of(name);
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

  private record Output(String text, String failure) {}

  /** Runs {@code cli} with {@code args}; null when it succeeded, else what went wrong. */
  private static String run(Path cli, List<String> args) {
    return runForOutput(cli, args).failure();
  }

  /**
   * Runs {@code cli} with {@code args} in the home directory, where no project's settings apply:
   * what it printed, and null for a failure when it succeeded, else what went wrong.
   */
  private static Output runForOutput(Path cli, List<String> args) {
    List<String> command = new ArrayList<>();
    command.add(cli.toString());
    command.addAll(args);
    Path log = null;
    try {
      // To a file, not a pipe: a process blocked on a full pipe would outlive the timeout.
      log = Files.createTempFile("arend-mcp-register.", ".log");
      Process process = new ProcessBuilder(command).directory(home().toFile()).redirectErrorStream(true)
          .redirectOutput(log.toFile()).start();
      process.getOutputStream().close();
      if (!process.waitFor(CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return new Output("", cli.getFileName() + " did not answer within " + CLI_TIMEOUT_SECONDS + "s");
      }
      String output = Files.readString(log).strip();
      if (process.exitValue() == 0) return new Output(output, null);
      return new Output(output, output.isEmpty() ? "exit code " + process.exitValue() : output);
    } catch (IOException e) {
      return new Output("", e.getMessage());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new Output("", "interrupted");
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
