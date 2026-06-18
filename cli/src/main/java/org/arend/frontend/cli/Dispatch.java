package org.arend.frontend.cli;

import org.apache.commons.cli.CommandLine;
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
    boolean tcOk = TypecheckPipeline.run(ctx, cmdLine);
    return (tcOk && !ctx.exitWithError) ? 0 : 1;
  }
}
