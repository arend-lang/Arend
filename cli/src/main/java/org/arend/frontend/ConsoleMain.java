package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.cli.daemon.DaemonMain;
import org.arend.frontend.library.*;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.JsonOutputRedirect;
import org.arend.frontend.cli.commands.TypecheckPipeline;
import org.arend.frontend.query.*;
import org.arend.frontend.query.classhierarchy.ClassHierarchyTool;
import org.arend.frontend.query.findusages.FindUsagesTool;
import org.arend.frontend.query.proofsearch.ProofSearchTool;
import org.arend.frontend.query.scopeinfo.ScopeInfoTool;
import org.arend.frontend.query.symbolsearch.SymbolSearchTool;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.prelude.Prelude;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import java.util.*;

import static org.arend.frontend.cli.Dispatch.execute;
import static org.arend.frontend.cli.daemon.client.DaemonRpc.tryRouteCli;

public class ConsoleMain {
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
     * Exposed so the daemon worker can re-parse per-request CLI args on the warm context.
     */
    public static CommandLine parseArgs(String[] args) {
        // Per-flag help is resolved against the raw argv, before commons-cli sees it: `-ss` takes
        // unlimited arguments, so `-ss --help` would otherwise be rejected outright as a missing
        // argument rather than reaching any handler.
        if (hasRawFlag(args, "h", "help")) {
            if (hasRawFlag(args, "ai", "ai-pipeline")) { ConsoleHelp.printAi(); return null; }
            for (ConsoleQueryTool tool : QUERY_TOOLS) {
                if (hasRawFlag(args, tool.shortName(), tool.longName())) { tool.printHelp(); return null; }
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
            cmdOptions.addOption(Option.builder().longOpt("log-file").hasArg().argName("path").desc("with --json -ss/-ps/-fu/-sc/-ch: write diagnostics here instead of the default <tmpdir>/arend-symbol-search.log").build());
            cmdOptions.addOption(Option.builder("ai").longOpt("ai-pipeline").desc("agent-oriented typecheck: quiet by default (verbose output goes to a per-invocation log), maintains the <library>/.sig/ signature mirror. Pass `-ai --help` for full grammar.").build());
            cmdOptions.addOption(Option.builder("d").longOpt("daemon").desc("start a daemon for the given library. Requires either a LIBRARY positional anchored to an arend.yaml (or a name resolved via -L; defaults to ./arend.yaml when omitted), or a `-s <dir>` synthetic library (in which case the daemon is keyed to <dir> and its state lives in <dir>/.arend/). The daemon does the normal load+typecheck+persist+ai-finalize once, then idles serving future client requests.").build());
            cmdOptions.addOption(Option.builder().longOpt("daemon-stop").desc("stop the daemon serving the given library (single positional library reference).").build());
            cmdOptions.addOption(Option.builder().longOpt("daemon-ping").desc("ping the daemon serving the given library; reports IDLE or BUSY.").build());
            cmdOptions.addOption(Option.builder().longOpt("daemon-status").desc("dump status").build());
            cmdOptions.addOption(Option.builder().longOpt("daemon-refresh").desc("re-run the bootstrap pipeline (-ai) on the daemon's warm context so source edits are picked up.").build());
            cmdOptions.addOption(Option.builder().longOpt("no-daemon").desc("force in-process execution even if a daemon serves the requested library.").build());
            cmdOptions.addOption(Option.builder().longOpt("no-quiet").desc("with -ai: disable the per-invocation log split; emit verbose narration to stdout/stderr like a pre-quiet run. No effect outside -ai.").build());
            cmdOptions.addOption("r", "recompile", false, "recompile all modules from source, ignoring binary caches (.arc files)");
            cmdOptions.addOption(Option.builder().longOpt("slow-warn").hasArg().argName("ms").desc("emit a [WARN] line on stderr when typechecking of an individual definition exceeds this many milliseconds (default 5000; pass 0 to disable)").build());
            cmdOptions.addOption(null, "no-serialize", false, "do not persist typechecked modules as .arc binary caches after typechecking; serialization is on by default.");
            cmdOptions.addOption("t", "test", false, "run tests");
            cmdOptions.addOption("v", "version", false, "print language version");
            cmdOptions.addOption(Option.builder().longOpt(SHOW_TIMES).desc("after typechecking, print every definition's typecheck duration, sorted descending").build());
            cmdOptions.addOption(Option.builder().longOpt(SHOW_SIZES).desc("after typechecking, print every definition's typechecked core-term size (number of subterms), sorted descending").build());
            cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES).desc("after typechecking, print the module import-DAG in topological order via Tarjan SCC. Single modules: `[Module]`; import cycles: `[M1, M2, ...]`.").build());
            cmdOptions.addOption(Option.builder().longOpt(SHOW_MODULES_WITH_INSTANCES).desc("like --show-modules, but restricted to modules that define at least one \\instance -- useful for spotting cycles among instance-providing modules").build());
            CommandLine cmdLine = new DefaultParser().parse(cmdOptions, args);

            if (cmdLine.hasOption("h")) {
                ConsoleHelp.printGrouped(cmdOptions, List.of("no-quiet", "slow-warn",
                        SHOW_TIMES, SHOW_SIZES, SHOW_MODULES, SHOW_MODULES_WITH_INSTANCES));
                return null;
            }

            if (cmdLine.hasOption("v")) {
                System.out.println("Arend " + Prelude.VERSION);
                return null;
            }

            return cmdLine;
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    // The long-opt names are owned by TypecheckPipeline, which reads them back off the
    // parsed CommandLine; re-declaring them here would let the two drift apart silently.
    private final static String SHOW_TIMES = TypecheckPipeline.SHOW_TIMES;
    private final static String SHOW_SIZES = TypecheckPipeline.SHOW_SIZES;
    private final static String SHOW_MODULES = TypecheckPipeline.SHOW_MODULES;
    private final static String SHOW_MODULES_WITH_INSTANCES = TypecheckPipeline.SHOW_MODULES_WITH_INSTANCES;

    /** The query tools, in help/dispatch order; iterated for registration, {@code --help} and dispatch. */
    public static final List<ConsoleQueryTool> QUERY_TOOLS = List.of(
            SymbolSearchTool.INSTANCE, ProofSearchTool.INSTANCE, FindUsagesTool.INSTANCE,
            ClassHierarchyTool.INSTANCE, ScopeInfoTool.INSTANCE);

    /**
     * Daemon-bootstrap variant of {@link #run}: parse + setup + dispatch + return the
     * warm {@link CommandContext} (or null on failure). The daemon hands the returned
     * ctx to its {@link org.arend.frontend.cli.daemon.server.DaemonServer} so subsequent
     * per-request CLI commands can dispatch against an already-loaded ArendServer.
     *
     * <p>Typecheck errors (or any other diagnostic-level failure inside {@link
     * org.arend.frontend.cli.Dispatch#execute}) do <em>not</em> abort startup: the warm
     * ctx is still useful — clients can refresh, re-typecheck, or query symbols against
     * the partially-loaded state. Only fatal infrastructure failures (argv parse error,
     * CliSetup.bootstrap or loadRequestedLibraries returning false, or an unhandled
     * exception during dispatch) cause this method to return null.
     */
    public CommandContext runDaemonBootstrap(String[] args) {
        CommandLine cmdLine = parseArgs(args);
        if (cmdLine == null) return null;
        CommandContext ctx = new CommandContext();
        ctx.bootstrapArgs = args.clone();
        if (!CliSetup.bootstrap(ctx, cmdLine)) return null;
        if (!CliSetup.loadRequestedLibraries(ctx, cmdLine)) return null;
        // Library load succeeded; from here on, typecheck errors are non-fatal for daemon
        // startup. Dispatch.execute may flip ctx.exitWithError or return non-zero, but
        // either way we still want to serve the warm context to clients.
        try {
            execute(ctx, cmdLine);
        } catch (Throwable t) {
            System.err.println("[DAEMON] unhandled exception during bootstrap typecheck:");
            t.printStackTrace();
            return null;
        }
        // Clear the diagnostic-level error flag so it doesn't leak into the first client
        // request's exit code. Per-request state is also reset by CliDispatcher.
        ctx.exitWithError = false;
        return ctx;
    }

    private boolean run(String[] args) {
        CommandLine cmdLine = parseArgs(args);
        if (cmdLine == null) return false;

        CommandContext ctx = new CommandContext();
        if (!CliSetup.bootstrap(ctx, cmdLine)) return false;
        if (ctx.exitWithError) return false;

        // Daemon control: doesn't load libraries in-process; the child JVM does.
        // Library reference is always a single positional arg.
        boolean daemonStart = cmdLine.hasOption("d");
        boolean daemonStop = cmdLine.hasOption("daemon-stop");
        boolean daemonPing = cmdLine.hasOption("daemon-ping");
        boolean daemonStatus = cmdLine.hasOption("daemon-status");
        boolean daemonRefresh = cmdLine.hasOption("daemon-refresh");
        if (daemonStart || daemonStop || daemonPing || daemonStatus || daemonRefresh) {
            int chosen = (daemonStart ? 1 : 0) + (daemonStop ? 1 : 0) + (daemonPing ? 1 : 0)
                    + (daemonStatus ? 1 : 0) + (daemonRefresh ? 1 : 0);
            if (chosen > 1) {
                System.err.println("[ERROR] only one of -d / --daemon-stop / --daemon-ping / --daemon-status / --daemon-refresh may be given");
                return false;
            }
            List<String> positional = cmdLine.getArgList();
            if (positional.size() > 1) {
                System.err.println("[ERROR] daemon mode requires at most one positional library reference");
                return false;
            }
            // Synthetic-library daemon: -s <dir> with no positional. The daemon is keyed to
            // <dir> and its inner pipeline is driven by -s/-e/-m rather than an arend.yaml.
            // Positional wins if both are given (warn the user that -s is ignored).
            String sourceDirStr = cmdLine.getOptionValue("s");
            Path syntheticSrcDir = null;
            if (sourceDirStr != null && positional.isEmpty()) {
                syntheticSrcDir = Paths.get(sourceDirStr).toAbsolutePath();
            } else if (sourceDirStr != null) {
                System.err.println("[WARN] daemon mode: positional LIBRARY given, -s " + sourceDirStr
                        + " is ignored for daemon-identity resolution (still forwarded to the inner pipeline)");
            }
            // Control ops (stop/ping/status/refresh) with no -s and no positional: try cwd's
            // arend.yaml first; if absent, see if a synthetic daemon was previously started here
            // (a `<cwd>/.arend/daemon.lock` is the marker) and target that instead.
            if (syntheticSrcDir == null && positional.isEmpty() && !daemonStart) {
                Path cwd = Paths.get(".").toAbsolutePath();
                if (!Files.isRegularFile(cwd.resolve("arend.yaml"))) {
                    org.arend.frontend.cli.daemon.DaemonPaths synth =
                            org.arend.frontend.cli.daemon.DaemonPaths.resolveSynthetic(cwd);
                    if (Files.exists(synth.lockFile)) syntheticSrcDir = cwd;
                }
            }
            // No positional + no -s → fall back to ./arend.yaml, mirroring non-daemon default.
            String libRef = positional.isEmpty() ? "." : positional.getFirst();
            int rc;
            if (daemonStart) {
                // Forward bootstrap-affecting flags so the child JVM owns the same locked state.
                List<String> extra = new ArrayList<>();
                if (cmdLine.hasOption("s")) {
                    extra.add("-s");
                    extra.add(cmdLine.getOptionValue("s"));
                }
                if (cmdLine.hasOption("e")) {
                    extra.add("-e");
                    extra.add(cmdLine.getOptionValue("e"));
                }
                if (cmdLine.hasOption("m")) {
                    extra.add("-m");
                    extra.add(cmdLine.getOptionValue("m"));
                }
                if (cmdLine.hasOption("c")) {
                    extra.add("-c");
                }
                if (cmdLine.hasOption("r")) {
                    extra.add("-r");
                }
                if (cmdLine.hasOption("no-serialize")) {
                    extra.add("--no-serialize");
                }
                if (cmdLine.hasOption("slow-warn")) {
                    extra.add("--slow-warn");
                    extra.add(cmdLine.getOptionValue("slow-warn"));
                }
                rc = org.arend.frontend.cli.daemon.DaemonStart.run(libRef, ctx.libDirs, extra, syntheticSrcDir);
            } else if (daemonStop) {
                rc = org.arend.frontend.cli.daemon.DaemonStop.run(libRef, ctx.libDirs, syntheticSrcDir);
            } else {
                String op = daemonPing ? "ping" : daemonStatus ? "status" : "refresh";
                rc = org.arend.frontend.cli.daemon.client.DaemonRpc.run(libRef, ctx.libDirs, op, syntheticSrcDir);
                if (rc == org.arend.frontend.cli.daemon.client.DaemonRpc.NO_DAEMON) {
                    System.err.println("[ERROR] " + op + ": no daemon running for the given library");
                    rc = 1;
                }
            }
            return rc == 0;
        }

        // -i REPL needs only libDirs + server (both set up by bootstrap); short-circuit before
        // loading libraries -- the REPL loads its own startup targets, so we only need the
        // positional args classified, not the libraries pushed into the server.
        if (cmdLine.hasOption("i")) {
            // In REPL mode, positional arguments are libraries to add and modules to auto-load; the
            // batch-only options do not apply, so warn and ignore them rather than silently dropping them.
            warnUnsupportedReplOptions(cmdLine);
            CliSetup.classifyRequestedTargets(ctx, cmdLine);
            List<SourceLibrary> replLibraries = ctx.requestedLibraries;
            List<ModulePath> autoloadModules = ctx.requestedModules.stream().map(pair -> pair.proj1).distinct().toList();
            String replKind = cmdLine.getOptionValue("i", "jline");
            switch (replKind.toLowerCase()) {
                case "plain":
                    PlainCliRepl.launch(replLibraries, autoloadModules, ctx.libDirs, ctx.server);
                    break;
                case "jline":
                    JLineCliRepl.launch(replLibraries, autoloadModules, ctx.libDirs, ctx.server);
                    break;
                default:
                    System.err.println("[ERROR] Unrecognized repl type: " + replKind);
                    return false;
            }
            // A failed argument only prints a diagnostic; the interactive prompt still starts.
            return !ctx.exitWithError;
        }
        // Try to route to a daemon serving the requested library. Returns empty when no
        // daemon is available or healthy — caller falls through to the in-process pipeline.
        if (!cmdLine.hasOption("no-daemon")) {
            OptionalInt remote = tryRouteCli(args, cmdLine.getArgList(), ctx.libDirs);
            if (remote.isPresent()) {
                return remote.getAsInt() == 0;
            }
        }

        // Opened around the load as well as the dispatch: in `--json` query mode the loader's
        // [INFO] chatter would otherwise land on stdout and break the JSON document.
        try (JsonOutputRedirect json = JsonOutputRedirect.open(cmdLine)) {
            if (!CliSetup.loadRequestedLibraries(ctx, cmdLine)) return false;
            if (ctx.exitWithError) return false;

            return execute(ctx, cmdLine, json) == 0;
        }
    }

    /**
     * Options REPL ({@code -i}) mode acts on: the mode selector plus everything feeding library/module resolution.
     */
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

  public static void main(String[] args) {
    // Internal entry for the daemon child JVM: never returns to normal CLI dispatch.
    // Detected here, before parseArgs, because commons-cli would reject the flag and we
    // also want to bypass any normal-CLI output buffering before the parent has wired up
    // stdout/stderr to daemon.log.
    if (args.length > 0 && ("--daemon-bootstrap".equals(args[0]) || "--daemon-bootstrap-synthetic".equals(args[0]))) {
      DaemonMain.run(args);
      return;
    }
    if (!new ConsoleMain().run(args)) {
      System.exit(1);
    }
  }
}
