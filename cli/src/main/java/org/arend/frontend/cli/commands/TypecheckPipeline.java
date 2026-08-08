package org.arend.frontend.cli.commands;

import org.apache.commons.cli.CommandLine;
import org.arend.core.definition.Definition;
import org.arend.core.expr.visitor.SizeExpressionVisitor;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.SlowDefinitionWarningReporter;
import org.arend.frontend.TimedProgressReporter;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.cli.ai.AiOutputRouter;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.ReferenceResolveSuggest;
import org.arend.frontend.query.SignatureFileWriter;
import org.arend.frontend.query.SymbolIndex;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.DefinitionData;
import org.arend.server.impl.ErrorService;
import org.arend.source.PersistableBinarySource;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;
import org.arend.typechecking.order.MapTarjanSCC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.arend.ext.prettyprinting.PrettyPrinterConfig.DEFAULT;

/**
 * Default typecheck pipeline plus its modifiers: {@code -ai} pre/post phases,
 * binary-cache preload, the typecheck loop, {@code -p print}, {@code -t tests},
 * {@code --show-*} footers, and library persistence.
 *
 * <p>Moved verbatim out of {@code ConsoleMain.run()}.
 */
public final class TypecheckPipeline {
  public static final String SHOW_TIMES = "show-times";
  public static final String SHOW_SIZES = "show-sizes";
  public static final String SHOW_MODULES = "show-modules";
  public static final String SHOW_MODULES_WITH_INSTANCES = "show-modules-with-instances";

  private TypecheckPipeline() {}

  /**
   * Run the default pipeline. Returns false on a fatal error during typecheck.
   * {@link CommandContext#exitWithError} may also be set for non-fatal errors.
   */
  public static boolean run(CommandContext ctx, CommandLine cmdLine) {
    boolean aiMode = cmdLine.hasOption("ai");

    // -p MODULE[:DEF] is consumed at the very end of this method (printDefinitions). Make
    // sure its target module is in the typecheck scope, otherwise the printed core would
    // be missing (silent no-op for whole-module targets; misleading "not found" for
    // single-def targets). Auto-extend even when the user gave their own positional scope.
    if (cmdLine.hasOption("p")) {
      Pair<ModulePath, LongName> parsed = ctx.parseFullName(cmdLine.getOptionValue("p"));
      if (parsed != null) {
        ModulePath pTarget = parsed.proj1;
        boolean alreadyInScope = false;
        for (Pair<ModulePath, LongName> r : ctx.requestedModules) {
          if (r.proj1.equals(pTarget)) { alreadyInScope = true; break; }
        }
        if (!alreadyInScope) {
          if (!ctx.requestedModules.isEmpty()) {
            System.err.println("[INFO] -p target " + cmdLine.getOptionValue("p")
                + " is outside the typecheck scope; auto-adding " + pTarget + " to the scope");
          }
          ctx.requestedModules.add(new Pair<>(pTarget, null));
        }
      }
    }

    TimedProgressReporter timedProgressReporter = cmdLine.hasOption(SHOW_TIMES) ? new TimedProgressReporter() : null;
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter =
        timedProgressReporter != null ? timedProgressReporter : ProgressReporter.empty();
    if (ctx.slowWarnMs > 0) {
      progressReporter = new SlowDefinitionWarningReporter(progressReporter, ctx.slowWarnMs, System.err);
    }

    if (aiMode) {
      runAiNameResolve(ctx);
    }
    ctx.cancellation.checkCanceled();

    Set<String> requestedLibraryNames = requestedLibraryNames(ctx.requestedLibraries);
    Map<String, Set<ModulePath>> liveModules = liveSourceModules(ctx.requestedLibraries);
    // Diagnostics raised while resolving, held back so they land after the
    // "--- Typechecking ... ---" banner instead of before it. Otherwise a per-module
    // filter such as `arend M | grep -A20 'Typechecking M'` — the natural thing to write
    // while iterating on one module — never shows a resolution error.
    List<GeneralError> deferredErrors = new ArrayList<>();

    // Pre-load binary caches (unless --recompile is set)
    if (!ctx.recompile) {
      // Typecheck Prelude first — binary cache loading needs Prelude definitions to be available
      ctx.server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
      boolean resolvedRequestedScope = false;
      if (ctx.requestedModules.isEmpty()) {
        // Whole-library typechecking: resolve every module of each requested library.
        for (SourceLibrary library : ctx.requestedLibraries) {
          List<ModuleLocation> allModules = liveModules.get(library.getLibraryName()).stream()
              .map(mp -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
              .toList();
          if (!allModules.isEmpty()) {
            resolveDeferringErrors(ctx, allModules, deferredErrors);
            resolvedRequestedScope = true;
          }
        }
      } else {
        // Targeted typechecking: seed resolveAll with just the requested modules so only
        // their transitive import cone is raw-loaded.
        List<ModuleLocation> targets = new ArrayList<>();
        for (Pair<ModulePath, LongName> requested : ctx.requestedModules) {
          ModuleLocation module = ctx.server.findModule(requested.proj1, null, true, false);
          if (module != null) targets.add(module);
        }
        if (!targets.isEmpty()) {
          resolveDeferringErrors(ctx, targets, deferredErrors);
          resolvedRequestedScope = true;
        }
      }
      if (resolvedRequestedScope) {
        for (SourceLibrary library : dependencyFirstLibraries(ctx.libraryManager, ctx.requestedLibraries)) {
          ctx.requester.loadBinaryCache(library, ctx.server);
          if (!requestedLibraryNames.contains(library.getLibraryName())) {
            typecheckUncachedDependencyModules(ctx, library);
          }
        }
      }
    }

    // The targeted branch below prints one banner per requested module but replays the
    // whole scope's stored diagnostics; this keeps it to the first banner.
    boolean storedReplayed = false;

    if (ctx.requestedModules.isEmpty()) {
      for (SourceLibrary library : ctx.requestedLibraries) {
        ctx.cancellation.checkCanceled();
        ctx.outputRouter.stage("");
        ctx.outputRouter.stage("--- Typechecking " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();
        storedReplayed = true;
        replayStoredDiagnostics(ctx, Collections.singleton(library.getLibraryName()), liveModules);
        flushDeferredErrors(ctx, deferredErrors);

        for (ModulePath modulePath : liveModules.get(library.getLibraryName())) {
          ctx.cancellation.checkCanceled();
          ctx.server.getCheckerFor(Collections.singletonList(
                  new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath)))
              .typecheck(ctx.cancellation, progressReporter);
        }

        time = System.currentTimeMillis() - time;

        int numWithErrors = 0;
        int numWithGoals = 0;
        for (ModuleLocation module : ctx.server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
              && module.getLibraryName().equals(library.getLibraryName())
              && isLive(liveModules, module)) {
            GeneralError.Level result = ctx.moduleResults.get(module);
            reportTypeCheckResult(ctx.outputRouter, module, result);
            if (result == GeneralError.Level.ERROR) numWithErrors++;
            if (result == GeneralError.Level.GOAL) numWithGoals++;
          }
        }

        if (numWithErrors > 0) {
          ctx.exitWithError = true;
          ctx.outputRouter.info("Number of modules with errors: " + numWithErrors);
        }
        if (numWithGoals > 0) {
          ctx.outputRouter.info("Number of modules with goals: " + numWithGoals);
        }
        ctx.outputRouter.stage("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

        if (cmdLine.hasOption(SHOW_SIZES)) {
          showSizes(ctx.server, library);
        }

        if (cmdLine.hasOption(SHOW_MODULES)) {
          System.out.println();
          System.out.println("Modules cycles:");
          showModules(ctx.server, library, true);
        }

        if (cmdLine.hasOption(SHOW_MODULES_WITH_INSTANCES)) {
          System.out.println();
          System.out.println("Modules with instances cycles:");
          showModules(ctx.server, library, false);
        }

        if (ctx.doubleCheck && numWithErrors == 0) {
          ctx.outputRouter.stage("");
          ctx.outputRouter.stage("--- Checking " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(ctx.errorReporter);
            for (ModuleLocation module : ctx.server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
                  && module.getLibraryName().equals(library.getLibraryName())
                  && isLive(liveModules, module)) {
                ConcreteGroup group = ctx.server.getRawGroup(module);
                if (group != null) {
                  checker.checkGroup(group);
                }
              }
            }
          } finally {
            time = System.currentTimeMillis() - time;
            ctx.outputRouter.stage("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }

        if (ctx.serialize) persistLibrary(ctx, library, liveModules);
      }
    } else {
      for (Pair<ModulePath, LongName> requested : ctx.requestedModules) {
        ModulePath modulePath = requested.proj1;
        LongName definitionName = requested.proj2;
        ModuleLocation module = ctx.server.findModule(modulePath, null, true, false);
        if (module == null) {
          ctx.systemErrErrorReporter.report(new ModuleNotFoundError(modulePath));
        } else if (definitionName != null) {
          FullName fullName = new FullName(module, definitionName);
          // Validate the def exists in the module before kicking off typecheck.
          // Without this, a typo'd name silently typechecks zero definitions and
          // the AI summariser prints "OK for target" — same shape -fu / -ch / -sc
          // catch up front.
          boolean defFound = false;
          for (DefinitionData d : ctx.server.getResolvedDefinitions(module)) {
            if (d.definition().getData().getRefLongName().equals(definitionName)) {
              defFound = true;
              break;
            }
          }
          if (!defFound) {
            ctx.systemErrErrorReporter.report(new DefinitionNotFoundError(fullName));
            ctx.exitWithError = true;
            continue;
          }
          ctx.outputRouter.stage("");
          ctx.outputRouter.stage("--- Typechecking " + fullName + " ---");
          long time = System.currentTimeMillis();
          if (!storedReplayed) {
            storedReplayed = true;
            replayStoredDiagnostics(ctx, requestedLibraryNames, liveModules);
          }
          flushDeferredErrors(ctx, deferredErrors);

          ctx.server.getCheckerFor(Collections.singletonList(module))
              .typecheck(Collections.singletonList(fullName), ctx.errorReporter,
                  ctx.cancellation, progressReporter);

          ctx.outputRouter.stage("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
        } else {
          ctx.outputRouter.stage("");
          ctx.outputRouter.stage("--- Typechecking " + module + " ---");
          long time = System.currentTimeMillis();
          if (!storedReplayed) {
            storedReplayed = true;
            replayStoredDiagnostics(ctx, requestedLibraryNames, liveModules);
          }
          flushDeferredErrors(ctx, deferredErrors);

          ctx.server.getCheckerFor(Collections.singletonList(module))
              .typecheck(ctx.cancellation, progressReporter);

          ctx.outputRouter.stage("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");

          if (ctx.doubleCheck) {
            ctx.outputRouter.stage("");
            ctx.outputRouter.stage("--- Checking " + module + " ---");
            time = System.currentTimeMillis();

            try {
              CoreModuleChecker checker = new CoreModuleChecker(ctx.errorReporter);
              ConcreteGroup group = ctx.server.getRawGroup(module);
              if (group != null) {
                checker.checkGroup(group);
              }
            } finally {
              time = System.currentTimeMillis() - time;
              ctx.outputRouter.stage("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
            }
          }
        }
      }
      for (SourceLibrary library : ctx.requestedLibraries) {
        if (ctx.serialize) persistLibrary(ctx, library, liveModules);
      }
    }
    // No banner was printed at all (no libraries, or every requested module was
    // unresolvable): the stored diagnostics still have to reach the user.
    if (!storedReplayed) {
      replayStoredDiagnostics(ctx, requestedLibraryNames, liveModules);
    }
    flushDeferredErrors(ctx, deferredErrors);

    if (aiMode) {
      ctx.cancellation.checkCanceled();
      finalizeAi(ctx);
    }

    printDefinitions(ctx.server, cmdLine.getOptionValue("p"), ctx);

    if (cmdLine.hasOption("t")) {
      for (SourceLibrary library : ctx.requestedLibraries) {
        ctx.outputRouter.stage("");
        ctx.outputRouter.stage("--- Running tests in " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        for (ModulePath modulePath : library.findModules(true)) {
          ctx.server.getCheckerFor(Collections.singletonList(
                  new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.TEST, modulePath)))
              .typecheck(ctx.cancellation, progressReporter);
        }

        time = System.currentTimeMillis() - time;

        int[] total = new int[1];
        int[] failed = new int[1];
        for (ModuleLocation module : ctx.server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.TEST
              && module.getLibraryName().equals(library.getLibraryName())) {
            for (ConcreteStatement statement : Objects.requireNonNull(ctx.server.getRawGroup(module)).statements()) {
              if (statement.group() != null && statement.group().referable() instanceof TCDefReferable referable) {
                Definition definition = referable.getTypechecked();
                if (definition != null || referable.getKind().isTypecheckable()) {
                  total[0]++;
                  if (definition == null || definition.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
                    failed[0]++;
                  }
                }
              }
            }
          }
        }

        ctx.outputRouter.info("Tests completed: " + total[0] + ", Failed: " + failed[0]);
        ctx.outputRouter.stage("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

        if (ctx.doubleCheck) {
          ctx.outputRouter.stage("");
          ctx.outputRouter.stage("--- Checking tests in " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(ctx.errorReporter);
            for (ModuleLocation module : ctx.server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.TEST
                  && module.getLibraryName().equals(library.getLibraryName())) {
                ConcreteGroup group = ctx.server.getRawGroup(module);
                if (group != null) {
                  checker.checkGroup(group);
                }
              }
            }
          } finally {
            time = System.currentTimeMillis() - time;
            ctx.outputRouter.stage("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }
      }
    }

    if (timedProgressReporter != null) {
      timedProgressReporter.print();
    }

    if (aiMode && ctx.outputRouter != null) {
      ctx.outputRouter.summary();
    }

    return true;
  }

  private static void typecheckUncachedDependencyModules(CommandContext ctx, SourceLibrary library) {
    List<ModuleLocation> modules = new ArrayList<>();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
          && module.getLibraryName().equals(library.getLibraryName())
          && !ctx.requester.getBinaryCacheLoaded().contains(module)) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) return;

    boolean oldBufferErrors = ctx.bufferErrors;
    int oldBufferedSize = ctx.bufferedErrors.size();
    ctx.bufferErrors = true;
    try {
      ctx.server.getCheckerFor(modules).typecheck(ctx.cancellation, ProgressReporter.empty());
    } finally {
      ctx.bufferErrors = oldBufferErrors;
      if (ctx.bufferedErrors.size() > oldBufferedSize) {
        ctx.bufferedErrors.subList(oldBufferedSize, ctx.bufferedErrors.size()).clear();
      }
    }
  }

  /**
   * Resolve {@code modules}, holding back whatever is reported so the caller can print it
   * after the {@code "--- Typechecking ... ---"} banner. Everything is preserved: the
   * name-resolution errors are additionally recorded in the server's {@code ErrorService},
   * so {@link #replayStoredDiagnostics} prints those and {@link #flushDeferredErrors} then
   * prints whatever else showed up (parse errors, missing imports, ...) — the per-run
   * de-duplication in {@link CommandContext} keeps the overlap to one line each.
   */
  private static void resolveDeferringErrors(CommandContext ctx, List<ModuleLocation> modules, List<GeneralError> deferred) {
    boolean oldBufferErrors = ctx.bufferErrors;
    int mark = ctx.bufferedErrors.size();
    ctx.bufferErrors = true;
    try {
      ctx.server.getCheckerFor(modules).resolveAll(ctx.cancellation, ProgressReporter.empty());
    } finally {
      ctx.bufferErrors = oldBufferErrors;
      List<GeneralError> collected = ctx.bufferedErrors.subList(mark, ctx.bufferedErrors.size());
      deferred.addAll(collected);
      collected.clear();
    }
  }

  /** Print (once) the diagnostics {@link #resolveDeferringErrors} held back. */
  private static void flushDeferredErrors(CommandContext ctx, List<GeneralError> deferred) {
    if (deferred.isEmpty()) return;
    for (GeneralError error : deferred) ctx.printError(error);
    deferred.clear();
  }

  /**
   * The source modules each requested library currently has on disk, keyed by library name
   * and in {@code findModules} order.
   *
   * <p>A warm daemon keeps a module in the server after its source file is deleted — nothing
   * rescans the library between runs — so without this filter every later run keeps listing
   * the module, replaying its stored diagnostics and trying to persist it.
   */
  private static Map<String, Set<ModulePath>> liveSourceModules(List<SourceLibrary> libraries) {
    Map<String, Set<ModulePath>> result = new HashMap<>();
    for (SourceLibrary library : libraries) {
      result.put(library.getLibraryName(), new LinkedHashSet<>(library.findModules(false)));
    }
    return result;
  }

  /** False only for a source module of a requested library whose file is gone. */
  private static boolean isLive(Map<String, Set<ModulePath>> liveModules, ModuleLocation module) {
    if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) return true;
    Set<ModulePath> live = liveModules.get(module.getLibraryName());
    return live == null || live.contains(module.getModulePath());
  }

  private static Set<String> requestedLibraryNames(List<SourceLibrary> requestedLibraries) {
    Set<String> result = new HashSet<>();
    for (SourceLibrary library : requestedLibraries) {
      result.add(library.getLibraryName());
    }
    return result;
  }

  private static List<SourceLibrary> dependencyFirstLibraries(LibraryManager libraryManager, List<SourceLibrary> requestedLibraries) {
    List<SourceLibrary> result = new ArrayList<>();
    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();
    for (SourceLibrary library : requestedLibraries) {
      collectDependencyFirst(libraryManager, library, visiting, visited, result);
    }
    return result;
  }

  private static void collectDependencyFirst(LibraryManager libraryManager, SourceLibrary library, Set<String> visiting, Set<String> visited, List<SourceLibrary> result) {
    String libraryName = library.getLibraryName();
    if (visited.contains(libraryName) || !visiting.add(libraryName)) return;

    for (String dependencyName : library.getLibraryDependencies()) {
      SourceLibrary dependency = libraryManager.getLibrary(dependencyName);
      if (dependency != null) {
        collectDependencyFirst(libraryManager, dependency, visiting, visited, result);
      }
    }

    visiting.remove(libraryName);
    visited.add(libraryName);
    result.add(library);
  }

  // ───────── AI pipeline phases ─────────

  /**
   * Step 1 of the -ai pipeline: resolve every module in scope, buffer the resulting name-
   * resolution errors, hand them to {@link ReferenceResolveSuggest}, and print suggestions.
   * Never rewrites sources.
   */
  private static void runAiNameResolve(CommandContext ctx) {
    ctx.outputRouter.stage("");
    ctx.outputRouter.stage("--- AI: resolve + suggest ---");
    long t = System.currentTimeMillis();
    ctx.bufferErrors = true;
    ctx.bufferedErrors.clear();
    try {
      if (ctx.requestedModules.isEmpty()) {
        for (SourceLibrary lib : ctx.requestedLibraries) {
          ctx.cancellation.checkCanceled();
          List<ModuleLocation> mods = lib.findModules(false).stream()
              .map(mp -> new ModuleLocation(lib.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
              .toList();
          if (!mods.isEmpty()) {
            ctx.server.getCheckerFor(mods).resolveAll(ctx.cancellation, ProgressReporter.empty());
            ctx.cancellation.checkCanceled();
          }
        }
      } else {
        for (Pair<ModulePath, LongName> requested : ctx.requestedModules) {
          ctx.cancellation.checkCanceled();
          ModuleLocation module = ctx.server.findModule(requested.proj1, null, true, false);
          if (module == null) {
            ctx.systemErrErrorReporter.report(new ModuleNotFoundError(requested.proj1));
            continue;
          }
          ctx.server.getCheckerFor(Collections.singletonList(module))
              .resolveAll(ctx.cancellation, ProgressReporter.empty());
          ctx.cancellation.checkCanceled();
        }
      }
    } finally {
      ctx.bufferErrors = false;
    }

    ReferenceResolveSuggest.Result result =
        ReferenceResolveSuggest.process(ctx.server, ctx.libraryManager, ctx.bufferedErrors, ctx.requestedLibraries);

    for (GeneralError error : result.errorsToPrint()) ctx.printError(error);
    for (ReferenceResolveSuggest.SuggestionBlock block : result.suggestionBlocks()) {
      ctx.outputRouter.resolveSuggestion(block.text(), block.affectedModule());
    }
    // Constructor-import advisories: useful but verbose; route to log only. The agent
    // gets the actionable signal (Candidates blocks above) on stdout.
    for (String warning : result.warnings()) ctx.outputRouter.info(warning);

    // Name-resolution errors recorded against failedDefinitions are reset so the typecheck
    // phase tracks only real failures; unresolved refs will resurface as typecheck errors.
    ctx.failedDefinitions.clear();
    ctx.bufferedErrors.clear();
    ctx.outputRouter.stage("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - t) + ") ---");
  }

  /**
   * Steps 3-4 of the -ai pipeline: write the .sig mirror for verified definitions only
   * (per-def filter via {@link CommandContext#failedDefinitions}) and refresh the binary
   * symbol index used by -ss / -fu / -ch / -sc.
   */
  private static void finalizeAi(CommandContext ctx) {
    Set<ModulePath> only = ctx.requestedModules.isEmpty() ? null : collectModulePaths(ctx.requestedModules);
    ctx.outputRouter.stage("");
    ctx.outputRouter.stage("--- AI: .sig + reindex ---");
    for (SourceLibrary library : ctx.requestedLibraries) {
      SignatureFileWriter.Result r =
          SignatureFileWriter.writeFiltered(ctx.server, library, only, ctx.failedDefinitions);
      for (String err : r.errors()) System.err.println(err);
      StringBuilder line = new StringBuilder("[INFO] .sig: wrote ")
          .append(r.written()).append(" file(s) for ").append(library.getLibraryName());
      if (r.skippedDefinitions() > 0) {
        line.append("; skipped ").append(r.skippedDefinitions())
            .append(" def").append(r.skippedDefinitions() == 1 ? "" : "s").append(" with errors");
      }
      if (r.skipped() > 0) {
        line.append("; skipped ").append(r.skipped())
            .append(" module").append(r.skipped() == 1 ? "" : "s").append(" (out of scope)");
      }
      ctx.outputRouter.info(line.toString());
    }
    reindexSymbols(ctx);
  }

  /**
   * Refreshes the on-disk symbol index of every loaded library (dependencies included, since
   * {@code -ss}/{@code -fu}/{@code -ch}/{@code -sc} search them too) and reports per library
   * how many modules had to be re-parsed. Unchanged modules are served from the cache, so a
   * no-op run costs a stat per source file.
   */
  private static void reindexSymbols(CommandContext ctx) {
    List<SourceLibrary> libraries = new ArrayList<>();
    for (String name : ctx.libraryManager.getLibraries()) {
      SourceLibrary library = ctx.libraryManager.getLibrary(name);
      if (library != null) libraries.add(library);
    }
    if (libraries.isEmpty()) {
      ctx.outputRouter.info("No libraries to index.");
      return;
    }
    for (SourceLibrary library : libraries) {
      SymbolIndex.refreshLibrary(library, ctx.server, false, reparsed ->
          ctx.outputRouter.info(library.getLibraryName() + ": indexed (" + reparsed
              + " stale module" + (reparsed == 1 ? "" : "s") + " re-parsed)."));
    }
  }

  private static Set<ModulePath> collectModulePaths(Set<Pair<ModulePath, LongName>> requestedModules) {
    Set<ModulePath> result = new HashSet<>();
    for (Pair<ModulePath, LongName> p : requestedModules) result.add(p.proj1);
    return result;
  }

  // ───────── reporting helpers ─────────

  /**
   * Re-emit every diagnostic that is already known to the server but that this run's
   * typechecking will not produce again:
   *
   * <ul>
   *   <li>goals ({@code {?}}) of modules restored from a binary cache — the {@code isGoal}
   *       flag survives in the {@code .arc}, so they are detectable without re-typechecking;</li>
   *   <li>name-resolution errors, which {@code ErrorService.setResolverErrors} pushes to the
   *       reporters exactly once, when the module is (re-)resolved;</li>
   *   <li>typechecking errors of modules that are not re-typechecked.</li>
   * </ul>
   *
   * <p>Only a warm server ever has any of these — a cold process resolves and typechecks
   * everything itself. Without this step the daemon called a broken library clean and exited
   * 0 from the second run on (arend-lang/Arend#138): persist skips a module that has errors
   * and the cache load therefore never sees it, so nothing re-emitted its diagnostics and
   * neither {@code moduleResults} nor the exit code knew about them.
   */
  private static void replayStoredDiagnostics(CommandContext ctx, Set<String> libraryNames, Map<String, Set<ModulePath>> liveModules) {
    for (ModuleLocation module : ctx.requester.getBinaryCacheLoaded()) {
      if (!libraryNames.contains(module.getLibraryName()) || !isLive(liveModules, module)) continue;
      ConcreteGroup group = ctx.server.getRawGroup(module);
      if (group == null) continue;
      reportGoalsInGroup(ctx, group, module);
    }

    if (!(ctx.server instanceof ArendServerImpl impl)) return;
    ErrorService errorService = impl.getErrorService();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE
          || !libraryNames.contains(module.getLibraryName())
          || !isLive(liveModules, module)) continue;
      for (GeneralError error : errorService.getResolverErrors(module)) {
        ctx.errorReporter.report(error);
      }
      for (GeneralError error : errorService.getTypecheckingErrors(module)) {
        ctx.errorReporter.report(error);
      }
    }
  }

  private static void reportGoalsInGroup(CommandContext ctx, ConcreteGroup group, ModuleLocation module) {
    if (group.referable() instanceof TCDefReferable tcRef) {
      Definition def = tcRef.getTypechecked();
      if (def != null && def.getGoals().contains(def)) {
        final var ref = group.referable();
        GeneralError goalError = new GeneralError(GeneralError.Level.GOAL, "Goal") {
          @Override
          public Object getCause() {
            return ref;
          }
        };
        ctx.errorReporter.report(goalError);
      }
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null) {
        reportGoalsInGroup(ctx, statement.group(), module);
      }
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      reportGoalsInGroup(ctx, dynGroup, module);
    }
  }

  private static void persistLibrary(CommandContext ctx, SourceLibrary library, Map<String, Set<ModulePath>> liveModules) {
    if (!library.supportsPersisting()) return;
    int persisted = 0;
    int skipped = 0;
    int failed = 0;
    int skippedWithErrors = 0;
    Set<ModuleLocation> skipModules = ctx.requester.getBinaryCacheLoaded();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
          && module.getLibraryName().equals(library.getLibraryName())
          && isLive(liveModules, module)) {
        if (skipModules.contains(module)) {
          skipped++;
          continue;
        }
        // Skip modules whose typechecked state contains any HAS_ERRORS def.  Persisting
        // them would write a cache that the next load can't use (the deserialized
        // module would still need re-typechecking from source) and, in a long-lived
        // daemon, accumulates orphan FunctionDefinitions pinned by cached expression
        // trees across the deserialize → clear → re-typecheck cycle.
        ConcreteGroup group = ctx.server.getRawGroup(module);
        if (group != null && groupHasTypecheckingErrors(group)) {
          skippedWithErrors++;
          continue;
        }
        PersistableBinarySource binarySource = library.getBinarySource(module.getModulePath());
        if (binarySource != null) {
          if (binarySource.persist(ctx.server, ctx.systemErrErrorReporter)) {
            persisted++;
          } else {
            failed++;
          }
        }
      }
    }
    if (persisted > 0 || failed > 0 || skippedWithErrors > 0) {
      String line = "[INFO] Persisted " + persisted + " module(s)"
          + (failed > 0 ? ", " + failed + " failed" : "")
          + (skippedWithErrors > 0 ? ", " + skippedWithErrors + " skipped (had errors)" : "")
          + (skipped > 0 ? " (" + skipped + " up-to-date)" : "");
      if (ctx.outputRouter != null) ctx.outputRouter.info(line);
      else System.out.println(line);
    }
  }

  /**
   * Returns true if any typecheckable definition reachable from {@code group} has
   * status {@link Definition.TypeCheckingStatus#HAS_ERRORS}. Used to gate persist:
   * caching a module that contains an erroneous def would only feed the
   * deserialize → orphan-shell-detected → clear → re-typecheck cycle in
   * {@code CliServerRequester.loadBinaryCache}.
   */
  public static boolean groupHasTypecheckingErrors(ConcreteGroup group) {
    if (group.referable() instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
      Definition def = tcRef.getTypechecked();
      if (def != null && def.status() == Definition.TypeCheckingStatus.HAS_ERRORS) return true;
    }
    for (org.arend.naming.reference.InternalReferable internalRef : group.getInternalReferables()) {
      if (internalRef instanceof TCDefReferable tcRef && tcRef.getKind().isTypecheckable()) {
        Definition def = tcRef.getTypechecked();
        if (def != null && def.status() == Definition.TypeCheckingStatus.HAS_ERRORS) return true;
      }
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && groupHasTypecheckingErrors(statement.group())) return true;
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      if (groupHasTypecheckingErrors(dynGroup)) return true;
    }
    return false;
  }

  private static void reportTypeCheckResult(AiOutputRouter router, ModuleLocation module, GeneralError.Level result) {
    if (router != null) {
      router.moduleResult(module, result);
    } else {
      System.out.println("[" + resultChar(result) + "]" + " " + module.getModulePath());
    }
  }

  private static char resultChar(GeneralError.Level result) {
    if (result == null) return ' ';
    return switch (result) {
      case GOAL -> '◯';
      case ERROR -> '✗';
      default -> '·';
    };
  }

  // ───────── -p / --show-sizes / --show-modules ─────────

  private static void printDefinitions(ArendServer server, String printString, CommandContext ctx) {
    if (printString == null) return;

    Pair<ModulePath, LongName> pair = ctx.parseFullName(printString);
    if (pair != null) {
      ModuleLocation module = server.findModule(pair.proj1, null, false, false);
      if (module == null) {
        ctx.systemErrErrorReporter.report(new ModuleNotFoundError(pair.proj1));
      } else {
        boolean found = pair.proj2 == null;
        for (DefinitionData definitionData : server.getResolvedDefinitions(module)) {
          if (pair.proj2 == null
              || definitionData.definition().getData().getRefLongName().equals(pair.proj2)) {
            Definition definition = definitionData.definition().getData().getTypechecked();
            if (definition != null) {
              System.out.println();
              StringBuilder builder = new StringBuilder();
              ToAbstractVisitor.convert(definition, DEFAULT).prettyPrint(builder, DEFAULT);
              System.out.println(builder);
            }

            if (pair.proj2 != null) {
              found = true;
              break;
            }
          }
        }
        if (!found) {
          ctx.systemErrErrorReporter.report(new DefinitionNotFoundError(new FullName(module, pair.proj2)));
        }
      }
    }
  }

  private static void showSizes(ArendServer server, SourceLibrary library) {
    Map<Definition, Integer> sizes = new HashMap<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
          && module.getLibraryName().equals(library.getLibraryName())) {
        for (DefinitionData definitionData : server.getResolvedDefinitions(module)) {
          Definition definition = definitionData.definition().getData().getTypechecked();
          if (definition != null) {
            sizes.put(definition, SizeExpressionVisitor.getSize(definition));
          }
        }
      }
    }

    System.out.println();
    List<Pair<Definition, Integer>> list = new ArrayList<>(sizes.size());
    for (Map.Entry<Definition, Integer> entry : sizes.entrySet()) {
      list.add(new Pair<>(entry.getKey(), entry.getValue()));
    }
    list.sort((o1, o2) -> Long.compare(o2.proj2, o1.proj2));
    for (Pair<Definition, Integer> pair : list) {
      System.out.println(pair.proj1.getReferable().getRefLongName() + ": " + pair.proj2);
    }
  }

  private static void showModules(ArendServer server, SourceLibrary library, boolean allModules) {
    Map<ModulePath, List<ModulePath>> map = new HashMap<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
          && module.getLibraryName().equals(library.getLibraryName())) {
        ConcreteGroup group = server.getRawGroup(module);
        if (group == null) continue;
        boolean withInstances = allModules;
        List<ModulePath> dependencies = new ArrayList<>();
        for (ConcreteStatement statement : group.statements()) {
          ConcreteNamespaceCommand cmd = statement.command();
          if (cmd != null && cmd.isImport()) {
            dependencies.add(new ModulePath(cmd.module().getPath()));
          }
          if (!withInstances && !dependencies.isEmpty()) {
            ConcreteGroup subgroup = statement.group();
            if (subgroup != null && subgroup.referable().getKind() == GlobalReferable.Kind.INSTANCE) {
              withInstances = true;
            }
          }
        }
        if (withInstances) {
          map.put(module.getModulePath(), dependencies);
        }
      }
    }

    new MapTarjanSCC<>(map) {
      @Override
      protected void unitFound(ModulePath unit, boolean withLoops) {
        System.out.println("[" + unit + "]");
      }

      @Override
      protected void sccFound(List<ModulePath> scc) {
        System.out.println(scc);
      }
    }.order();
  }
}
