package org.arend.frontend.cli.daemon.server;

import org.apache.commons.cli.CommandLine;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.ConsoleMain;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.Dispatch;
import org.arend.frontend.cli.daemon.LockedFlags;
import org.arend.util.FileUtils;

/**
 * Run a per-request CLI command on the daemon's warm {@link CommandContext}.
 *
 * <p>The daemon was bootstrapped once with {@code -L <libdir> <libRef> -ai}; the
 * resulting ArendServer + libraryManager + loaded libraries live on the ctx. Each
 * subsequent client request reuses that ctx — we only reset the per-command
 * bookkeeping and re-populate {@link CommandContext#requestedModules} from positional
 * args before handing off to the shared {@link Dispatch#execute}.
 *
 * <p>Flags we deliberately ignore because the daemon's state is fixed for its
 * lifetime: {@code -L}, {@code -s/-b/-e/-m} (synthetic library), {@code -r}
 * (recompile). Flags we refuse entirely because they don't make sense in this
 * context: {@code -i} (REPL), {@code -d / --daemon-*} (nested daemon control).
 */
public final class CliDispatcher {
  private CliDispatcher() {}

  public static int run(CommandContext ctx, String[] args) {
    return run(ctx, args, null);
  }

  /**
   * Variant called by the daemon worker that knows the client's request ID; passed
   * through so the per-invocation log filename is unique per request.
   */
  public static int run(CommandContext ctx, String[] args, String requestId) {
    CommandLine cmdLine = ConsoleMain.parseArgs(args);
    if (cmdLine == null) {
      // parseArgs already printed help/version/parse error to stdout/stderr.
      return 0;
    }
    if (requestId != null) ctx.requestId = requestId;

    if (cmdLine.hasOption("i")) {
      System.err.println("[ERROR] -i (REPL) is not supported in daemon mode");
      return 1;
    }
    if (cmdLine.hasOption("d")
        || cmdLine.hasOption("daemon-stop")
        || cmdLine.hasOption("daemon-ping")
        || cmdLine.hasOption("daemon-status")
        || cmdLine.hasOption("daemon-refresh")) {
      System.err.println("[ERROR] daemon control flags are not valid inside a daemon-served command");
      return 1;
    }

    for (String flag : LockedFlags.NAMES) {
      if (cmdLine.hasOption(flag)) {
        System.err.println("[WARN] " + LockedFlags.display(flag)
            + " passed to a daemon-served command is ignored "
            + "(daemon bootstrap is authoritative; use --no-daemon to override).");
      }
    }

    resetPerCommandState(ctx);
    populateRequestedModules(ctx, cmdLine);

    // Re-derive aiMode and reinstall the output router for THIS request. The bootstrap
    // router covered the warm-load pass; each client invocation now gets a fresh
    // InvocationLog so log files are per-invocation rather than append-only.
    ctx.aiMode = cmdLine.hasOption("ai");
    ctx.noQuiet = cmdLine.hasOption("no-quiet");
    CliSetup.installOutputRouter(ctx);

    return Dispatch.execute(ctx, cmdLine);
  }

  /** Clear bookkeeping that accumulated during the previous command. */
  private static void resetPerCommandState(CommandContext ctx) {
    ctx.exitWithError = false;
    ctx.moduleResults.clear();
    ctx.failedDefinitions.clear();
    ctx.bufferedErrors.clear();
    ctx.bufferErrors = false;
    ctx.requestedModules.clear();
  }

  /**
   * Translate positional args into {@code requestedModules}. Library references
   * (matching a loaded {@link CommandContext#requestedLibraries} name) are ignored —
   * the daemon already serves that library. {@code MODULE}, {@code MODULE:DEF} forms
   * populate the set; anything else is reported as an error via the systemErr reporter.
   */
  private static void populateRequestedModules(CommandContext ctx, CommandLine cmdLine) {
    java.util.Set<String> loadedLibNames = new java.util.HashSet<>();
    for (var lib : ctx.requestedLibraries) loadedLibNames.add(lib.getLibraryName());

    for (String pos : cmdLine.getArgList()) {
      if (loadedLibNames.contains(pos)) continue;
      int colonIdx = pos.indexOf(':');
      if (colonIdx >= 0) {
        Pair<ModulePath, LongName> parsed = ctx.parseFullName(pos);
        if (parsed != null && parsed.proj2 != null) {
          ctx.requestedModules.add(parsed);
        }
        continue;
      }
      ModulePath mp = ModulePath.fromString(pos);
      if (FileUtils.isCorrectModulePath(mp)) {
        ctx.requestedModules.add(new Pair<>(mp, null));
      }
      // else: silently ignore — could be a library ref the daemon doesn't recognise.
    }
  }
}
