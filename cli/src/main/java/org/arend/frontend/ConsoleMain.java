package org.arend.frontend;

import org.arend.cli.ArendOptions;
import org.apache.commons.cli.*;
import org.arend.cli.LockedFlags;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.daemon.DaemonStart;
import org.arend.frontend.cli.daemon.DaemonStop;
import org.arend.frontend.cli.daemon.client.DaemonRpc;
import org.arend.frontend.cli.daemon.mcp.DaemonMcpServer;
import org.arend.frontend.cli.daemon.server.DaemonMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.arend.frontend.cli.JsonOutputRedirect;
import org.arend.query.*;
import org.arend.frontend.repl.PlainCliRepl;
import org.arend.frontend.repl.jline.JLineCliRepl;
import org.arend.prelude.Prelude;

import java.util.*;


public class ConsoleMain {
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
      for (ConsoleQueryTool tool : ArendOptions.QUERY_TOOLS) {
        if (hasRawFlag(args, tool.shortName(), tool.longName())) { ConsoleHelpRenderer.printTopicHelp(tool.cliHelp()); return ParsedArgs.served(); }
      }
      // else fall through: the general -h handler below prints the grouped help.
    }
    try {
      Options cmdOptions = ArendOptions.create();
      CommandLine cmdLine = new DefaultParser().parse(cmdOptions, args);

      if (cmdLine.hasOption("h")) {
        ConsoleHelp.printGrouped(cmdOptions, List.of(ArendOptions.SHOW_TIMES, ArendOptions.SHOW_SIZES,
            ArendOptions.SHOW_MODULES, ArendOptions.SHOW_MODULES_WITH_INSTANCES));
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
    CliSetup.bootstrap(ctx, cmdLine);
    if (ctx.exitWithError) return null;
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
    ctx.servedLibraries.addAll(ctx.requestedLibraries);
    return ctx;
  }

  /**
   * The context a {@link org.arend.daemon.server.DaemonServer} serves: a
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
    // Only the library search path yet: a command a daemon answers needs no server of its own.
    CliSetup.readLibDirs(ctx, cmdLine);

    if (cmdLine.hasOption("mcp")) return DaemonMcpServer.run(ctx.libDirs);

    // A recoverable error must not reach a daemon: -d would bake a bad -L into a server that
    // outlives the command, and routing would hand the work to one built from a context this
    // process already knows is wrong. The REPL is exempt -- it loads its own startup targets, and
    // CliSetup.readLibDirs documents such an error as recoverable.
    boolean replRun = cmdLine.hasOption("i") && !ArendOptions.hasDaemonControlFlag(cmdLine);
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

    CliSetup.buildServer(ctx, cmdLine);
    CliSetup.classifyRequestedTargets(ctx, cmdLine);
    if (ctx.exitWithError && !replRun) return 1;

    if (cmdLine.hasOption("i")) return runRepl(ctx, cmdLine) ? 0 : 1;

    // In JSON mode stdout has to carry the document and nothing else, so the redirect covers
    // library loading as well as dispatch -- loader chatter would otherwise land in it. It is
    // also what restores the streams on every exit below, including the early ones.
    try (JsonOutputRedirect json = JsonOutputRedirect.open(cmdLine, null)) {
      if (!CliSetup.loadRequestedLibraries(ctx, cmdLine) || ctx.exitWithError) return 1;
      return Dispatch.execute(ctx, cmdLine, json);
    }
  }

  /**
   * Handles {@code -d} and the {@code --daemon-*} control flags. None of them loads a library in
   * this process -- the child JVM does that.
   *
   * @return the exit code, or null when no control flag was given and the run should continue.
   */
  private Integer runDaemonControl(CommandContext ctx, CommandLine cmdLine) {
    List<String> given = ArendOptions.DAEMON_CONTROL_FLAGS.stream().filter(cmdLine::hasOption).toList();
    if (given.isEmpty()) return null;
    if (given.size() > 1) {
      System.err.println("[ERROR] only one of -d / --daemon-stop / --daemon-ping /"
          + " --daemon-status / --daemon-refresh may be given");
      return 1;
    }
    String flag = given.getFirst();
    boolean daemonStart = flag.equals("d");
    List<String> positional = cmdLine.getArgList();
    if (!daemonStart && positional.size() > 1) {
      System.err.println("[ERROR] daemon control requires at most one positional library reference");
      return 1;
    }

    // No positional -> ./arend.yaml, mirroring the non-daemon default.
    String libRef = positional.isEmpty() ? "." : positional.getFirst();

    if (daemonStart) {
      List<String> childArgs = bootstrapFlagsToForward(cmdLine);
      return DaemonStart.run(positional.isEmpty() ? List.of(libRef) : positional, ctx.libDirs, childArgs);
    }
    if (flag.equals("daemon-stop")) {
      return DaemonStop.run(libRef, ctx.libDirs);
    }
    // --daemon-ping, --daemon-status and --daemon-refresh: the op is the flag's own name.
    String op = flag.substring("daemon-".length());
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
