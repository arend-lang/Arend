package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.commands.TypecheckPipeline;
import org.arend.frontend.query.ConsoleQueryTool;

import java.util.Set;

/**
 * Per-op dispatch on a populated {@link CommandContext}. Pulled out of {@code
 * ConsoleMain.run()} so the daemon worker can call exactly the same code as the local
 * CLI path — one implementation, two callers.
 *
 * <p>Preconditions: {@code ctx.server / libraryManager / requestedLibraries} are
 * already populated (via {@link CliSetup} for the local path, or carried over the warm
 * daemon context for the daemon path). This method does NOT load libraries — the
 * caller is responsible for that.
 *
 * <p>Returns a Unix-style exit code: 0 success, non-zero failure. Honors
 * {@link CommandContext#exitWithError} on top of each branch's own return.
 */
public final class Dispatch {
  private Dispatch() {}

  /**
   * Dispatch with a {@code --json} redirect scoped to the dispatch itself. Correct when the
   * caller has nothing to run beforehand that could write to stdout — the daemon path, whose
   * libraries are already warm, and the daemon bootstrap.
   */
  public static int execute(CommandContext ctx, CommandLine cmdLine) {
    try (JsonOutputRedirect json = JsonOutputRedirect.open(cmdLine)) {
      return execute(ctx, cmdLine, json);
    }
  }

  /**
   * Dispatch against an already-open {@code json} redirect. The local CLI path opens it
   * before loading libraries, so loader chatter is captured in the log too and stdout stays
   * pure JSON.
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
