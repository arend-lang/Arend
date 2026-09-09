package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.core.definition.Definition;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.daemon.DaemonStart;
import org.arend.frontend.cli.daemon.DaemonStop;
import org.arend.frontend.cli.daemon.LockedFlags;
import org.arend.frontend.cli.daemon.client.DaemonRpc;
import org.arend.frontend.cli.daemon.server.DaemonMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.arend.frontend.cli.JsonOutputRedirect;
import org.arend.frontend.cli.commands.TypecheckPipeline;
import org.arend.frontend.library.*;
import org.arend.frontend.query.*;
import org.arend.frontend.query.classhierarchy.ClassHierarchyTool;
import org.arend.frontend.query.findusages.FindUsagesTool;
import org.arend.frontend.query.proofsearch.ProofSearchTool;
import org.arend.frontend.query.scopeinfo.ScopeInfoTool;
import org.arend.frontend.query.symbolsearch.SymbolSearchTool;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.prelude.Prelude;


import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class ConsoleMain {


  // Owned by TypecheckPipeline, which reads them back off the parsed CommandLine; re-declaring
  // them here would let the two drift apart silently.
  private final static String SHOW_TIMES = TypecheckPipeline.SHOW_TIMES;
  private final static String SHOW_SIZES = TypecheckPipeline.SHOW_SIZES;
  private final static String SHOW_MODULES = TypecheckPipeline.SHOW_MODULES;
  private final static String SHOW_MODULES_WITH_INSTANCES = TypecheckPipeline.SHOW_MODULES_WITH_INSTANCES;

  /** The query tools, in help/dispatch order; iterated for registration, {@code --help} and dispatch. */
  public static final List<ConsoleQueryTool> QUERY_TOOLS = List.of(
          SymbolSearchTool.INSTANCE, ProofSearchTool.INSTANCE, FindUsagesTool.INSTANCE,
          ClassHierarchyTool.INSTANCE, ScopeInfoTool.INSTANCE);


  /**
   * What parsing argv came to. A null {@code cmdLine} means there is nothing left to run, and
   * {@code failed} says which of the two reasons applies: the request was served in full
   * ({@code --help}, {@code -v}) or the arguments were rejected. They must not share an exit
   * code -- asking for help is not an error.
   */
  public record ParsedArgs(CommandLine cmdLine, boolean failed) {
    static ParsedArgs served() { return new ParsedArgs(null, false); }
    static ParsedArgs rejected() { return new ParsedArgs(null, true); }
    static ParsedArgs of(CommandLine cmdLine) { return new ParsedArgs(cmdLine, false); }
  }

  public static ParsedArgs parseArgs(String[] args) {
    if (hasRawFlag(args, "h", "help")) {
      for (ConsoleQueryTool tool : QUERY_TOOLS) {
        if (hasRawFlag(args, tool.shortName(), tool.longName())) { tool.printHelp(); return ParsedArgs.served(); }
      }
      // else fall through: the general -h handler below prints the grouped help.
    }
    try {
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
      cmdOptions.addOption(Option.builder("d").longOpt("daemon").desc("start a daemon for the given library: a LIBRARY positional anchored to an arend.yaml, or a name resolved via -L, defaulting to ./arend.yaml when omitted. The daemon does the normal load+typecheck+persist once, then idles serving future client requests.").build());
      cmdOptions.addOption(Option.builder().longOpt("daemon-stop").desc("stop the daemon serving the given library (single positional library reference).").build());
      cmdOptions.addOption(Option.builder().longOpt("daemon-ping").desc("ping the daemon serving the given library; reports IDLE or BUSY.").build());
      cmdOptions.addOption(Option.builder().longOpt("daemon-status").desc("dump the daemon's state: queue depth, uptime, current task, protocol version and locked flags.").build());
      cmdOptions.addOption(Option.builder().longOpt("daemon-refresh").desc("re-run the bootstrap pipeline on the daemon's warm context so source edits are picked up.").build());
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
      CommandLine cmdLine = new DefaultParser().parse(cmdOptions, args);

      if (cmdLine.hasOption("h")) {
        ConsoleHelp.printGrouped(cmdOptions, List.of(SHOW_TIMES, SHOW_SIZES, SHOW_MODULES, SHOW_MODULES_WITH_INSTANCES));
        return ParsedArgs.served();
      }

      if (cmdLine.hasOption("v")) {
        System.out.println("Arend " + Prelude.VERSION);
        return ParsedArgs.served();
      }

      return ParsedArgs.of(cmdLine);
    } catch (ParseException e) {
      System.err.println(e.getMessage());
      return ParsedArgs.rejected();
    }
  }

  /** True if {@code -<shortOpt>} or {@code --<longOpt>} appears as a raw arg token (stopping at a bare {@code --}). */
  private static boolean hasRawFlag(String[] args, String shortOpt, String longOpt) {
    String shortFlag = "-" + shortOpt, longFlag = "--" + longOpt;
    for (String arg : args) {
      if (arg.equals("--")) break;
      if (arg.equals(shortFlag) || arg.equals(longFlag)) return true;
    }
    return false;
  }

  /**
   * Parses {@code args} with {@code tool} and runs the resulting {@link ConsoleQueryTool.ConsoleToolRunner}
   * against {@code ctx}; returns {@code null} on parse failure (the tool has already printed the
   * diagnostic), else the tool's exit code.
   */
  private static Integer runQueryTool(ConsoleQueryTool tool, String[] args, ConsoleQueryTool.QueryContext ctx) {
    ConsoleQueryTool.ConsoleToolRunner parsed = tool.parseArgs(args);
    return parsed == null ? null : parsed.run(ctx);
  }










  /**
   * Builds a context with its libraries loaded and the requested command run once, and hands it
   * back so that further commands can be run against it with
   * {@link Dispatch#run(CommandContext, String[])}.
   *
   * <p>The first command's diagnostics are not fatal: a library that does not typecheck is
   * still worth holding, since the next command can re-run it or query it. Only a failure that
   * leaves nothing to hold returns null -- rejected arguments, a library that would not load,
   * or an unhandled exception.
   *
   * @return the loaded context, or null if there is nothing to hold.
   */
  public CommandContext warmContext(String[] args) {
    ParsedArgs parsed = parseArgs(args);
    if (parsed.cmdLine() == null) return null;
    CommandLine cmdLine = parsed.cmdLine();

    CommandContext ctx = new CommandContext();
    if (!CliSetup.bootstrap(ctx, cmdLine) || ctx.exitWithError) return null;
    CliSetup.classifyRequestedTargets(ctx, cmdLine);
    if (!CliSetup.loadRequestedLibraries(ctx, cmdLine)) return null;

    try {
      Dispatch.execute(ctx, cmdLine);
    } catch (Throwable t) {
      System.err.println("[ERROR] unhandled exception while loading the context:");
      t.printStackTrace();
      return null;
    }
    // This command's verdict is not the next one's.
    ctx.beginCommand();
    return ctx;
  }

  /**
   * The context a {@link org.arend.frontend.cli.daemon.server.DaemonServer} serves: a
   * {@link #warmContext} that also remembers the argv it was built from, which the daemon's
   * {@code status} and {@code refresh} ops need.
   */
  public CommandContext runDaemonBootstrap(String[] args) {
    CommandContext ctx = warmContext(args);
    if (ctx != null) ctx.bootstrapArgs = args.clone();
    return ctx;
  }

  /**
   * Whether this command may be served by a daemon. Decided here from the flags alone, because
   * {@link DaemonRpc#tryRouteCli} returns the daemon's exit code and the client exits with it: a
   * refusal on the far side is not a fallback, it is the answer the user gets.
   *
   * <p>Besides {@code --no-daemon}, two flags mean a command cannot be served. {@code -i} is the
   * REPL, which the daemon rejects outright. {@code -s} names the sources to typecheck and is
   * read only on the cold-context path ({@link CliSetup#classifyRequestedTargets}), so a served
   * command would ignore it and report success for the daemon's own library instead.
   */
  static boolean isRoutable(CommandLine cmdLine) {
    return !cmdLine.hasOption("no-daemon")
        && !cmdLine.hasOption("i")
        && !cmdLine.hasOption("s");
  }

  /** @return the process exit code. */
  private int run(String[] args) {
    ParsedArgs parsed = parseArgs(args);
    if (parsed.cmdLine() == null) return parsed.failed() ? 1 : 0;
    CommandLine cmdLine = parsed.cmdLine();

    CommandContext ctx = new CommandContext();
    if (!CliSetup.bootstrap(ctx, cmdLine)) return 1;

    // A recoverable error must not reach a daemon: -d would bake a bad -L into a server that
    // outlives the command, and routing would hand the work to one built from a context this
    // process already knows is wrong. The REPL is exempt -- it loads its own startup targets, and
    // CliSetup.bootstrap documents such an error as recoverable.
    boolean replRun = cmdLine.hasOption("i") && !hasDaemonControlFlag(cmdLine);
    if (ctx.exitWithError && !replRun) return 1;

    // Before the positionals are classified: a daemon flag takes a library *reference*, which is
    // looser than a library -- "." names the daemon for the current directory but is not a
    // library name, so classifying first rejected it before this branch was reached.
    Integer daemonControl = runDaemonControl(ctx, cmdLine);
    if (daemonControl != null) return daemonControl;

    // Likewise before: an ordinary command goes to whichever daemon serves this library, and
    // there is no reason to classify or load anything here when the work happens elsewhere. The
    // daemon's exit code is returned verbatim -- 130 for a cancellation is the whole reason it
    // reports one, and collapsing every non-zero code to 1 throws that away.
    if (isRoutable(cmdLine)) {
      OptionalInt routed = DaemonRpc.tryRouteCli(args, cmdLine.getArgList(), ctx.libDirs);
      if (routed.isPresent()) return routed.getAsInt();
    }

    CliSetup.classifyRequestedTargets(ctx, cmdLine);
    if (ctx.exitWithError && !replRun) return 1;

    if (cmdLine.hasOption("i")) return runRepl(ctx, cmdLine) ? 0 : 1;

    // In JSON mode stdout has to carry the document and nothing else, so the redirect covers
    // library loading as well as dispatch -- loader chatter would otherwise land in it. It is
    // also what restores the streams on every exit below, including the early ones.
    try (JsonOutputRedirect json = JsonOutputRedirect.open(cmdLine)) {
      if (!CliSetup.loadRequestedLibraries(ctx, cmdLine) || ctx.exitWithError) return 1;
      return Dispatch.execute(ctx, cmdLine, json);
    }
  }

  /** Whether argv asks for one of the daemon control flags {@link #runDaemonControl} handles. */
  public static boolean hasDaemonControlFlag(CommandLine cmdLine) {
    return cmdLine.hasOption("d")
        || cmdLine.hasOption("daemon-stop")
        || cmdLine.hasOption("daemon-ping")
        || cmdLine.hasOption("daemon-status")
        || cmdLine.hasOption("daemon-refresh");
  }

  /**
   * Handles {@code -d} and the {@code --daemon-*} control flags. None of them loads a library in
   * this process -- the child JVM does that.
   *
   * @return the exit code, or null when no control flag was given and the run should continue.
   */
  private Integer runDaemonControl(CommandContext ctx, CommandLine cmdLine) {
    boolean daemonStart = cmdLine.hasOption("d");
    boolean daemonStop = cmdLine.hasOption("daemon-stop");
    boolean daemonPing = cmdLine.hasOption("daemon-ping");
    boolean daemonStatus = cmdLine.hasOption("daemon-status");
    boolean daemonRefresh = cmdLine.hasOption("daemon-refresh");
    if (!hasDaemonControlFlag(cmdLine)) return null;

    int chosen = (daemonStart ? 1 : 0) + (daemonStop ? 1 : 0) + (daemonPing ? 1 : 0)
        + (daemonStatus ? 1 : 0) + (daemonRefresh ? 1 : 0);
    if (chosen > 1) {
      System.err.println("[ERROR] only one of -d / --daemon-stop / --daemon-ping /"
          + " --daemon-status / --daemon-refresh may be given");
      return 1;
    }
    List<String> positional = cmdLine.getArgList();
    if (positional.size() > 1) {
      System.err.println("[ERROR] daemon mode requires at most one positional library reference");
      return 1;
    }

    // No positional -> ./arend.yaml, mirroring the non-daemon default.
    String libRef = positional.isEmpty() ? "." : positional.getFirst();

    if (daemonStart) {
      return DaemonStart.run(libRef, ctx.libDirs, bootstrapFlagsToForward(cmdLine));
    }
    if (daemonStop) {
      return DaemonStop.run(libRef, ctx.libDirs);
    }
    String op = daemonPing ? "ping" : daemonStatus ? "status" : "refresh";
    int rc = DaemonRpc.run(libRef, ctx.libDirs, op);
    if (rc == DaemonRpc.NO_DAEMON) {
      System.err.println("[ERROR] " + op + ": no daemon running for the given library");
      return 1;
    }
    return rc;
  }

  /**
   * The bootstrap-affecting flags {@code -d} hands to the child, so the child owns the same
   * locked state this process was asked for. Read off {@link LockedFlags#ALL} rather than listed
   * again, except {@code -L}, which {@link DaemonStart} forwards from {@code ctx.libDirs}.
   */
  private static List<String> bootstrapFlagsToForward(CommandLine cmdLine) {
    List<String> extra = new ArrayList<>();
    for (LockedFlags.Flag flag : LockedFlags.ALL) {
      if (flag.kind() == LockedFlags.Kind.MULTI || !cmdLine.hasOption(flag.name())) continue;
      extra.add(flag.display());
      if (flag.kind() == LockedFlags.Kind.SINGLE) extra.add(cmdLine.getOptionValue(flag.name()));
    }
    return extra;
  }

  /**
   * Starts the REPL. Positional arguments are libraries to add and modules to auto-load; the
   * batch-only options do not apply, so warn and ignore them rather than silently dropping them.
   */
  private boolean runRepl(CommandContext ctx, CommandLine cmdLine) {
    warnUnsupportedReplOptions(cmdLine);
    List<ModulePath> autoloadModules = ctx.requestedModules.stream().map(pair -> pair.proj1).distinct().toList();
    String replKind = cmdLine.getOptionValue("i", "jline");
    switch (replKind.toLowerCase()) {
      case "plain" -> PlainCliRepl.launch(ctx.requestedLibraries, autoloadModules, ctx.libDirs, ctx.server);
      case "jline" -> JLineCliRepl.launch(ctx.requestedLibraries, autoloadModules, ctx.libDirs, ctx.server);
      default -> {
        System.err.println("[ERROR] Unrecognized repl type: " + replKind);
        return false;
      }
    }
    // A bad argument only printed a diagnostic; the prompt still started, but the run failed.
    return !ctx.exitWithError;
  }

  /** Options REPL ({@code -i}) mode acts on: the mode selector plus everything feeding library/module resolution. */
  private static final Set<String> REPL_CONSUMED_OPTIONS = Set.of("i", "L", "s", "e", "m");

  /**
   * Warns about every parsed option that REPL ({@code -i}) mode does not act on. Rather than track a
   * list of batch-only options, this ignores anything not in {@link #REPL_CONSUMED_OPTIONS}, so newly
   * added batch options are covered automatically.
   */
  private void warnUnsupportedReplOptions(CommandLine cmdLine) {
    for (Option option : cmdLine.getOptions()) {
      // Every consumed option has a short name, so a null short name is necessarily not consumed
      // (and Set.of rejects a null argument to contains).
      if (option.getOpt() != null && REPL_CONSUMED_OPTIONS.contains(option.getOpt())) continue;
      String name = option.getLongOpt() != null ? "--" + option.getLongOpt() : "-" + option.getOpt();
      System.out.println("[WARNING] Option " + name + " is not supported in REPL (-i) mode and will be ignored.");
    }
  }







  /**
   * Returns true if any typecheckable definition reachable from {@code group} has
   * status {@link Definition.TypeCheckingStatus#HAS_ERRORS}. Used to gate persist:
   * caching a module that contains an erroneous def would only feed the
   * deserialize → orphan-shell-detected → clear → re-typecheck cycle in
   * {@code CliServerRequester.loadBinaryCache}.
   */

  /**
   * Re-reports typechecking errors stored in the server's {@code ErrorService} for
   * source modules in the given library. Without this step, a daemon that
   * bootstrapped with HAS_ERRORS modules would silently drop the error reports on
   * the second and later client requests: persist now skips those modules, load
   * doesn't see them in the binary cache, and the typechecker skips already-
   * typechecked defs — so the per-request {@code moduleResults} map never gets
   * an ERROR entry. Walking the ErrorService here restores the per-invocation
   * "Number of modules with errors" summary that the old re-typecheck cycle
   * incidentally provided.
   */

  /**
   * The file that {@code --json -ss} writes its diagnostics to. Honours an
   * explicit {@code --log-file <path>}; otherwise defaults to
   * {@code <java.io.tmpdir>/arend-symbol-search.log}, overwritten each run.
   */
  private static Path resolveJsonLogPath(CommandLine cmdLine) {
    String custom = cmdLine.getOptionValue("log-file");
    if (custom != null && !custom.isEmpty()) return Paths.get(custom);
    return Paths.get(System.getProperty("java.io.tmpdir"), "arend-symbol-search.log");
  }


  public static void main(String[] args) {
    // The daemon child JVM's entry point. Detected before parseArgs, because commons-cli would
    // reject the flag, and before any other output, so nothing is written before the parent has
    // pointed stdout and stderr at daemon.log.
    if (args.length > 0 && "--daemon-bootstrap".equals(args[0])) {
      DaemonMain.run(args);
      return;
    }
    int exitCode = new ConsoleMain().run(args);
    if (exitCode != 0) {
      System.exit(exitCode);
    }
  }
}
