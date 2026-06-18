package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.cli.commands.ProofSearch;
import org.arend.frontend.cli.commands.TypecheckPipeline;

/**
 * Per-op dispatch on a populated {@link CommandContext}. Pulled out of {@code
 * ConsoleMain.run()} so option handling stays small and each command has one entry point.
 *
 * <p>Preconditions: {@code ctx.server / libraryManager / requestedLibraries} are
 * already populated via {@link CliSetup}. This method does NOT load libraries; the
 * caller is responsible for that.
 *
 * <p>Returns a Unix-style exit code: 0 success, non-zero failure. Honors
 * {@link CommandContext#exitWithError} on top of each branch's own return.
 */
public final class Dispatch {
  private Dispatch() {}

  public static int execute(CommandContext ctx, CommandLine cmdLine) {


    if (cmdLine.hasOption("ss")) {
      org.arend.frontend.symbol.SymbolSearch.Parsed parsed =
          org.arend.frontend.symbol.SymbolSearch.parseArgs(cmdLine.getOptionValues("ss"), ctx.systemErrErrorReporter);
      if (parsed == null) return 1;
      org.arend.frontend.symbol.SymbolSearch.run(parsed.patterns(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return ctx.exitWithError ? 1 : 0;
    }


    if (cmdLine.hasOption("fu")) {
      org.arend.frontend.symbol.UsageSearch.Parsed parsed =
          org.arend.frontend.symbol.UsageSearch.parseArgs(cmdLine.getOptionValues("fu"));
      if (parsed == null) return 1;
      org.arend.frontend.symbol.UsageSearch.run(parsed.spec(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return ctx.exitWithError ? 1 : 0;
    }


    if (cmdLine.hasOption("ch")) {
      org.arend.frontend.symbol.ClassHierarchy.Parsed parsed =
          org.arend.frontend.symbol.ClassHierarchy.parseArgs(cmdLine.getOptionValues("ch"));
      if (parsed == null) return 1;
      org.arend.frontend.symbol.ClassHierarchy.run(parsed.spec(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return ctx.exitWithError ? 1 : 0;
    }

    if (cmdLine.hasOption("ps")) {
      boolean psOk = ProofSearch.run(ctx, cmdLine.getOptionValues("ps"));
      return (psOk && !ctx.exitWithError) ? 0 : 1;
    }

    boolean tcOk = TypecheckPipeline.run(ctx, cmdLine);
    return (tcOk && !ctx.exitWithError) ? 0 : 1;
  }
}
