package org.arend.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.arend.daemon.DaemonPaths;
import org.arend.query.ConsoleQueryTool;
import org.arend.query.classhierarchy.ClassHierarchyTool;
import org.arend.query.findusages.FindUsagesTool;
import org.arend.query.proofsearch.ProofSearchTool;
import org.arend.query.scopeinfo.ScopeInfoTool;
import org.arend.query.symbolsearch.SymbolSearchTool;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * The options of the {@code arend} command line. Here rather than in the CLI because a daemon
 * answered from inside IntelliJ reads the same command lines, and must read them the same way.
 */
public final class ArendOptions {
  private ArendOptions() {}

  public static final String SHOW_TIMES = "show-times";
  public static final String SHOW_SIZES = "show-sizes";
  public static final String SHOW_MODULES = "show-modules";
  public static final String SHOW_MODULES_WITH_INSTANCES = "show-modules-with-instances";

  /** The daemon control flags, each a command of its own: at most one is given, and none to a daemon. */
  public static final List<String> DAEMON_CONTROL_FLAGS =
      List.of("d", "daemon-stop", "daemon-ping", "daemon-status", "daemon-refresh");

  /** The query tools, in help/dispatch order; iterated for registration, {@code --help} and dispatch. */
  public static final List<ConsoleQueryTool> QUERY_TOOLS = List.of(
          SymbolSearchTool.INSTANCE, ProofSearchTool.INSTANCE, FindUsagesTool.INSTANCE,
          ClassHierarchyTool.INSTANCE, ScopeInfoTool.INSTANCE);

  /** Whether {@code cmdLine} gives one of {@link #DAEMON_CONTROL_FLAGS}. */
  public static boolean hasDaemonControlFlag(CommandLine cmdLine) {
    return DAEMON_CONTROL_FLAGS.stream().anyMatch(cmdLine::hasOption);
  }

  /** Why a daemon refuses {@code cmdLine} outright, as every daemon says it; null when it does not. */
  public static String daemonRefusal(CommandLine cmdLine) {
    if (cmdLine.hasOption("i")) return "[ERROR] -i (REPL) is not supported in daemon mode";
    if (hasDaemonControlFlag(cmdLine)) {
      return "[ERROR] daemon control flags are not valid inside a daemon-served command";
    }
    return null;
  }

  /** The query tool {@code cmdLine} asks for, or null for a typecheck. */
  public static ConsoleQueryTool queryTool(CommandLine cmdLine) {
    return QUERY_TOOLS.stream().filter(tool -> cmdLine.hasOption(tool.shortName())).findFirst().orElse(null);
  }

  /**
   * Where a query with {@code --json} writes its diagnostics, to keep stdout the document alone:
   * {@code --log-file} when given, as a client in {@code cwd} meant it, else
   * {@code <java.io.tmpdir>/arend-symbol-search.log}.
   */
  public static Path jsonLogPath(CommandLine cmdLine, String cwd) {
    String custom = cmdLine.getOptionValue("log-file");
    if (custom != null && !custom.isEmpty()) return DaemonPaths.against(cwd, custom);
    return Paths.get(System.getProperty("java.io.tmpdir"), "arend-symbol-search.log");
  }

  /** What a query with {@code --json} says when its log cannot be opened, and it writes diagnostics to stderr. */
  public static String jsonLogFailed(ConsoleQueryTool tool, Path logPath, String reason) {
    return "[WARN] cannot open -" + tool.shortName() + " log file " + logPath + " (" + reason
        + "); routing diagnostics to stderr instead";
  }

  /** What a query with {@code --json} says once done, on stderr, of where its diagnostics went. */
  public static String jsonLogWritten(ConsoleQueryTool tool, Path logPath) {
    return "[INFO] -" + tool.shortName() + " diagnostics written to " + logPath;
  }

  /** Every option {@code arend} accepts. */
  public static Options create() {
    Options cmdOptions = new Options();
    cmdOptions.addOption("h", "help", false, "print this message");
    cmdOptions.addOption(Option.builder("L").longOpt("libdir").hasArg().argName("dir").desc("directory containing libraries").build());
    cmdOptions.addOption(Option.builder("s").longOpt("sources").hasArg().argName("dir").desc("project source directory").build());
    cmdOptions.addOption(Option.builder("e").longOpt("extensions").hasArg().argName("dir").desc("language extensions directory").build());
    cmdOptions.addOption(Option.builder("m").longOpt("extension-main").hasArg().argName("class").desc("main extension class").build());
    cmdOptions.addOption(Option.builder("c").longOpt("double-check").desc("double check correctness of the result").build());
    cmdOptions.addOption(Option.builder("i").longOpt("interactive").hasArg().optionalArg(true).argName("plain|jline").desc("start an interactive REPL").build());
    cmdOptions.addOption(Option.builder("p").longOpt("print").hasArg().argName("MODULE[:DEF]").desc("after the typecheck, print the elaborated/typechecked form of MODULE (or a single DEF inside it): implicits filled in, eliminators desugared.").build());
    // The query tools (-ss/-ps/-fu/-ch/-sc): registered from the shared registry so each flag's option identity lives on its ConsoleQueryTool.
    for (ConsoleQueryTool tool : QUERY_TOOLS) {
      cmdOptions.addOption(Option.builder(tool.shortName()).longOpt(tool.longName())
          .hasArgs().argName(tool.argName()).desc(tool.cliDescription()).build());
    }
    cmdOptions.addOption(Option.builder().longOpt("json").desc("with -ss/-ps/-fu/-sc/-ch: print results as a single JSON object on stdout ({results:[...],count:N}; -sc uses {target,entries:[...],count:N}; -ch uses {target,superclasses:[...],subclasses:[...],instances:[...],newSites:[...],counts}); all diagnostics ([INFO]/[WARN]/[ERROR], query echo) go to a log file, keeping stdout pure JSON and the console clean (ignored in REPL mode). For -fu, each usage is a separate entry (never grouped by row).").build());
    cmdOptions.addOption(Option.builder("d").longOpt("daemon").desc("start one daemon holding the given libraries (LIBRARY positionals: an arend.yaml, its directory, or a name resolved via -L; ./arend.yaml when omitted), loaded in the order given, the way IntelliJ holds a project's libraries. Refused if one of them is already served by another daemon; libraries under ~/.arend/libs are the exception, any daemon may load them. The daemon does the normal load+typecheck+persist once, then serves arend commands routed to it, and agents through --mcp.").build());
    cmdOptions.addOption(Option.builder().longOpt("daemon-stop").desc("stop the daemon serving the given library (single positional library reference), with every other library it serves. Not for a library under ~/.arend/libs, which several daemons may share.").build());
    cmdOptions.addOption(Option.builder().longOpt("daemon-ping").desc("ping the daemon serving the given library; reports IDLE or BUSY.").build());
    cmdOptions.addOption(Option.builder().longOpt("daemon-status").desc("dump the daemon's state: queue depth, uptime, current task, protocol version and locked flags.").build());
    cmdOptions.addOption(Option.builder().longOpt("daemon-refresh").desc("re-run the bootstrap pipeline on the daemon's warm context so source edits are picked up.").build());
    cmdOptions.addOption(Option.builder().longOpt("mcp").desc("run an MCP server on stdin/stdout for an agent, exposing the running daemons' commands as tools; each call goes to the daemon holding the library it names, found the way a routed command finds it. Register it once, e.g. `claude mcp add --scope user arend-daemons -- arend --mcp`.").build());
    cmdOptions.addOption(Option.builder().longOpt("no-daemon").desc("force in-process execution even if a daemon serves the requested library.").build());
    cmdOptions.addOption(Option.builder().longOpt("log-file").hasArg().argName("path").desc("with --json -ss/-ps/-fu/-sc/-ch: write diagnostics here instead of the default <tmpdir>/arend-symbol-search.log").build());
    cmdOptions.addOption("r", "recompile", false, "recompile all modules from source, ignoring binary caches (.arc files)");
    cmdOptions.addOption(null, "serialize", false, "after typechecking, persist typechecked modules as .arc binary caches; without this flag, no .arc files are written");
    cmdOptions.addOption("t", "test", false, "run tests");
    cmdOptions.addOption("v", "version", false, "print language version");
    cmdOptions.addOption(Option.builder().longOpt(SHOW_TIMES).desc("after typechecking, print every definition's typecheck duration, sorted descending").build());
    cmdOptions.addOption(Option.builder().longOpt(SHOW_SIZES).desc("after typechecking, print every definition's typechecked core-term size (number of subterms), sorted descending").build());
    cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES).desc("after typechecking, print the module import-DAG in topological order via Tarjan SCC. Single modules: `[Module]`; import cycles: `[M1, M2, ...]`.").build());
    cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES_WITH_INSTANCES).desc("like --show-modules, but restricted to modules that define at least one \\instance -- useful for spotting cycles among instance-providing modules").build());
    return cmdOptions;
  }
}
