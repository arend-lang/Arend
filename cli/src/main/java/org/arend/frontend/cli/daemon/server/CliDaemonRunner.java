package org.arend.frontend.cli.daemon.server;

import org.arend.cli.LockedFlags;
import org.arend.daemon.server.DaemonServer;
import org.arend.daemon.server.SocketBinder;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;

import java.util.Map;

/**
 * How the CLI's daemon runs a request: against the warm {@link CommandContext} it was started
 * with, with the worker's {@code System.out} / {@code System.err} streaming to the client as frames.
 * A client's command goes through {@link CliDispatcher}; a {@code refresh} re-runs the daemon's own
 * bootstrap argv.
 */
public final class CliDaemonRunner {
  /** How a client's command is run. Visible so a test can substitute one it can hold open. */
  @FunctionalInterface
  public interface CommandRunner {
    int run(CommandContext ctx, String[] args, String clientCwd);
  }

  private CliDaemonRunner() {}

  /**
   * The daemon serving {@code ctx}, which holds the library {@code libraryConfig}; {@code onShutdown}
   * is called once a client's {@code shutdown} has let the queue run out.
   */
  public static DaemonServer server(SocketBinder.Bound bound, CommandContext ctx, String libraryConfig,
                                    CommandRunner commands, Runnable onShutdown) {
    // Without this the worker's println calls go to the daemon's own stdout and the client sees
    // nothing. Idempotent, and installing it here is what keeps "started but mute" unreachable.
    StreamRedirector.install();
    Map<String, Object> lockedFlags = ctx.bootstrapArgs == null ? Map.of()
        : LockedFlags.extract(ConsoleMain.parseArgs(ctx.bootstrapArgs).cmdLine());
    return new DaemonServer(bound, libraryConfig, lockedFlags, request -> run(ctx, commands, request), onShutdown);
  }

  private static int run(CommandContext ctx, CommandRunner commands, DaemonServer.Request request) {
    ctx.cancellation = request.cancel();
    StreamRedirector.attach(request.output());
    try {
      if (request.args() != null) return commands.run(ctx, request.args().toArray(new String[0]), request.cwd());
      // A refresh re-dispatches the daemon's own bootstrap argv against the warm context, so that
      // source-timestamp checks pick up edits. Not through CliDispatcher: these args are what the
      // locked flags are locked to, and it would warn that they are being ignored.
      return Dispatch.run(ctx, ctx.bootstrapArgs == null ? new String[0] : ctx.bootstrapArgs.clone(), request.cwd());
    } finally {
      // The progress counter leaves "\r[37/412] Typechecking Algebra.Ring" open with no newline;
      // without closing it the server's [CANCELLED] would reach the client welded to it.
      if (request.cancel().isCanceled()) ctx.finishProgressLine();
      StreamRedirector.detach();
      ctx.cancellation = UnstoppableCancellationIndicator.INSTANCE;
    }
  }
}
