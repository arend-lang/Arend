package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.commands.TypecheckPipeline;
import org.arend.frontend.query.ConsoleQueryTool;

import java.util.Set;

/**
 * Picks the command to run on a populated {@link CommandContext}: one of the query tools, or
 * the typecheck pipeline.
 *
 * <p>Preconditions: {@code ctx.server / libraryManager / requestedLibraries} are already
 * populated by {@link CliSetup}. Dispatch loads nothing itself.
 *
 * <p>Returns a Unix-style exit code: 0 success, non-zero failure. Honours
 * {@link CommandContext#exitWithError} on top of each branch's own return.
 */
public final class Dispatch {
  private Dispatch() {}

  /**
   * Runs the command named by {@code args} against a context whose libraries are already
   * loaded. Everything belonging to a previous command is discarded first.
   *
   * @return the exit code the caller should report.
   */
  public static int run(CommandContext ctx, String[] args) {
    return run(ctx, args, null);
  }

  /**
   * @param clientCwd the directory the command was issued from, when that is not this process's
   *                  own -- a daemon serves requests from anywhere, and a relative path in the
   *                  argv means the client's directory, never the daemon's. Null for a local run.
   */
  public static int run(CommandContext ctx, String[] args, String clientCwd) {
    ConsoleMain.ParsedArgs parsed = ConsoleMain.parseArgs(args);
    if (parsed.cmdLine() == null) {
      // parseArgs printed the help, the version, or the parse error. Only the first two are a
      // success: a command that did nothing because its arguments were rejected must not
      // report 0.
      return parsed.failed() ? 1 : 0;
    }
    ctx.beginCommand();
    CliSetup.populateRequestedTargets(ctx, parsed.cmdLine(), clientCwd);
    // A target that named nothing has been reported. Stop rather than dispatching with an
    // empty scope, which means "the whole library".
    if (ctx.exitWithError) return 1;
    return execute(ctx, parsed.cmdLine());
  }

  /**
   * Dispatch with a {@code --json} redirect scoped to the dispatch itself. Correct when the
   * caller has nothing to run beforehand that could write to stdout.
   */
  public static int execute(CommandContext ctx, CommandLine cmdLine) {
    try (JsonOutputRedirect json = JsonOutputRedirect.open(cmdLine)) {
      return execute(ctx, cmdLine, json);
    }
  }

  /**
   * Dispatch against an already-open {@code json} redirect. The CLI opens it before loading
   * libraries, so loader chatter is captured in the log too and stdout stays pure JSON.
   */
  public static int execute(CommandContext ctx, CommandLine cmdLine, JsonOutputRedirect json) {
    // The query tools (-ss/-ps/-fu/-ch/-sc) all implement ConsoleQueryTool and share one
    // registry, one parsed-args interface and one context, so dispatch is a registry lookup
    // rather than a block per flag. The same tools back the REPL's `:` commands, which is
    // what keeps CLI and REPL behaviour identical.
    ConsoleQueryTool.QueryContext queryCtx = new ConsoleQueryTool.QueryContext(
        ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter,
        json.stdout(), json.active(), Set.of(), ctx.cancellation);
    for (ConsoleQueryTool tool : ConsoleMain.QUERY_TOOLS) {
      if (!cmdLine.hasOption(tool.shortName())) continue;
      ConsoleQueryTool.ConsoleToolRunner parsed = tool.parseArgs(cmdLine.getOptionValues(tool.shortName()));
      // The tool has already printed its own diagnostic on a parse failure.
      if (parsed == null) return 1;
      return (parsed.run(queryCtx) == 0 && !ctx.exitWithError) ? 0 : 1;
    }

    boolean tcOk = TypecheckPipeline.run(ctx, cmdLine);
    return (tcOk && !ctx.exitWithError) ? 0 : 1;
  }
}
