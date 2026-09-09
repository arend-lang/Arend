package org.arend.frontend;

import org.apache.commons.cli.*;
import org.arend.core.definition.Definition;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.cli.CliSetup;
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

  private boolean run(String[] args) {
    ParsedArgs parsed = parseArgs(args);
    if (parsed.cmdLine() == null) return !parsed.failed();
    CommandLine cmdLine = parsed.cmdLine();

    CommandContext ctx = new CommandContext();
    if (!CliSetup.bootstrap(ctx, cmdLine)) return false;
    CliSetup.classifyRequestedTargets(ctx, cmdLine);

    if (cmdLine.hasOption("i")) return runRepl(ctx, cmdLine);

    // In JSON mode stdout has to carry the document and nothing else, so the redirect covers
    // library loading as well as dispatch -- loader chatter would otherwise land in it. It is
    // also what restores the streams on every exit below, including the early ones.
    try (JsonOutputRedirect json = JsonOutputRedirect.open(cmdLine)) {
      if (!CliSetup.loadRequestedLibraries(ctx, cmdLine) || ctx.exitWithError) return false;
      return Dispatch.execute(ctx, cmdLine, json) == 0;
    }
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
    if (!new ConsoleMain().run(args)) {
      System.exit(1);
    }
  }
}
