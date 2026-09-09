package org.arend.frontend.cli.daemon.server;

import org.apache.commons.cli.CommandLine;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.arend.frontend.cli.daemon.LockedFlags;

import java.nio.file.Path;
import java.nio.file.Paths;

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
    CommandLine cmdLine = ConsoleMain.parseArgs(args).cmdLine();
    // A command line Dispatch will reject anyway is left to it, so that the verdict on argv is
    // reached in exactly one place.
    if (cmdLine != null) {
      if (cmdLine.hasOption("i")) {
        System.err.println("[ERROR] -i (REPL) is not supported in daemon mode");
        return 1;
      }
      for (LockedFlags.Flag flag : LockedFlags.ALL) {
        if (cmdLine.hasOption(flag.name())) {
          System.err.println("[WARN] " + flag.display()
              + " passed to a daemon-served command is ignored "
              + "(daemon bootstrap is authoritative; use --no-daemon to override).");
        }
      }
    }

    return Dispatch.run(ctx, rebasePathOptions(args, clientCwd), clientCwd);
  }

  /**
   * Rewrites relative values of path-valued options to the client's directory. Only
   * {@code --log-file} needs it: the other path-valued options are all in {@link LockedFlags},
   * fixed at bootstrap, and the positionals are rebased in
   * {@code CliSetup.populateRequestedTargets}.
   */
  private static String[] rebasePathOptions(String[] args, String clientCwd) {
    if (clientCwd == null) return args;
    String[] out = args.clone();
    for (int i = 0; i < out.length; i++) {
      if ("--log-file".equals(out[i]) && i + 1 < out.length) {
        out[i + 1] = rebase(out[i + 1], clientCwd);
      } else if (out[i].startsWith("--log-file=")) {
        out[i] = "--log-file=" + rebase(out[i].substring("--log-file=".length()), clientCwd);
      }
    }
    return out;
  }

  private static String rebase(String value, String clientCwd) {
    if (value.isEmpty()) return value;
    Path path = Paths.get(value);
    return path.isAbsolute() ? value : Paths.get(clientCwd).resolve(path).toString();
  }
}
