package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.cli.commands.AiGuide;
import org.arend.frontend.cli.commands.ProofSearch;
import org.arend.frontend.cli.commands.TypecheckPipeline;

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

  public static int execute(CommandContext ctx, CommandLine cmdLine) {
    if (cmdLine.hasOption("ss")) {
      org.arend.frontend.symbol.SymbolSearch.Parsed parsed =
          org.arend.frontend.symbol.SymbolSearch.parseArgs(cmdLine.getOptionValues("ss"), ctx.systemErrErrorReporter);
      if (parsed == null) return 1;
      org.arend.frontend.symbol.SymbolSearch.run(parsed.patterns(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return ctx.exitWithError ? 1 : 0;
    }

    if (cmdLine.hasOption("rx")) {
      java.util.Set<String> only = null;
      String[] rxArgs = cmdLine.getOptionValues("rx") == null ? new String[0] : cmdLine.getOptionValues("rx");
      for (String arg : rxArgs) {
        if (arg.startsWith("only=")) {
          if (only == null) only = new java.util.HashSet<>();
          for (String s : arg.substring("only=".length()).split(",")) {
            if (!s.isEmpty()) only.add(s.trim());
          }
        } else {
          System.err.println("[ERROR] Unknown -rx token: " + arg);
          return 1;
        }
      }
      org.arend.frontend.symbol.SymbolSearch.reindex(ctx.requestedLibraries, ctx.libraryManager, ctx.server, only);
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

    if (cmdLine.hasOption("sc")) {
      org.arend.frontend.symbol.ReferableScope.Parsed parsed =
          org.arend.frontend.symbol.ReferableScope.parseArgs(cmdLine.getOptionValues("sc"));
      if (parsed == null) return 1;
      org.arend.frontend.symbol.ReferableScope.run(parsed.spec(), parsed.pattern(), parsed.options(),
          ctx.requestedLibraries, ctx.libraryManager, ctx.server, ctx.systemErrErrorReporter);
      return ctx.exitWithError ? 1 : 0;
    }

    if (cmdLine.hasOption("ps")) {
      boolean psOk = ProofSearch.run(ctx, cmdLine.getOptionValues("ps"));
      return (psOk && !ctx.exitWithError) ? 0 : 1;
    }

    if (cmdLine.hasOption("ag")) {
      // `-ag` with no value reads the library root README.
      String module = cmdLine.getOptionValue("ag", "");
      boolean agOk = AiGuide.run(ctx, module);
      return (agOk && !ctx.exitWithError) ? 0 : 1;
    }

    boolean tcOk = TypecheckPipeline.run(ctx, cmdLine);
    return (tcOk && !ctx.exitWithError) ? 0 : 1;
  }
}
