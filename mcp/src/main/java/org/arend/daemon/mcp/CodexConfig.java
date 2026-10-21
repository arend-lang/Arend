package org.arend.daemon.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@link McpRegistration#SERVER_NAME} table of a Codex {@code config.toml}: a project's own
 * {@code .codex/config.toml}, which Codex reads for a trusted project only, or the user's.
 *
 * <p>There is no TOML library here, so the file is edited by lines, and only the server's table is
 * touched: {@code [mcp_servers.arend-daemons]} and its subtables, every other line kept as it is,
 * comments included. What a line-wise edit cannot do safely is refused: a file with multi-line
 * strings, where a line starting with {@code [} need not be a table, and a server defined inline,
 * under {@code [mcp_servers]} or as dotted keys.
 */
final class CodexConfig {
  private CodexConfig() {}

  private static final String NAME = "(?:\"" + McpRegistration.SERVER_NAME + "\"|'" + McpRegistration.SERVER_NAME + "'|"
      + McpRegistration.SERVER_NAME + ")";
  /** The server's table, or one of its subtables. */
  private static final Pattern OWN_TABLE = Pattern.compile("\\s*\\[\\s*mcp_servers\\s*\\.\\s*" + NAME + "\\s*(?:\\..*)?]\\s*(?:#.*)?");
  private static final Pattern SERVERS_TABLE = Pattern.compile("\\s*\\[\\s*mcp_servers\\s*]\\s*(?:#.*)?");
  private static final Pattern ANY_TABLE = Pattern.compile("\\s*\\[.*");
  /** The server defined inline: a key of {@code [mcp_servers]}, or a dotted key at the top. */
  private static final Pattern INLINE_IN_SERVERS = Pattern.compile("\\s*" + NAME + "\\s*[.=].*");
  private static final Pattern INLINE_AT_TOP = Pattern.compile("\\s*mcp_servers\\s*\\.\\s*" + NAME + "\\s*[.=].*");
  /** A project's table, its key a basic string or, as Codex writes it, a literal one. */
  private static final Pattern PROJECT_TABLE = Pattern.compile("\\s*\\[\\s*projects\\s*\\.\\s*(?:\"((?:[^\"\\\\]|\\\\.)*)\"|'([^']*)')\\s*]\\s*(?:#.*)?");
  private static final Pattern TRUSTED = Pattern.compile("\\s*trust_level\\s*=\\s*(?:\"trusted\"|'trusted')\\s*(?:#.*)?");

  /** The server's table, as it is written and as a user can add it by hand. */
  static List<String> table(List<String> command) {
    List<String> args = command.subList(1, command.size()).stream().map(CodexConfig::string).toList();
    return List.of("[mcp_servers." + McpRegistration.SERVER_NAME + "]",
        "command = " + string(command.getFirst()),
        "args = [" + String.join(", ", args) + "]");
  }

  /** A TOML basic string. */
  private static String string(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  /** Whether {@code file} has the server, as its own table or inline; false when it cannot be read. */
  static boolean hasServer(Path file) {
    try {
      if (!Files.isRegularFile(file)) return false;
      List<String> lines = lines(Files.readString(file));
      return lines.stream().anyMatch(line -> OWN_TABLE.matcher(line).matches()) || inline(lines);
    } catch (IOException e) {
      return false;
    }
  }

  /** Sets the server running {@code command} in {@code file}, replacing a table of it there and keeping the rest. */
  static void setServer(Path file, List<String> command) throws IOException {
    if (Files.isSymbolicLink(file)) file = file.toRealPath();
    String text = Files.exists(file) ? Files.readString(file) : "";
    String separator = text.contains("\r\n") ? "\r\n" : "\n";
    List<String> kept = withoutServer(file, text, new ArrayList<>());
    if (!kept.isEmpty()) kept.add("");
    kept.addAll(table(command));
    write(file, String.join(separator, kept) + separator);
  }

  /** Removes the server's table from {@code file}, keeping the rest; the lines removed, none when it had no table. */
  static List<String> removeServer(Path file) throws IOException {
    if (Files.isSymbolicLink(file)) file = file.toRealPath();
    if (!Files.exists(file)) return List.of();
    String text = Files.readString(file);
    List<String> removed = new ArrayList<>();
    List<String> kept = withoutServer(file, text, removed);
    if (removed.isEmpty()) return removed;
    String separator = text.contains("\r\n") ? "\r\n" : "\n";
    write(file, kept.isEmpty() ? "" : String.join(separator, kept) + separator);
    while (!removed.isEmpty() && removed.getLast().isBlank()) removed.removeLast();
    return removed;
  }

  /**
   * Whether the user's {@code config} trusts {@code projectDir}, which Codex requires before it reads
   * the project's own config: {@code trust_level = 'trusted'} under {@code [projects.'<dir>']}, as Codex
   * writes it, or the same in basic strings.
   */
  static boolean trusts(Path config, Path projectDir) {
    try {
      if (!Files.isRegularFile(config)) return false;
      List<String> names = List.of(projectDir.toAbsolutePath().normalize().toString(), McpRegistration.canonical(projectDir).toString());
      boolean inProject = false;
      for (String line : lines(Files.readString(config))) {
        Matcher project = PROJECT_TABLE.matcher(line);
        if (project.matches()) {
          String key = project.group(1) != null ? project.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : project.group(2);
          inProject = names.contains(key);
        } else if (ANY_TABLE.matcher(line).matches()) {
          inProject = false;
        } else if (inProject && TRUSTED.matcher(line).matches()) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * The lines of {@code text} without the server's table and its subtables, nor the blank lines that led
   * up to it; the lines of the tables go to {@code removed}.
   */
  private static List<String> withoutServer(Path file, String text, List<String> removed) throws IOException {
    if (text.contains("\"\"\"") || text.contains("'''")) {
      throw new IOException(file + " has multi-line strings, which this edit does not parse");
    }
    List<String> lines = lines(text);
    if (inline(lines)) throw new IOException(file + " defines " + McpRegistration.SERVER_NAME + " inline, not as a table of its own");
    List<String> kept = new ArrayList<>();
    boolean inOwn = false;
    for (String line : lines) {
      if (OWN_TABLE.matcher(line).matches()) {
        inOwn = true;
        while (!kept.isEmpty() && kept.getLast().isBlank()) kept.removeLast();
      } else if (ANY_TABLE.matcher(line).matches()) {
        // The blank line that set the next table off went with the removed one.
        if (inOwn && !kept.isEmpty() && !kept.getLast().isBlank()) kept.add("");
        inOwn = false;
      }
      (inOwn ? removed : kept).add(line);
    }
    while (!kept.isEmpty() && kept.getLast().isBlank()) kept.removeLast();
    return kept;
  }

  /** Whether the server is defined inline in {@code lines}: in {@code [mcp_servers]}, or by dotted keys before any table. */
  private static boolean inline(List<String> lines) {
    String table = "";
    for (String line : lines) {
      if (SERVERS_TABLE.matcher(line).matches()) {
        table = "mcp_servers";
      } else if (ANY_TABLE.matcher(line).matches()) {
        table = "other";
      } else if (table.isEmpty() ? INLINE_AT_TOP.matcher(line).matches() : table.equals("mcp_servers") && INLINE_IN_SERVERS.matcher(line).matches()) {
        return true;
      }
    }
    return false;
  }

  private static List<String> lines(String text) {
    List<String> lines = new ArrayList<>(List.of(text.split("\r?\n", -1)));
    if (!lines.isEmpty() && lines.getLast().isEmpty()) lines.removeLast();
    return lines;
  }

  /** Writes {@code text} to {@code file} whole or not at all. */
  private static void write(Path file, String text) throws IOException {
    Files.createDirectories(file.getParent());
    Path tmp = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
    try {
      Files.writeString(tmp, text);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } finally {
      Files.deleteIfExists(tmp);
    }
  }
}
