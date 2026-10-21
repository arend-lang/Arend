package org.arend.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import org.arend.cli.ArendOptions;
import org.arend.query.ConsoleQueryTool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DaemonMcpTools {
  private DaemonMcpTools() {}

  /** The daemon op a tool call sends, with the argv of a {@code cli} op; a null op is daemon_list. */
  record Call(String library, String op, List<String> argv) {}

  private static final String TYPECHECK = "typecheck";
  private static final String LIST = "daemon_list";

  /** A tool that sends the daemon serving `library` one op of its own and nothing else. */
  private record Control(String name, String op, String description) {}

  private static final List<Control> CONTROLS = List.of(
      new Control("daemon_ping", "ping", "Report whether the worker of the daemon serving `library` is IDLE or BUSY."),
      new Control("daemon_status", "status", "The state of the daemon serving `library`, as JSON: process id, worker"
          + " state, queue depth, uptime, current task, protocol version, the libraries it serves and locked flags."),
      new Control("daemon_refresh", "refresh", "Resolve the libraries of the daemon serving `library` again, so source"
          + " edits, new and deleted files are picked up; typechecks nothing. Call it after creating, deleting or"
          + " renaming a module file, before a query or a typecheck that should see the change."),
      new Control("daemon_stop", "shutdown", "Stop the daemon owning `library` once the commands already queued have"
          + " run; not for a library no daemon owns, under ~/.arend/libs or a zip. Only when the user asks for it: the daemon serves other agents"
          + " and the user's own `arend` commands too."));

  private static final Map<String, String> TYPECHECK_FLAGS = new LinkedHashMap<>();
  static {
    TYPECHECK_FLAGS.put("test", "-t");
    TYPECHECK_FLAGS.put("show_times", "--" + ArendOptions.SHOW_TIMES);
    TYPECHECK_FLAGS.put("show_sizes", "--" + ArendOptions.SHOW_SIZES);
    TYPECHECK_FLAGS.put("show_modules", "--" + ArendOptions.SHOW_MODULES);
    TYPECHECK_FLAGS.put("show_modules_with_instances", "--" + ArendOptions.SHOW_MODULES_WITH_INSTANCES);
  }

  static String toolName(ConsoleQueryTool tool) {
    return tool.longName().replace('-', '_');
  }

  static List<Map<String, Object>> definitions() {
    List<Map<String, Object>> tools = new ArrayList<>();
    Map<String, Object> library = Map.of("type", "string", "description",
        "The library to work on, by name (e.g. `arend-lib`) or path; the call goes to the daemon serving it"
            + " and is limited to it. A name means a running daemon's own library of that name first, a project and"
            + " not a zip, with a warning when a daemon also loaded a zip of that name, and nothing runs when"
            + " daemons own several; else the longest-running daemon that loaded a zip or a ~/.arend/libs library of"
            + " that name. A path to one under ~/.arend/libs, or to a zip, goes to the longest-running daemon that"
            + " loaded it. Omitted: the working directory's library, else the daemon holding the project in the"
            + " working directory, else the only running daemon.");

    Map<String, Object> tcProps = new LinkedHashMap<>();
    tcProps.put("library", library);
    tcProps.put("targets", stringArray("Modules (e.g. `Algebra.Ring`) or libraries to typecheck: the positional"
        + " arguments of `arend`. Omitted or empty: the whole of `library`, or with no `library` every library the daemon serves."));
    tcProps.put("print", Map.of("type", "string", "description",
        "MODULE[:DEF]: after the typecheck, print the elaborated form of MODULE, or of a single DEF inside it (-p)."));
    tcProps.put("test", bool("Run tests (-t)."));
    tcProps.put("show_times", bool("Print how long each definition this call re-typechecked took: only those edited"
        + " since the last check, and their dependents."));
    tcProps.put("show_sizes", bool("Print every definition's typechecked core-term size, sorted descending."));
    tcProps.put("show_modules", bool("Print the module import DAG in topological order; import cycles are grouped."));
    tcProps.put("show_modules_with_instances", bool("Like show_modules, restricted to modules that define an \\instance."));
    tools.add(tool(TYPECHECK,
        "Typecheck modules on the warm daemon serving `library`, as `arend [TARGETS]` would; reports errors and"
            + " goals. The flags the daemon was started with apply and cannot be changed here.",
        schema(tcProps, List.of())));

    for (ConsoleQueryTool query : ArendOptions.QUERY_TOOLS) {
      Map<String, Object> props = new LinkedHashMap<>();
      props.put("library", library);
      props.put("args", stringArray("The tokens that follow -" + query.shortName() + " on the command line ("
          + query.argName() + " and the key=value / flag tokens described below), one per element."));
      props.put("json", bool("Print the result as a single JSON document instead of the human-readable listing (--json)."));
      tools.add(tool(toolName(query),
          "Same as `arend -" + query.shortName() + "` on the warm daemon serving `library`. Full grammar of"
              + " the arguments, as printed by `arend -" + query.shortName() + " --help`:\n\n" + query.cliHelp(),
          schema(props, List.of("args"))));
    }

    Map<String, Object> libraryOnly = Map.of("library", library);
    tools.add(tool(LIST, "The running daemons, oldest first: process id, when it started, the libraries it serves,"
        + " and those it loaded without owning them -- under ~/.arend/libs, zips and dependencies. Start one with `arend -d LIBRARY...`.",
        schema(Map.of(), List.of())));
    for (Control control : CONTROLS) tools.add(tool(control.name(), control.description(), schema(libraryOnly, List.of())));
    return tools;
  }

  static Call resolve(String name, JsonNode arguments) {
    JsonNode args = arguments == null || arguments.isNull() ? MissingNode.getInstance() : arguments;
    switch (name) {
      case TYPECHECK -> {
        List<String> argv = new ArrayList<>(strings(args, "targets"));
        String print = string(args, "print");
        if (print != null) {
          argv.add("-p");
          argv.add(print);
        }
        for (Map.Entry<String, String> flag : TYPECHECK_FLAGS.entrySet()) {
          if (bool(args, flag.getKey())) argv.add(flag.getValue());
        }
        return new Call(string(args, "library"), "cli", argv);
      }
      case LIST -> { return new Call(null, null, null); }
      default -> { }
    }
    for (Control control : CONTROLS) {
      if (control.name().equals(name)) return new Call(string(args, "library"), control.op(), null);
    }
    for (ConsoleQueryTool query : ArendOptions.QUERY_TOOLS) {
      if (!toolName(query).equals(name)) continue;
      List<String> tokens = strings(args, "args");
      if (tokens.isEmpty()) throw new IllegalArgumentException("'args' must name at least one " + query.argName());
      List<String> argv = new ArrayList<>();
      argv.add("-" + query.shortName());
      argv.addAll(tokens);
      if (bool(args, "json")) argv.add("--json");
      return new Call(string(args, "library"), "cli", argv);
    }
    return null;
  }

  // ───────── argument decoding ─────────

  private static List<String> strings(JsonNode args, String key) {
    JsonNode node = args.get(key);
    if (node == null || node.isNull()) return List.of();
    if (!node.isArray()) throw new IllegalArgumentException("'" + key + "' must be an array of strings");
    List<String> out = new ArrayList<>();
    for (JsonNode element : node) {
      if (!element.isTextual()) throw new IllegalArgumentException("'" + key + "' must be an array of strings");
      String value = element.asText();
      if (value.startsWith("-")) {
        throw new IllegalArgumentException("'" + key + "' element '" + value + "' must not start with '-'");
      }
      if (!value.isEmpty()) out.add(value);
    }
    return out;
  }

  private static String string(JsonNode args, String key) {
    JsonNode node = args.get(key);
    if (node == null || node.isNull()) return null;
    if (!node.isTextual()) throw new IllegalArgumentException("'" + key + "' must be a string");
    String value = node.asText();
    if (value.startsWith("-")) throw new IllegalArgumentException("'" + key + "' must not start with '-'");
    return value.isEmpty() ? null : value;
  }

  private static boolean bool(JsonNode args, String key) {
    JsonNode node = args.get(key);
    if (node == null || node.isNull()) return false;
    if (!node.isBoolean()) throw new IllegalArgumentException("'" + key + "' must be a boolean");
    return node.asBoolean();
  }

  // ───────── schema building ─────────

  static Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
    Map<String, Object> tool = new LinkedHashMap<>();
    tool.put("name", name);
    tool.put("description", description);
    tool.put("inputSchema", inputSchema);
    return tool;
  }

  static Map<String, Object> schema(Map<String, Object> properties, List<String> required) {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("properties", properties);
    if (!required.isEmpty()) schema.put("required", required);
    schema.put("additionalProperties", false);
    return schema;
  }

  static Map<String, Object> stringArray(String description) {
    return Map.of("type", "array", "items", Map.of("type", "string"), "description", description);
  }

  private static Map<String, Object> bool(String description) {
    return Map.of("type", "boolean", "description", description);
  }
}
