package org.arend.frontend.cli.daemon.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arend.frontend.cli.daemon.DaemonPaths;
import org.arend.frontend.cli.daemon.LockFile;
import org.arend.frontend.cli.daemon.client.DaemonClient;
import org.arend.frontend.cli.daemon.client.DaemonRpc;
import org.arend.frontend.cli.daemon.server.DaemonServer;
import org.arend.frontend.cli.daemon.wire.SocketAddress;
import org.arend.prelude.Prelude;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class DaemonMcpServer {
  static final String PROTOCOL_VERSION = "2025-06-18";

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final BufferedReader in;
  private final PrintStream out;
  private final List<Path> libDirs;
  private final Path workingDir;
  private final List<Map<String, Object>> tools = DaemonMcpTools.definitions();
  private final ConcurrentHashMap<String, Running> running = new ConcurrentHashMap<>();
  private final ExecutorService pool = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "arend-mcp-call");
    t.setDaemon(true);
    return t;
  });

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
    this.in = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    this.out = out;
    this.libDirs = libDirs;
    this.workingDir = workingDir;
  }

  public static int run(List<Path> libDirs) {
    PrintStream protocol = System.out;
    System.setOut(System.err);
    new DaemonMcpServer(System.in, protocol, libDirs, Path.of("").toAbsolutePath()).serve();
    return 0;
  }

  void serve() {
    try {
      String line;
      while ((line = in.readLine()) != null) {
        if (!line.isBlank()) handleLine(line);
      }
    } catch (IOException e) {
      // The agent is gone; what it asked for is still answered, in case it is only its stdin.
    } finally {
      pool.shutdown();
      try {
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
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
      running.put(key, call);
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

    return switch (method.asText()) {
      case "initialize" -> result(id, initialize());
      case "ping" -> result(id, Map.of());
      case "tools/list" -> result(id, Map.of("tools", tools));
      case "tools/call" -> callTool(id, params);
      default -> error(id, -32601, "Method not found: " + method.asText());
    };
  }

  private static Map<String, Object> initialize() {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("protocolVersion", PROTOCOL_VERSION);
    result.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
    result.put("serverInfo", Map.of("name", "arend-daemons", "version", String.valueOf(Prelude.VERSION)));
    result.put("instructions", "Tools of the running Arend daemons: each mirrors an `arend` option and goes"
        + " to the daemon serving its `library`, limited to that library; daemon_list shows which daemon"
        + " serves what.");
    return result;
  }

  private Map<String, Object> callTool(JsonNode id, JsonNode params) {
    String name = params.path("name").asText("");
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
    if (call.argv() == null) return result(id, invoke(id, target.daemon(), call.op(), Map.of()));
    // The library first, as a positional: that is what limits the command to it.
    List<String> argv = new ArrayList<>();
    if (target.library() != null) argv.add(target.library().libraryConfig.toString());
    argv.addAll(call.argv());
    return result(id, invoke(id, target.daemon(), call.op(), Map.of("args", argv)));
  }

  // ───────── daemons ─────────

  /** A daemon, and the library a call to it is about; null for every library the daemon serves. */
  private record Target(LockFile daemon, DaemonPaths library) {}

  /** The daemon a call about {@code library} goes to: with none named, the working directory's, else the only one. */
  private Target target(String library) {
    if (library != null) {
      String reference = library.startsWith("~/") ? System.getProperty("user.home") + library.substring(1) : library;
      DaemonPaths paths = DaemonPaths.resolveByName(reference, libDirs, false);
      if (paths == null) {
        throw new IllegalArgumentException("cannot find the library '" + library + "': give a library name found"
            + " under ~/.arend/libs or -L, or a path to a library directory or its arend.yaml.");
      }
      LockFile daemon = DaemonRpc.daemonFor(List.of(paths));
      if (daemon == null) {
        throw new IllegalArgumentException("no running daemon serves " + paths.libraryConfig
            + "; start one with `arend -d " + library + "`.\n\n" + listDaemons());
      }
      return new Target(daemon, paths);
    }

    DaemonPaths here = DaemonPaths.resolve(workingDir);
    if (here != null) {
      LockFile daemon = DaemonRpc.daemonFor(List.of(here));
      if (daemon != null) return new Target(daemon, here);
    }
    List<LockFile> live = DaemonRpc.liveDaemons();
    if (live.size() == 1) return new Target(live.getFirst(), null);
    throw new IllegalArgumentException(live.isEmpty()
        ? "no daemon is running; start one with `arend -d LIBRARY...`."
        : "several daemons are running; say which library with `library`.\n\n" + listDaemons());
  }

  private static String listDaemons() {
    List<LockFile> live = DaemonRpc.liveDaemons();
    if (live.isEmpty()) return "No daemon is running. Start one with `arend -d LIBRARY...`.";
    StringBuilder out = new StringBuilder("Running daemons, oldest first:");
    for (LockFile daemon : live) {
      out.append("\n\nPID ").append(daemon.pid()).append(", started ").append(Instant.ofEpochMilli(daemon.startedAt()));
      out.append("\n  libraries:");
      for (String library : daemon.libraries()) out.append("\n    ").append(library);
    }
    return out.toString();
  }

  private Map<String, Object> invoke(JsonNode mcpId, LockFile daemon, String op, Map<String, Object> extra) {
    Running call = running.getOrDefault(mcpId.toString(), new Running());
    Collector collector = new Collector();
    try (DaemonClient client = DaemonClient.connect(SocketAddress.parse(daemon.socketPath()))) {
      if (!call.attach(client)) return collector.toolResult(DaemonServer.CANCELLED_EXIT_CODE);
      int exitCode = client.invoke(call.requestId, op, extra, collector::accept,
          accepted -> { if (!accepted) call.refused(); });
      return collector.toolResult(exitCode);
    } catch (IOException | IllegalArgumentException e) {
      return toolResult("cannot reach the daemon (PID " + daemon.pid() + ") at " + daemon.socketPath() + ": "
          + e.getMessage(), true);
    }
  }

  // ───────── results ─────────

  private static Map<String, Object> result(JsonNode id, Object result) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("result", result);
    return response;
  }

  private static Map<String, Object> error(JsonNode id, int code, String message) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("jsonrpc", "2.0");
    response.put("id", id);
    response.put("error", Map.of("code", code, "message", message));
    return response;
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
      String out = collapseCarriageReturns(stdout);
      String err = collapseCarriageReturns(stderr);
      if (!out.isBlank()) texts.add(out);
      if (!err.isBlank()) texts.add("stderr:\n" + err);
      if (exitCode == DaemonServer.CANCELLED_EXIT_CODE) texts.add("cancelled");
      else if (exitCode != 0) texts.add("exit code " + exitCode);
      if (texts.isEmpty()) texts.add("(no output)");
      return DaemonMcpServer.toolResult(texts, exitCode != 0);
    }
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
