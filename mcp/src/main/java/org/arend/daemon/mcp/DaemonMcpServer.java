package org.arend.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.daemon.BuildIdentity;
import org.arend.daemon.DaemonPaths;
import org.arend.daemon.LockFile;
import org.arend.daemon.wire.FrameChannel;

import org.arend.daemon.client.DaemonClient;
import org.arend.daemon.client.DaemonRpc;
import org.arend.prelude.Prelude;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class DaemonMcpServer {
  /**
   * The revisions served statelessly, each request carrying its version, capabilities and client in
   * {@code _meta}, as 2026-07-28 and later have it.
   */
  private static final List<String> MODERN_VERSIONS = List.of("2026-07-28");
  /** The revisions served after an {@code initialize} handshake, latest first: what this server needs of them is the same in all. */
  private static final List<String> LEGACY_VERSIONS = List.of("2025-11-25", "2025-06-18", "2025-03-26", "2024-11-05");

  private static final String META_VERSION = "io.modelcontextprotocol/protocolVersion";
  private static final String META_CAPABILITIES = "io.modelcontextprotocol/clientCapabilities";
  private static final String META_CLIENT = "io.modelcontextprotocol/clientInfo";
  private static final String META_SERVER = "io.modelcontextprotocol/serverInfo";
  private static final int UNSUPPORTED_PROTOCOL_VERSION = -32022;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final BufferedReader in;
  private final PrintStream out;
  private final List<Path> libDirs;
  private final Path workingDir;
  private final List<Map<String, Object>> tools = new ArrayList<>(DaemonMcpTools.definitions());
  private final DaemonMcpSkills skills;
  /** What the client calls itself in {@code initialize}: the agent whose skills the skill tools work on. */
  private volatile String clientName;
  private final ConcurrentHashMap<String, Running> running = new ConcurrentHashMap<>();
  private final ExecutorService pool = Executors.newCachedThreadPool(Thread.ofPlatform().daemon().name("arend-mcp-call").factory());

  /**
   * A tool call from the line that asked for it to its reply. A cancel can come before the call
   * has a daemon connection, or before the daemon has read the request, which then refuses it as
   * naming nothing; either way it is remembered here rather than lost.
   */
  private static final class Running {
    final String requestId = UUID.randomUUID().toString();
    private DaemonClient client;
    private boolean cancelled;
    private boolean resent;

    synchronized void cancel() {
      cancelled = true;
      send();
    }

    /** @return false if the call was cancelled before it got to the daemon. */
    synchronized boolean attach(DaemonClient client) {
      this.client = client;
      return !cancelled;
    }

    /** The daemon refused the cancel: it overtook the request. The request has been sent by now. */
    synchronized void refused() {
      if (cancelled && !resent) {
        resent = true;
        send();
      }
    }

    private void send() {
      if (client == null) return;
      try {
        client.sendCancel(requestId);
      } catch (IOException e) {
        System.err.println("[MCP] could not cancel: " + e.getMessage());
      }
    }
  }

  DaemonMcpServer(InputStream in, PrintStream out, List<Path> libDirs, Path workingDir) {
    this(in, out, libDirs, workingDir, new DaemonMcpSkills(Path.of(System.getProperty("user.home"))));
  }

  DaemonMcpServer(InputStream in, PrintStream out, List<Path> libDirs, Path workingDir, DaemonMcpSkills skills) {
    this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    this.out = out;
    this.libDirs = libDirs;
    this.workingDir = workingDir;
    this.skills = skills;
    tools.addAll(DaemonMcpSkills.definitions());
  }

  public static int run(List<Path> libDirs) {
    // The jar as it is at the start, for warnIfReplaced to compare with.
    BuildIdentity.ofThisProcess();
    PrintStream protocol = System.out;
    System.setOut(System.err);
    new DaemonMcpServer(System.in, protocol, libDirs, Path.of("").toAbsolutePath()).serve();
    return 0;
  }

  /** {@code arend --mcp} without the CLI, as the IntelliJ plugin registers it: from its own jars, with no {@code -L}. */
  public static void main(String[] args) {
    System.exit(run(List.of()));
  }

  /** How long the calls still running may take to wind down once the agent has closed stdin. */
  private static final int EXIT_GRACE_SECONDS = 10;

  /**
   * Serves until the agent closes stdin, which is how a stdio client shuts its server down. Nobody
   * reads what a call still running would answer then, and a typecheck it left running holds its
   * daemon's worker against every other client: each is cancelled on its daemon, and the server
   * exits once they have wound down, or after {@value #EXIT_GRACE_SECONDS}s.
   */
  void serve() {
    try {
      String line;
      while ((line = in.readLine()) != null) {
        if (!line.isBlank()) handleLine(line);
      }
    } catch (IOException e) {
      // The agent is gone, as when it closes stdin.
    } finally {
      running.values().forEach(Running::cancel);
      pool.shutdown();
      try {
        if (!pool.awaitTermination(EXIT_GRACE_SECONDS, TimeUnit.SECONDS)) pool.shutdownNow();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  // ───────── stdio transport ─────────

  private void handleLine(String line) {
    JsonNode message;
    try {
      message = MAPPER.readTree(line);
    } catch (IOException e) {
      write(error(null, -32700, "Parse error: " + e.getMessage()));
      return;
    }
    if ("tools/call".equals(message.path("method").asText()) && message.hasNonNull("id")) {
      // Registered here, on the reader thread, so that a cancel on a later line always finds it.
      String key = message.get("id").toString();
      Running call = new Running();
      // An id names one request while it is in flight. A second one under it would find the first's
      // entry, share its daemon request and take its cancel, so it is refused instead.
      if (running.putIfAbsent(key, call) != null) {
        write(error(message.get("id"), -32600, "Invalid Request: the request " + key + " is still in progress"));
        return;
      }
      pool.submit(() -> {
        try {
          write(safeDispatch(message));
        } finally {
          // This call's own entry: once its reply is out, the client may reuse the id for the next one.
          running.remove(key, call);
        }
      });
    } else {
      write(safeDispatch(message));
    }
  }

  private synchronized void write(Map<String, Object> message) {
    if (message == null) return;
    try {
      out.println(MAPPER.writeValueAsString(message));
      out.flush();
    } catch (IOException e) {
      System.err.println("[MCP] cannot encode a reply: " + e);
    }
  }

  // ───────── JSON-RPC ─────────

  private Map<String, Object> safeDispatch(JsonNode message) {
    try {
      return dispatch(message);
    } catch (RuntimeException | LinkageError e) {
      e.printStackTrace();
      JsonNode id = message == null ? null : message.get("id");
      return id == null ? null : error(id, -32603, "Internal error: " + e);
    }
  }

  private Map<String, Object> dispatch(JsonNode message) {
    if (message == null || !message.isObject()) return error(null, -32600, "Invalid Request");
    JsonNode id = message.get("id");
    JsonNode method = message.get("method");
    if (method == null || !method.isTextual()) {
      return id == null ? null : error(id, -32600, "Invalid Request");
    }
    JsonNode params = message.path("params");

    if (id == null || id.isNull()) {
      if ("notifications/cancelled".equals(method.asText())) {
        Running call = running.get(params.path("requestId").toString());
        if (call != null) call.cancel();
      }
      return null;
    }

    // A request of a stateless revision says which in its _meta; one of an earlier revision, which
    // the initialize handshake set up, does not. Both are served, as the specification allows.
    JsonNode meta = params.path("_meta");
    if (meta.has(META_VERSION)) return modern(id, method.asText(), params, meta);

    return switch (method.asText()) {
      case "initialize" -> {
        clientName = params.path("clientInfo").path("name").textValue();
        yield result(id, initialize(params.path("protocolVersion").asText("")));
      }
      case "ping" -> result(id, Map.of());
      case "tools/list" -> result(id, Map.of("tools", tools));
      case "tools/call" -> warnIfReplaced(callTool(id, params, clientName));
      default -> error(id, -32601, "Method not found: " + method.asText());
    };
  }

  /**
   * A request of a stateless revision: everything the server needs to know of the client is in
   * {@code meta}, nothing is kept from an earlier request, and every result says what it is and who
   * answers it.
   */
  private Map<String, Object> modern(JsonNode id, String method, JsonNode params, JsonNode meta) {
    String version = meta.path(META_VERSION).asText("");
    if (!MODERN_VERSIONS.contains(version)) {
      List<String> supported = new ArrayList<>(MODERN_VERSIONS);
      supported.addAll(LEGACY_VERSIONS);
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("code", UNSUPPORTED_PROTOCOL_VERSION);
      error.put("message", "Unsupported protocol version");
      error.put("data", Map.of("supported", supported, "requested", version));
      return response(id, "error", error);
    }
    if (!meta.path(META_CAPABILITIES).isObject()) {
      return error(id, -32602, "Invalid params: _meta must carry " + META_CAPABILITIES);
    }
    Map<String, Object> answer = switch (method) {
      case "server/discover" -> result(id, discover());
      case "ping" -> result(id, Map.of());
      case "tools/list" -> result(id, Map.of("tools", tools));
      case "tools/call" -> warnIfReplaced(callTool(id, params, meta.path(META_CLIENT).path("name").textValue()));
      default -> error(id, -32601, "Method not found: " + method);
    };
    if (!(answer.get("result") instanceof Map<?, ?> result)) return answer;
    Map<String, Object> complete = new LinkedHashMap<>();
    complete.put("resultType", "complete");
    for (Map.Entry<?, ?> entry : result.entrySet()) complete.put(String.valueOf(entry.getKey()), entry.getValue());
    complete.put("_meta", Map.of(META_SERVER, SERVER_INFO));
    return result(id, complete);
  }

  private static final Map<String, Object> SERVER_INFO = Map.of("name", "arend-daemons", "version", String.valueOf(Prelude.VERSION));
  private static final Map<String, Object> CAPABILITIES = Map.of("tools", Map.of("listChanged", false));
  private static final String INSTRUCTIONS = "Tools of the running Arend daemons: each mirrors an `arend` option and goes"
      + " to the daemon serving its `library`, limited to that library; daemon_list shows which daemon"
      + " serves what. A library named goes to the daemon owning a library of that name, else to the"
      + " longest-running daemon that loaded a zip or a ~/.arend/libs library of that name. skills_list"
      + " shows the skills for working in Arend the arend-skills repository offers; install them only when the user asks.";

  /** The answer to an earlier revision's handshake: the revision the client asked for if it is one served here, else the latest. */
  private static Map<String, Object> initialize(String requested) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("protocolVersion", LEGACY_VERSIONS.contains(requested) ? requested : LEGACY_VERSIONS.getFirst());
    result.put("capabilities", CAPABILITIES);
    result.put("serverInfo", SERVER_INFO);
    result.put("instructions", INSTRUCTIONS);
    return result;
  }

  /** What {@code server/discover} answers: the stateless revisions served here, and what the handshake would have said. */
  private static Map<String, Object> discover() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("supportedVersions", MODERN_VERSIONS);
    result.put("capabilities", CAPABILITIES);
    result.put("instructions", INSTRUCTIONS);
    return result;
  }

  /** A tool call; {@code clientName} is what the client calls itself, which tells the skill tools whose skills they work on. */
  private Map<String, Object> callTool(JsonNode id, JsonNode params, String clientName) {
    String name = params.path("name").asText("");
    if (DaemonMcpSkills.handles(name)) {
      JsonNode arguments = params.path("arguments");
      try {
        return result(id, toolResult(skills.call(name, arguments.isObject() ? arguments : MAPPER.createObjectNode(), clientName), false));
      } catch (IllegalArgumentException | IOException e) {
        return result(id, toolResult(e.getMessage(), true));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return result(id, toolResult("cancelled", true));
      }
    }
    DaemonMcpTools.Call call;
    try {
      call = DaemonMcpTools.resolve(name, params.get("arguments"));
    } catch (IllegalArgumentException e) {
      return result(id, toolResult(e.getMessage(), true));
    }
    if (call == null) return error(id, -32602, "Unknown tool: " + name);
    if (call.op() == null) return result(id, toolResult(listDaemons(), false));

    Target target;
    try {
      target = target(call.library());
    } catch (IllegalArgumentException e) {
      return result(id, toolResult(e.getMessage(), true));
    }
    if ("shutdown".equals(call.op()) && target.library() != null && !target.library().canOwn()) {
      return result(id, toolResult(target.library().libraryConfig + " is not owned by any one daemon; stop a"
          + " daemon through a library it owns (see daemon_list).", true));
    }
    if (call.argv() == null) return result(id, warned(target.warning(), invoke(id, target.daemon(), call.op(), Map.of())));
    // The library first, as a positional: that is what limits the command to it.
    List<String> argv = new ArrayList<>();
    if (target.library() != null) argv.add(target.library().libraryConfig.toString());
    argv.addAll(call.argv());
    return result(id, warned(target.warning(), invoke(id, target.daemon(), call.op(), Map.of("args", argv))));
  }

  // ───────── daemons ─────────

  /**
   * A daemon, the library a call to it is about (null for every library the daemon serves), and what
   * to warn about in its answer (null for nothing).
   */
  private record Target(LockFile daemon, DaemonPaths library, String warning) {}

  /**
   * The daemon a call about {@code library} goes to, as {@link #find} has it, with a word when it runs
   * another build than this server's: it answers, but as that build. A command line would run the
   * command itself instead ({@link DaemonRpc#tryRouteCli}); this server has only the daemons.
   */
  private Target target(String library) {
    Target target = find(library);
    String otherBuild = DaemonRpc.otherBuild(target.daemon(), BuildIdentity.ofThisProcess());
    if (otherBuild == null) return target;
    String warning = "[WARN] " + target.daemon().describe() + " runs " + otherBuild + ", and answers as that"
        + " build. Restart it to have it run this one: `arend --daemon-stop`, then `arend -d`.";
    return new Target(target.daemon(), target.library(), target.warning() == null ? warning : target.warning() + "\n" + warning);
  }

  /** The daemon a call about {@code library} goes to: with none named, the working directory's, else the only one there is to talk to. */
  private Target find(String library) {
    if (library != null) {
      String reference = library.startsWith("~/") ? System.getProperty("user.home") + library.substring(1) : library;
      Named named = runningLibrariesNamed(reference);
      List<DaemonPaths> candidates = named.owned().isEmpty() ? named.loaded() : named.owned();
      if (candidates.size() > 1) throw new IllegalArgumentException(ambiguity(library, candidates));
      DaemonPaths paths = candidates.isEmpty() ? DaemonPaths.resolveByName(reference, libDirs, false) : candidates.getFirst();
      if (paths == null) {
        throw new IllegalArgumentException("cannot find the library '" + library + "': give a library name found"
            + " under ~/.arend/libs or -L, or a path to a library directory, its arend.yaml or its zip.");
      }
      LockFile daemon = DaemonRpc.daemonFor(List.of(paths));
      if (daemon == null) {
        throw new IllegalArgumentException("no running daemon serves " + paths.libraryConfig
            + "; start one with `arend -d " + library + "`.\n\n" + listDaemons());
      }
      return new Target(daemon, paths,
          named.owned().isEmpty() || named.loaded().isEmpty() ? null : shadowed(library, paths, named.loaded()));
    }

    DaemonPaths here = DaemonPaths.resolve(workingDir);
    if (here != null) {
      LockFile daemon = DaemonRpc.daemonFor(List.of(here));
      if (daemon != null) return new Target(daemon, here, null);
    } else {
      // An agent started in a project's directory, as one registered for the project is.
      LockFile daemon = DaemonRpc.projectDaemon(workingDir, false);
      if (daemon != null) return new Target(daemon, null, null);
    }
    List<LockFile> live = DaemonRpc.liveDaemons();
    List<LockFile> usable = live.stream().filter(DaemonRpc::compatible).toList();
    if (usable.size() == 1) return new Target(usable.getFirst(), null, null);
    throw new IllegalArgumentException(live.isEmpty() ? "no daemon is running; start one with `arend -d LIBRARY...`."
        : usable.isEmpty() ? "no running daemon speaks this build's protocol; restart one with `arend --daemon-stop`,"
            + " then `arend -d`.\n\n" + listDaemons()
        : "several daemons are running; say which library with `library`.\n\n" + listDaemons());
  }

  /**
   * The libraries of one name the running daemons hold, longest-running daemon first: those they own
   * -- projects they were started with -- and the zips and shared libraries they loaded, which
   * {@link DaemonRpc#daemonFor} sends to the longest-running daemon that loaded each.
   */
  private record Named(List<DaemonPaths> owned, List<DaemonPaths> loaded) {}

  /**
   * The libraries a bare {@code name} may mean, judged by what the running daemons hold rather than
   * by where the name resolves from the working directory. A name means a project before a zip or a
   * shared library. A daemon holds one library of a name at most, but two daemons may each own a
   * different directory of the same name, and then the name decides nothing.
   */
  private static Named runningLibrariesNamed(String name) {
    List<DaemonPaths> owned = new ArrayList<>();
    List<DaemonPaths> loaded = new ArrayList<>();
    for (LockFile daemon : DaemonRpc.liveDaemons()) {
      // One no request can reach is no candidate: it would make a name ambiguous that one daemon answers for.
      if (!DaemonRpc.compatible(daemon)) continue;
      Set<String> own = new HashSet<>(daemon.ownedLibraries());
      List<String> libraries = new ArrayList<>(daemon.libraries());
      libraries.addAll(daemon.loaded());
      for (String library : libraries) {
        // The lock's paths are canonical already: compare the name before touching the file system.
        if (!DaemonPaths.nameOf(Paths.get(library)).equals(name)) continue;
        DaemonPaths paths = DaemonPaths.resolve(Paths.get(library));
        if (paths == null) continue;
        List<DaemonPaths> kind = own.contains(library) ? owned : loaded;
        if (kind.stream().noneMatch(other -> other.libraryConfig.equals(paths.libraryConfig))) kind.add(paths);
      }
    }
    return new Named(owned, loaded);
  }

  /** What to say when {@code name} fits several running libraries, instead of picking one of them. */
  private static String ambiguity(String name, List<DaemonPaths> named) {
    return "[WARN] several running daemons serve a library named '" + name + "': "
        + named.stream().map(paths -> paths.libraryConfig.toString()).collect(Collectors.joining(", "))
        + "; nothing was run. Give the path of the one you mean.";
  }

  /** What to say when {@code name} meant the project {@code used} over the zips or shared libraries {@code loaded}. */
  private static String shadowed(String name, DaemonPaths used, List<DaemonPaths> loaded) {
    return "[WARN] a library named '" + name + "' is both a project a daemon owns and a zip or a ~/.arend/libs"
        + " library a daemon loaded: "
        + loaded.stream().map(paths -> paths.libraryConfig.toString()).collect(Collectors.joining(", "))
        + "; using the project, " + used.libraryConfig + ". Give a path to choose another.";
  }

  private static String listDaemons() {
    List<LockFile> live = DaemonRpc.liveDaemons();
    if (live.isEmpty()) return "No daemon is running. Start one with `arend -d LIBRARY...`.";
    StringBuilder out = new StringBuilder("Running daemons, oldest first:");
    for (LockFile daemon : live) {
      out.append("\n\nPID ").append(daemon.pid()).append(", started ").append(Instant.ofEpochMilli(daemon.startedAt()));
      if (daemon.isHostedByIde()) out.append(", inside ").append(daemon.host());
      out.append("\n  libraries:");
      for (String library : daemon.libraries()) out.append("\n    ").append(library);
      List<String> loaded = daemon.loaded().stream().filter(library -> !daemon.libraries().contains(library)).toList();
      if (!loaded.isEmpty()) {
        out.append("\n  also loaded, not its own:");
        for (String library : loaded) out.append("\n    ").append(library);
      }
    }
    return out.toString();
  }

  private Map<String, Object> invoke(JsonNode mcpId, LockFile daemon, String op, Map<String, Object> extra) {
    // Registered when its line was read: a tools/call without an id is a notification, never run.
    Running call = running.get(mcpId.toString());
    Collector collector = new Collector();
    try (DaemonClient client = DaemonClient.connect(daemon)) {
      if (!call.attach(client)) return collector.toolResult(FrameChannel.CANCELLED_EXIT_CODE);
      int exitCode = client.invoke(call.requestId, op, extra, collector::accept,
          accepted -> { if (!accepted) call.refused(); });
      return collector.toolResult(exitCode);
    } catch (IOException | IllegalArgumentException e) {
      return toolResult("cannot reach " + daemon.describe() + " at " + daemon.socketPath() + ": "
          + e.getMessage(), true);
    }
  }

  // ───────── results ─────────

  private static Map<String, Object> result(JsonNode id, Object result) {
    return response(id, "result", result);
  }

  private static Map<String, Object> error(JsonNode id, int code, String message) {
    return response(id, "error", Map.of("code", code, "message", message));
  }

  private static Map<String, Object> response(JsonNode id, String kind, Object body) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put(kind, body);
    return response;
  }

  /**
   * {@code response} with a word that this server's own jar has changed since it started, when it
   * has: it still answers, through daemons of their own build, but it is the old build that does,
   * and it may fail on the next class it reads. Reconnecting the server runs the new one.
   */
  private static Map<String, Object> warnIfReplaced(Map<String, Object> response) {
    BuildIdentity build = BuildIdentity.ofThisProcess();
    if (build == null || build.isCurrent() || response == null || !(response.get("result") instanceof Map<?, ?> result)
        || !(result.get("content") instanceof List<?>)) {
      return response;
    }
    @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) result;
    Map<String, Object> out = new LinkedHashMap<>(response);
    out.put("result", warned("[WARN] the arend jar this MCP server runs from, " + build.path() + ", has changed since it"
        + " started; reconnect the arend-daemons server to run the new build.", typed));
    return out;
  }

  /** {@code result} with {@code warning}, unless null, as its first text. */
  private static Map<String, Object> warned(String warning, Map<String, Object> result) {
    if (warning == null) return result;
    List<Object> content = new ArrayList<>();
    content.add(Map.of("type", "text", "text", warning));
    content.addAll((List<?>) result.get("content"));
    Map<String, Object> out = new LinkedHashMap<>(result);
    out.put("content", content);
    return out;
  }

  private static Map<String, Object> toolResult(String text, boolean isError) {
    return toolResult(List.of(text), isError);
  }

  private static Map<String, Object> toolResult(List<String> texts, boolean isError) {
    List<Map<String, Object>> content = new ArrayList<>();
    for (String text : texts) content.add(Map.of("type", "text", "text", text));
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("content", content);
    result.put("isError", isError);
    return result;
  }

  private static final class Collector {
    private final StringBuilder stdout = new StringBuilder();
    private final StringBuilder stderr = new StringBuilder();

    synchronized void accept(Map<String, Object> frame) {
      String data = String.valueOf(frame.getOrDefault("data", ""));
      switch (String.valueOf(frame.get("kind"))) {
        case "stdout" -> stdout.append(data);
        case "stderr" -> stderr.append(data);
        case "state" -> stdout.append(frame.get("state")).append('\n');
        case "status" -> {
          Map<String, Object> status = new LinkedHashMap<>(frame);
          status.remove("id");
          status.remove("kind");
          try {
            stdout.append(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(status)).append('\n');
          } catch (IOException e) {
            stdout.append(status).append('\n');
          }
        }
        case "error" -> stderr.append("[DAEMON ERROR] ").append(frame.get("message")).append('\n');
        default -> { }
      }
    }

    synchronized Map<String, Object> toolResult(int exitCode) {
      List<String> texts = new ArrayList<>();
      String out = shorten(collapseCarriageReturns(stdout));
      String err = shorten(collapseCarriageReturns(stderr));
      if (!out.isBlank()) texts.add(out);
      if (!err.isBlank()) texts.add("stderr:\n" + err);
      if (exitCode == FrameChannel.CANCELLED_EXIT_CODE) texts.add("cancelled");
      else if (exitCode != 0) texts.add("exit code " + exitCode);
      if (texts.isEmpty()) texts.add("(no output)");
      return DaemonMcpServer.toolResult(texts, exitCode != 0);
    }
  }

  /** The most of one stream a tool result carries: some ten thousand tokens of an agent's context. */
  static final int MAX_TEXT = 40_000;

  /**
   * {@code text} cut to {@link #MAX_TEXT}, if it is longer: its beginning, where the first errors
   * are, and its end, where the summary is, with what was left out said between them, at a line break.
   */
  static String shorten(String text) {
    if (text.length() <= MAX_TEXT) return text;
    int headEnd = text.lastIndexOf('\n', MAX_TEXT * 3 / 4);
    if (headEnd <= 0) headEnd = MAX_TEXT * 3 / 4;
    int tailStart = text.indexOf('\n', text.length() - MAX_TEXT / 4);
    if (tailStart < 0 || tailStart <= headEnd) tailStart = text.length() - MAX_TEXT / 4;
    return text.substring(0, headEnd) + "\n[... " + (tailStart - headEnd) + " characters left out: name fewer targets,"
        + " or run the command with `arend` to see all of it ...]" + text.substring(tailStart);
  }

  /**
   * Keeps what a terminal would show of each line: the progress counter rewrites one line with
   * {@code \r} hundreds of times, and only its last state is worth an agent's context.
   */
  static String collapseCarriageReturns(CharSequence text) {
    StringBuilder out = new StringBuilder(text.length());
    String[] lines = text.toString().split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      if (i > 0) out.append('\n');
      String line = lines[i].endsWith("\r") ? lines[i].substring(0, lines[i].length() - 1) : lines[i];
      out.append(line.substring(line.lastIndexOf('\r') + 1));
    }
    return out.toString();
  }
}
