package org.arend.frontend.cli.daemon.server;

import org.apache.commons.cli.CommandLine;
import org.arend.cli.ArendOptions;
import org.arend.cli.LockedFlags;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;

/**
 * The daemon's policy on top of {@link Dispatch#run(CommandContext, String[])}: what a client is
 * and is not allowed to ask for.
 *
 * <p>Running the command is not daemon-specific and is not done here — a client request is the
 * same argv against the same already-loaded context that any other caller would use, and the two
 * must not drift. What is specific is that some flags shaped the daemon when it started and
 * cannot be changed per request, and some make no sense inside one at all.
 */
public final class CliDispatcher {
  private CliDispatcher() {}

  /** @return the exit code the client should report. */
  public static int run(CommandContext ctx, String[] args, String clientCwd) {
    String helpRefusal = ArendOptions.helpRefusal(args);
    if (helpRefusal != null) {
      System.err.println(helpRefusal);
      return 1;
    }
    // Parsed once, here: parsing prints the help, the version or the error, which the client should see once.
    ConsoleMain.ParsedArgs parsed = ConsoleMain.parseArgs(args);
    CommandLine cmdLine = parsed.cmdLine();
    // A command line Dispatch will reject anyway is left to it, so that the verdict on argv is
    // reached in exactly one place.
    if (cmdLine != null) {
      String refusal = ArendOptions.daemonRefusal(cmdLine);
      if (refusal != null) {
        System.err.println(refusal);
        return 1;
      }
      LockedFlags.warnIgnored(cmdLine, "daemon bootstrap", System.err);
    }

    return Dispatch.run(ctx, parsed, clientCwd);
  }
}
