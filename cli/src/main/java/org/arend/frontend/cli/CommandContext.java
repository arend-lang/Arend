package org.arend.frontend.cli;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.ext.util.Pair;
import org.arend.frontend.library.CliServerRequester;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.server.ArendServer;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.error.local.GoalError;
import org.arend.util.FileUtils;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-run state shared across CLI command handlers.
 *
 * <p>Mirrors what used to live as fields and local variables of {@code ConsoleMain.run()}.
 * Populated by {@link CliSetup} before any handler runs; handlers in
 * {@code org.arend.frontend.cli.commands.*} read and mutate it.
 *
 * <p>Field-level visibility is deliberately public to keep the M1 refactor a pure move.
 * Output is still hard-wired to {@code System.out}/{@code System.err}; redirection is
 * deferred to a later pass.
 */
public class CommandContext {
  // Server / library state — populated by CliSetup, then mostly read-only.
  public LibraryManager libraryManager;
  public CliServerRequester requester;
  public ArendServer server;
  public final List<SourceLibrary> requestedLibraries = new ArrayList<>();
  public final Set<Pair<ModulePath, LongName>> requestedModules = new LinkedHashSet<>();
  public final List<Path> libDirs = new ArrayList<>();
  public boolean doubleCheck;
  public boolean recompile;
  /**
   * If true, persist typechecked modules as .arc binary caches after typechecking.
   * On by default; disabled by {@code --no-serialize}.
   */
  public boolean serialize;

  /**
   * Threshold (ms) above which a per-definition typecheck triggers a stderr
   * {@code [WARN]} line. {@code 0} disables the check. Set by {@code --slow-warn};
   * defaults to {@code 5000}.
   */
  public long slowWarnMs = 5000;

  /**
   * Identifier for the current invocation. The in-process path leaves this as
   * {@code "local"}; the daemon worker sets it to the client request ID.
   */
  public String requestId = "local";

  /**
   * Original argv used to bootstrap the daemon (set only by {@code runDaemonBootstrap};
   * null for in-process runs). The daemon's {@code refresh} op re-dispatches these exact
   * args against the warm context so source-timestamp checks pick up edits.
   */
  public String[] bootstrapArgs;

  /**
   * Cancellation indicator threaded through long-running CLI handlers (typecheck,
   * proof search). In-process runs leave this as the unstoppable default; the daemon
   * worker swaps in a per-task indicator wrapping its {@code currentTaskCancel} flag,
   * so a client-side {@code cancel} op trips {@code ComputationRunner.checkCanceled()}
   * inside the typechecker.
   */
  public CancellationIndicator cancellation = UnstoppableCancellationIndicator.INSTANCE;

  // Per-run mutable bookkeeping consulted across phases.
  public boolean exitWithError;
  public final Map<ModuleLocation, GeneralError.Level> moduleResults = new LinkedHashMap<>();
  public boolean bufferErrors;
  public final List<GeneralError> bufferedErrors = new ArrayList<>();

  /**
   * Diagnostics already printed during this invocation. A warm daemon re-emits stored
   * resolver/typechecking errors from the server so that every run reports them, and the
   * same object may also arrive live from the pass that produced it; this set makes the
   * two paths idempotent. Identity-based: {@link GeneralError} has no {@code equals}, and
   * two structurally equal diagnostics from different definitions must both be shown.
   */
  private final Set<GeneralError> emittedErrors = Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * Plain stderr reporter; flips {@link #exitWithError} on an ERROR, like {@link #dispatchError}.
   *
   * <p>It used to fail the run on <em>anything</em> reported through it, which is how a warning
   * about a discarded binary cache — a condition the very same run then repairs — could exit 1 on
   * a library that typechecks. Callers that must fail on a non-ERROR diagnostic (a library that
   * would not load, say) set the flag themselves.
   */
  public final ErrorReporter systemErrErrorReporter = error -> {
    System.err.println(error);
    System.err.flush();
    if (error.level == GeneralError.Level.ERROR) exitWithError = true;
  };

  /** Goal-aware reporter that buffers when {@link #bufferErrors} is set. */
  public final ErrorReporter errorReporter = new ErrorReporter() {
    @Override
    public void report(GeneralError error) {
      error.forAffectedDefinitions((referable, err) -> {
        if (referable instanceof LocatedReferable) {
          updateSourceResult(((LocatedReferable) referable).getLocation(), err.level);
        }
      });

      if (bufferErrors) {
        bufferedErrors.add(error);
        return;
      }

      dispatchError(error);
    }
  };

  /** Drop the "already printed" marks; called when a new command starts on a warm context. */
  public void clearEmittedErrors() {
    emittedErrors.clear();
  }

  public void updateSourceResult(ModuleLocation module, GeneralError.Level result) {
    if (module == null) return;
    GeneralError.Level prevResult = moduleResults.get(module);
    if (prevResult == null || result.ordinal() > prevResult.ordinal()) {
      moduleResults.put(module, result);
    }
  }

  /** Print one buffered/late-emitted error using the same renderer as {@link #errorReporter}. */
  public void printError(GeneralError error) {
    dispatchError(error);
  }

  /** Render one diagnostic to stdout/stderr, goal-aware, at most once per invocation. */
  private void dispatchError(GeneralError error) {
    if (error.level == GeneralError.Level.ERROR) exitWithError = true;
    if (!emittedErrors.add(error)) return;
    PrettyPrinterConfigWithRenamer ppConfig = new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE);
    if (error instanceof GoalError) {
      ppConfig.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
    }
    String errorText = error.getDoc(ppConfig).toString();
    if (error.isSevere()) {
      System.err.println(errorText);
      System.err.flush();
    } else {
      System.out.println(errorText);
      System.out.flush();
    }
  }

  /** Parse a "{@code Module.Path[:Def.Long.Name]}" string. Reports illegality via {@link #systemErrErrorReporter}. */
  public Pair<ModulePath, LongName> parseFullName(String fullName) {
    ModulePath modulePath;
    LongName longName = null;
    int index = fullName.indexOf(':');
    if (index >= 0) {
      longName = LongName.fromString(fullName.substring(index + 1));
      if (!FileUtils.isCorrectDefinitionName(longName)) {
        systemErrErrorReporter.report(FileUtils.illegalDefinitionName(longName.toString()));
        return null;
      }
      fullName = fullName.substring(0, index);
    }
    modulePath = ModulePath.fromString(fullName);
    if (!FileUtils.isCorrectModulePath(modulePath)) {
      systemErrErrorReporter.report(FileUtils.illegalModuleName(modulePath.toString()));
      return null;
    }
    return new Pair<>(modulePath, longName);
  }
}
