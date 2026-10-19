package org.arend.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.arend.prelude.Prelude;

import java.io.IOException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The skills that teach an agent to work in Arend, downloaded on request from the arend-skills
 * repository on GitHub and installed among the user's skills of the agent asking -- the one the MCP
 * client says it is, or the one a call names. A skill is a directory at the top of the repository
 * holding a {@code SKILL.md}, whose frontmatter describes it, and every file under it is part of it.
 * A skill installed here is marked so: {@code skills_remove} removes those and never a skill of the
 * user's own.
 *
 * <p>What is installed becomes instructions the agent follows from then on, so it is taken from one
 * snapshot of the repository and nothing else. With no {@code ref} given, that is the tag of this
 * Arend's version ({@link #versionTag}) when the repository has one -- the skills that go with it,
 * which a later push to {@code main} does not change -- and {@code main} otherwise, which the answer
 * says. Every file is checked against the hash the repository's listing gives it, so that all of
 * them come from the snapshot listed, and the marker records that snapshot's tree.
 */
final class DaemonMcpSkills {
  static final String LIST = "skills_list";
  static final String INSTALL = "skills_install";
  static final String REMOVE = "skills_remove";

  /**
   * Where the files of the skills are, and where GitHub lists them, {@code {ref}} the branch or tag;
   * system properties may point elsewhere, as a test does.
   */
  static final String URL_PROPERTY = "arend.skills.url";
  static final String TREE_PROPERTY = "arend.skills.tree";
  private static final String DEFAULT_URL = "https://raw.githubusercontent.com/arend-lang/arend-skills/{ref}/";
  private static final String DEFAULT_TREE = "https://api.github.com/repos/arend-lang/arend-skills/git/trees/{ref}?recursive=1";
  private static final String DEFAULT_REF = "main";

  /** The tag of the arend-skills repository that goes with this Arend: {@code v} and the language version. */
  static String versionTag() {
    return "v" + Prelude.VERSION;
  }
  /** The file in an installed skill's directory that says it is ours, and where it came from. */
  static final String MARKER = ".arend-skill";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9-]{0,63}");
  private static final Pattern FILE = Pattern.compile("[A-Za-z0-9_.-]+(/[A-Za-z0-9_.-]+)*");

  /** The agents, by the ids a call names them with, in the order {@link #agents} has them. */
  private static final List<String> AGENT_IDS = List.of("claude", "codex", "gemini", "cursor", "vscode", "junie");

  /** An agent and the directory of its user's skills. */
  record Agent(String id, String name, Path skills) {}

  private final Path home;
  /** Through the proxy the JVM is told of, as the user's network may require. */
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
      .followRedirects(HttpClient.Redirect.NORMAL).proxy(ProxySelector.getDefault()).build();

  DaemonMcpSkills(Path home) {
    this.home = home;
  }

  static boolean handles(String tool) {
    return tool.equals(LIST) || tool.equals(INSTALL) || tool.equals(REMOVE);
  }

  /** Each agent's own directory, as its documentation gives it for the user's skills. */
  List<Agent> agents() {
    String claudeDir = System.getenv("CLAUDE_CONFIG_DIR");
    Path claude = claudeDir != null && !claudeDir.isEmpty() ? Path.of(claudeDir) : home.resolve(".claude");
    return List.of(
        new Agent("claude", "Claude Code", claude.resolve("skills")),
        new Agent("codex", "Codex", home.resolve(".agents").resolve("skills")),
        new Agent("gemini", "Gemini CLI", home.resolve(".gemini").resolve("skills")),
        new Agent("cursor", "Cursor", home.resolve(".cursor").resolve("skills")),
        new Agent("vscode", "VS Code (GitHub Copilot)", home.resolve(".copilot").resolve("skills")),
        new Agent("junie", "Junie", home.resolve(".junie").resolve("skills")));
  }

  /** The agent an MCP client is, by the name it gives in {@code initialize}; null when it is none of them. */
  Agent agentOfClient(String clientName) {
    if (clientName == null) return null;
    String name = clientName.toLowerCase(Locale.ROOT);
    // Cursor's client calls itself after VS Code, which it is built on.
    String id = name.contains("claude") ? "claude" : name.contains("codex") ? "codex" : name.contains("gemini") ? "gemini"
        : name.contains("cursor") ? "cursor" : name.contains("junie") ? "junie"
        : name.contains("visual studio code") || name.contains("vscode") || name.contains("copilot") ? "vscode" : null;
    return id == null ? null : agent(id);
  }

  private Agent agent(String id) {
    return agents().stream().filter(agent -> agent.id().equals(id)).findFirst().orElse(null);
  }

  static List<Map<String, Object>> definitions() {
    Map<String, Object> agent = Map.of("type", "string", "enum", AGENT_IDS,
        "description", "The agent whose user skills to work on. Omitted: the agent this MCP server runs for, as its"
            + " client names itself; say it when that is not one of these.");
    Map<String, Object> ref = Map.of("type", "string", "description",
        "The branch, tag or commit of the arend-skills repository to take the skills from. Omitted: the tag "
            + versionTag() + " of this Arend's version, or " + DEFAULT_REF + " when the repository has no such tag.");
    Map<String, Object> names = DaemonMcpTools.stringArray(
        "The skills, by name, as skills_list prints them. Omitted or empty: all of them.");

    List<Map<String, Object>> tools = new ArrayList<>();
    tools.add(DaemonMcpTools.tool(LIST, "The skills for working in Arend that the arend-skills repository offers, and which of them"
        + " are installed for the agent. A skill teaches the agent a workflow -- formalizing a statement, closing its goals,"
        + " reading an error -- and loads when a task needs it. Nothing is installed without skills_install.",
        DaemonMcpTools.schema(props("agent", agent, "ref", ref), List.of())));
    tools.add(DaemonMcpTools.tool(INSTALL, "Download skills from the arend-skills repository on GitHub and install them among the user's"
        + " skills of the agent, replacing earlier copies installed this way; a skill of the same name the user"
        + " wrote is left alone. Only when the user asks for it. The agent sees new skills from its next session on.",
        DaemonMcpTools.schema(props("agent", agent, "names", names, "ref", ref), List.of())));
    tools.add(DaemonMcpTools.tool(REMOVE, "Remove skills that skills_install installed for the agent; skills it did not install are"
        + " never touched. Only when the user asks for it.",
        DaemonMcpTools.schema(props("agent", agent, "names", names), List.of())));
    return tools;
  }

  /**
   * The text answering {@code tool}; throws {@link IllegalArgumentException} for a call it cannot
   * make. One at a time: the server runs calls side by side, and two installs share directories.
   */
  synchronized String call(String tool, JsonNode args, String clientName) throws IOException, InterruptedException {
    Agent agent = agent(args, clientName);
    return switch (tool) {
      case LIST -> list(agent, snapshot(ref(args)));
      case INSTALL -> install(agent, names(args), snapshot(ref(args)));
      case REMOVE -> remove(agent, names(args));
      default -> throw new IllegalArgumentException("Unknown tool: " + tool);
    };
  }

  /**
   * The repository at {@code ref}, or with none given at {@link #versionTag}, falling back to
   * {@link #DEFAULT_REF} when the repository has no such tag.
   */
  private Snapshot snapshot(String ref) throws IOException, InterruptedException {
    if (ref != null) {
      try {
        return index(ref, null);
      } catch (NotFound e) {
        throw new IOException("The arend-skills repository has no skills on '" + ref + "': " + e.getMessage());
      }
    }
    try {
      return index(versionTag(), null);
    } catch (NotFound e) {
      return index(DEFAULT_REF, "The arend-skills repository has no tag " + versionTag() + " for this version of Arend;"
          + " these are its " + DEFAULT_REF + ", which may have changed since this Arend was released.");
    }
  }

  private Agent agent(JsonNode args, String clientName) {
    JsonNode id = args.get("agent");
    if (id != null && !id.isNull()) {
      Agent agent = id.isTextual() ? agent(id.asText()) : null;
      if (agent == null) throw new IllegalArgumentException("'agent' must be one of " + String.join(", ", AGENT_IDS));
      return agent;
    }
    Agent agent = agentOfClient(clientName);
    if (agent == null) {
      throw new IllegalArgumentException("This MCP client (" + (clientName == null ? "unnamed" : clientName) + ") is not"
          + " an agent known here; say which it is with 'agent', one of " + String.join(", ", AGENT_IDS) + ".");
    }
    return agent;
  }

  /** The ref a call names, or null for none. */
  private static String ref(JsonNode args) {
    JsonNode ref = args.get("ref");
    if (ref == null || ref.isNull() || ref.asText().isEmpty()) return null;
    if (!ref.isTextual() || !ref.asText().matches("[A-Za-z0-9._/-]+") || ref.asText().contains("..")) {
      throw new IllegalArgumentException("'ref' must be a branch or tag name");
    }
    return ref.asText();
  }

  private static List<String> names(JsonNode args) {
    JsonNode names = args.get("names");
    if (names == null || names.isNull()) return List.of();
    if (!names.isArray()) throw new IllegalArgumentException("'names' must be an array of strings");
    List<String> out = new ArrayList<>();
    for (JsonNode name : names) {
      if (!name.isTextual()) throw new IllegalArgumentException("'names' must be an array of strings");
      if (!name.asText().isEmpty()) out.add(name.asText());
    }
    return out;
  }

  // ───────── the tools ─────────

  /** A file of a skill, by its path inside the skill, and the git hash of its contents the listing gives. */
  private record SkillFile(String path, String hash) {}

  private record Skill(String name, String description, List<SkillFile> files) {}

  /**
   * The repository as one listing of it has it: the ref asked for, the hash of the tree listed, its
   * skills, and what the answer should say of the choice of ref, if anything.
   */
  private record Snapshot(String ref, String tree, List<Skill> skills, String note) {
    String describe() {
      return ref + ", tree " + tree.substring(0, Math.min(12, tree.length()));
    }
  }

  private String list(Agent agent, Snapshot snapshot) throws IOException {
    StringBuilder out = new StringBuilder();
    if (snapshot.note() != null) out.append(snapshot.note()).append("\n\n");
    out.append("Skills of the arend-skills repository (").append(snapshot.describe()).append("), for ").append(agent.name())
        .append(" in ").append(agent.skills()).append(":\n");
    for (Skill skill : snapshot.skills()) {
      out.append("\n  ").append(skill.name()).append(" -- ").append(state(agent, skill.name(), snapshot)).append("\n    ")
          .append(skill.description()).append('\n');
    }
    out.append("\nInstall them with skills_install, remove them with skills_remove.");
    return out.toString();
  }

  private String state(Agent agent, String name, Snapshot snapshot) {
    Path dir = agent.skills().resolve(name);
    if (Files.isRegularFile(dir.resolve(MARKER))) {
      String tree;
      try {
        tree = MAPPER.readTree(dir.resolve(MARKER).toFile()).path("tree").asText("");
      } catch (IOException e) {
        tree = "";
      }
      return tree.equals(snapshot.tree()) ? "installed" : "installed, from another snapshot of the repository";
    }
    return Files.exists(dir) ? "a skill of this name the user has, not installed from here" : "not installed";
  }

  private String install(Agent agent, List<String> names, Snapshot snapshot) throws IOException, InterruptedException {
    String ref = snapshot.ref();
    List<Skill> offered = snapshot.skills();
    List<Skill> chosen = new ArrayList<>();
    for (String name : names) {
      chosen.add(offered.stream().filter(skill -> skill.name().equals(name)).findFirst().orElseThrow(() ->
          new IllegalArgumentException("The arend-skills repository (" + ref + ") has no skill '" + name + "'; skills_list lists them.")));
    }
    if (chosen.isEmpty()) chosen = offered;

    Files.createDirectories(agent.skills());
    List<String> installed = new ArrayList<>();
    List<String> skipped = new ArrayList<>();
    for (Skill skill : chosen) {
      Path dir = agent.skills().resolve(skill.name());
      if (Files.exists(dir) && !Files.isRegularFile(dir.resolve(MARKER))) {
        skipped.add(skill.name());
        continue;
      }
      // Downloaded whole before the old copy goes: a failure leaves that one in place.
      Path staged = Files.createTempDirectory(agent.skills(), "." + skill.name() + ".");
      try {
        for (SkillFile file : skill.files()) {
          Path target = staged.resolve(file.path());
          Files.createDirectories(target.getParent());
          byte[] contents = fetch(ref, skill.name() + "/" + file.path());
          // The same snapshot as the listing, and nothing but: a push in between, or anything else
          // answering for the file, would give other contents.
          if (!gitBlobHash(contents).equals(file.hash())) {
            throw new IOException(skill.name() + "/" + file.path() + " is not what the listing of " + snapshot.describe()
                + " says it is; the repository may have changed meanwhile. Nothing of " + skill.name() + " was installed.");
          }
          Files.write(target, contents);
        }
        ObjectNode marker = MAPPER.createObjectNode().put("source", url(ref, skill.name() + "/")).put("ref", ref)
            .put("tree", snapshot.tree());
        Files.writeString(staged.resolve(MARKER), marker.toString());
        // Swapped, not deleted first: a delete failing halfway, as one of a file held open does, would leave half a copy.
        Path old = Files.exists(dir) ? Files.move(dir, staged.resolveSibling(staged.getFileName() + ".old")) : null;
        try {
          Files.move(staged, dir, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
          // The old copy goes back where it was, rather than stay hidden beside it.
          if (old != null) Files.move(old, dir);
          throw e;
        }
        if (old != null) deleteTree(old);
        installed.add(skill.name() + " (" + skill.files().stream().map(SkillFile::path).collect(Collectors.joining(", ")) + ")");
      } finally {
        if (Files.exists(staged)) deleteTree(staged);
      }
    }

    StringBuilder out = new StringBuilder();
    if (snapshot.note() != null) out.append(snapshot.note()).append('\n');
    if (!installed.isEmpty()) {
      out.append("Installed for ").append(agent.name()).append(" in ").append(agent.skills()).append(", from arend-skills ")
          .append(snapshot.describe()).append(": ").append(String.join("; ", installed))
          .append(". They load from the agent's next session on.");
    }
    if (!skipped.isEmpty()) {
      if (!out.isEmpty()) out.append('\n');
      out.append("Left alone, as skills of the user's own: ").append(String.join(", ", skipped)).append('.');
    }
    return out.toString();
  }

  private String remove(Agent agent, List<String> names) throws IOException {
    List<String> removed = new ArrayList<>();
    List<String> notOurs = new ArrayList<>();
    List<String> chosen = names;
    if (chosen.isEmpty()) {
      chosen = new ArrayList<>();
      if (Files.isDirectory(agent.skills())) {
        try (Stream<Path> dirs = Files.list(agent.skills())) {
          for (Path dir : (Iterable<Path>) dirs.sorted()::iterator) {
            // Not a copy left staged by an install that was killed: its name is no skill's.
            String name = dir.getFileName().toString();
            if (NAME.matcher(name).matches() && Files.isRegularFile(dir.resolve(MARKER))) chosen.add(name);
          }
        }
      }
    }
    for (String name : chosen) {
      if (!NAME.matcher(name).matches()) throw new IllegalArgumentException("'" + name + "' is not a skill name");
      Path dir = agent.skills().resolve(name);
      if (Files.isRegularFile(dir.resolve(MARKER))) {
        deleteTree(dir);
        removed.add(name);
      } else if (Files.exists(dir)) {
        notOurs.add(name);
      }
    }
    if (removed.isEmpty() && notOurs.isEmpty()) return "No skill installed from here for " + agent.name() + " to remove.";
    StringBuilder out = new StringBuilder();
    if (!removed.isEmpty()) {
      out.append("Removed for ").append(agent.name()).append(": ").append(String.join(", ", removed)).append('.');
    }
    if (!notOurs.isEmpty()) {
      if (!out.isEmpty()) out.append('\n');
      out.append("Left alone, as not installed from here: ").append(String.join(", ", notOurs)).append('.');
    }
    return out.toString();
  }

  // ───────── the repository ─────────

  /**
   * The skills the repository has on {@code ref}, by what GitHub lists of it: every directory at its
   * top with a {@code SKILL.md}, its files, and the description in its frontmatter. Every path is
   * checked, since it becomes one on the user's disk; a directory without a {@code SKILL.md}, or not
   * named as a skill is, is no skill.
   */
  /**
   * The repository on {@code ref}, as GitHub lists it: every directory at its top with a
   * {@code SKILL.md}, its files with their hashes, and the description in its frontmatter. Throws
   * {@link NotFound} when there is no such ref; {@code note} goes with the snapshot.
   */
  private Snapshot index(String ref, String note) throws IOException, InterruptedException {
    String treeUrl = System.getProperty(TREE_PROPERTY, DEFAULT_TREE).replace("{ref}", ref);
    JsonNode listing = MAPPER.readTree(get(treeUrl));
    Map<String, List<SkillFile>> files = new TreeMap<>();
    for (JsonNode entry : listing.path("tree")) {
      if (!entry.path("type").asText().equals("blob")) continue;
      String path = entry.path("path").asText();
      if (!FILE.matcher(path).matches() || Arrays.asList(path.split("/")).contains("..")) {
        throw new IOException("the repository lists '" + path + "', which is not a relative file path");
      }
      int slash = path.indexOf('/');
      if (slash < 0 || !NAME.matcher(path.substring(0, slash)).matches()) continue;
      files.computeIfAbsent(path.substring(0, slash), name -> new ArrayList<>())
          .add(new SkillFile(path.substring(slash + 1), entry.path("sha").asText("")));
    }
    // Every path is checked before anything else is: those are what reach the disk.
    for (List<SkillFile> skill : files.values()) {
      for (SkillFile file : skill) {
        if (file.hash().isEmpty()) throw new IOException("the repository lists '" + file.path() + "' with no hash");
      }
    }
    // A listing GitHub cut short is not the snapshot: the skills it leaves out could be any.
    if (listing.path("truncated").asBoolean(false)) throw new IOException("GitHub's listing of arend-skills " + ref + " is truncated");
    String tree = listing.path("sha").asText("");
    if (tree.isEmpty()) throw new IOException("GitHub's listing of arend-skills " + ref + " gives no tree hash");
    List<Skill> skills = new ArrayList<>();
    for (Map.Entry<String, List<SkillFile>> skill : files.entrySet()) {
      if (skill.getValue().stream().noneMatch(file -> file.path().equals("SKILL.md"))) continue;
      String text = new String(fetch(ref, skill.getKey() + "/SKILL.md"), StandardCharsets.UTF_8);
      skills.add(new Skill(skill.getKey(), description(text), skill.getValue()));
    }
    return new Snapshot(ref, tree, skills, note);
  }

  /** The hash git gives {@code contents} as a blob, which a tree listing gives each file. */
  static String gitBlobHash(byte[] contents) {
    try {
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      sha1.update(("blob " + contents.length + "\0").getBytes(StandardCharsets.US_ASCII));
      return HexFormat.of().formatHex(sha1.digest(contents));
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError("SHA-1 unavailable", e);
    }
  }

  /**
   * The {@code description} in the frontmatter of a {@code SKILL.md}, on its line or, after a
   * {@code >} or {@code |}, on the indented lines below; empty when there is none.
   */
  static String description(String skillMd) {
    List<String> lines = skillMd.lines().toList();
    if (lines.isEmpty() || !lines.getFirst().strip().equals("---")) return "";
    for (int i = 1; i < lines.size() && !lines.get(i).strip().equals("---"); i++) {
      String line = lines.get(i);
      if (!line.startsWith("description:")) continue;
      String value = line.substring("description:".length()).strip();
      if (value.startsWith(">") || value.startsWith("|")) {
        StringBuilder block = new StringBuilder();
        for (int j = i + 1; j < lines.size() && (lines.get(j).startsWith(" ") || lines.get(j).startsWith("\t")); j++) {
          if (!block.isEmpty()) block.append(' ');
          block.append(lines.get(j).strip());
        }
        return block.toString();
      }
      if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"") || value.startsWith("'") && value.endsWith("'"))) {
        value = value.substring(1, value.length() - 1);
      }
      return value;
    }
    return "";
  }

  private static String url(String ref, String path) {
    return System.getProperty(URL_PROPERTY, DEFAULT_URL).replace("{ref}", ref) + path;
  }

  private byte[] fetch(String ref, String path) throws IOException, InterruptedException {
    return get(url(ref, path));
  }

  private byte[] get(String url) throws IOException, InterruptedException {
    HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30))
        .header("Accept", "application/vnd.github+json").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() == 404) throw new NotFound(url + " is not there");
    if (response.statusCode() != 200) throw new IOException("GET " + url + " answered " + response.statusCode());
    return response.body();
  }

  private static final class NotFound extends IOException {
    NotFound(String message) {
      super(message);
    }
  }

  // ───────── helpers ─────────

  private static void deleteTree(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) Files.delete(path);
    }
  }

  private static Map<String, Object> props(Object... keysAndValues) {
    Map<String, Object> props = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) props.put((String) keysAndValues[i], keysAndValues[i + 1]);
    return props;
  }
}
