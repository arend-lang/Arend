package org.arend.frontend.cli.commands;

import org.apache.commons.cli.CommandLine;
import org.arend.cli.ArendOptions;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.server.TimedProgressReporter;
import org.arend.server.TypecheckReports;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.query.QualifiedName;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.prelude.Prelude;
import org.arend.server.BinaryCacheFilter;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.ErrorService;
import org.arend.source.PersistableBinarySource;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The typecheck command: load what caches are usable, typecheck what is left, report, and
 * persist. Everything the CLI does that is not a query tool.
 *
 * <p>Two shapes, chosen by whether any module was named on the command line. With none, every
 * requested library is typechecked whole and reported as a per-module table. With some, only
 * those modules (or single definitions inside them) are, and there is no table.
 */
public final class TypecheckPipeline {
  private TypecheckPipeline() {}

  /** @return false if the run should be reported as failed, independently of {@code ctx.exitWithError}. */
  public static boolean run(CommandContext ctx, CommandLine cmdLine) {
    // The pipeline reports each module out of the server's error store, once that module has
    // been checked. That is the only account that is also right on a warm server, where a
    // module resolved on an earlier run reports nothing on this one; letting the same
    // diagnostics stream past as they arrive would simply print them a second time.
    boolean oldStream = ctx.streamDiagnostics;
    ctx.streamDiagnostics = false;
    try {
      return runPipeline(ctx, cmdLine);
    } finally {
      ctx.streamDiagnostics = oldStream;
    }
  }

  private static boolean runPipeline(CommandContext ctx, CommandLine cmdLine) {
    CliSetup.expandModulePatterns(ctx);
    if (ctx.exitWithError) return false;
    extendScopeForPrintTarget(ctx, cmdLine);
    // Each library's source modules, read off its directory once for the whole run.
    Map<SourceLibrary, List<ModulePath>> sources = sourcesOf(ctx.requestedLibraries);
    dropDeletedModules(ctx, sources);

    // Modules whose stored diagnostics have already been printed by this run.
    Set<ModuleLocation> reported = new HashSet<>();

    TimedProgressReporter timedProgressReporter = cmdLine.hasOption(ArendOptions.SHOW_TIMES) ? new TimedProgressReporter() : null;
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter = timedProgressReporter != null ? timedProgressReporter : ProgressReporter.empty();

    // Pre-load binary caches (unless --recompile is set)
    if (!ctx.recompile) {
      // Typecheck Prelude first — binary cache loading needs Prelude definitions to be available
      ctx.server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
      Set<String> libraryNames = QualifiedName.libraryNames(ctx.requestedLibraries);
      boolean resolvedRequestedScope = false;
      if (ctx.requestedModules.isEmpty()) {
        // Whole-library typechecking: resolve every module of each requested library.
        for (SourceLibrary library : ctx.requestedLibraries) {
          List<ModuleLocation> allModules = sourceLocations(library, sources.get(library));
          if (!allModules.isEmpty()) {
            ctx.server.getCheckerFor(allModules).resolveAll(ctx.cancellation, ProgressReporter.empty());
            resolvedRequestedScope = true;
          }
        }
      } else {
        // Targeted typechecking: seed resolveAll with just the requested modules
        // so only their transitive import cone is raw-loaded. loadBinaryCache then
        // iterates ctx.server.getModules() — by now the cone — and only deserializes
        // ARCs in it, avoiding the cost of touching every cached file in a large
        // dependency library like arend-lib.
        List<ModuleLocation> targets = new ArrayList<>();
        for (Pair<ModulePath, LongName> requested : ctx.requestedModules) {
          ModuleLocation module = ctx.server.findModule(requested.proj1, null, true, false);
          if (module != null) targets.add(module);
        }
        if (!targets.isEmpty()) {
          ctx.server.getCheckerFor(targets).resolveAll(ctx.cancellation, ProgressReporter.empty());
          resolvedRequestedScope = true;
        }
      }
      if (resolvedRequestedScope) {
        for (SourceLibrary library : dependencyFirstLibraries(ctx)) {
          ctx.binaryLoader.loadBinaryCache(library, ctx.server);
          if (!libraryNames.contains(library.getLibraryName())) {
            typecheckUncachedDependencyModules(ctx, library);
          }
        }
      }
    }

    if (ctx.requestedModules.isEmpty()) {
      for (SourceLibrary library : ctx.requestedLibraries) {
        System.out.println();
        System.out.println("--- Typechecking " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        List<ModulePath> modulesToTypecheck = sources.get(library);
        int totalModules = modulesToTypecheck.size();
        int checkedModules = 0;
        for (ModulePath modulePath : modulesToTypecheck) {
          ctx.reportModuleProgress("Typechecking", checkedModules, totalModules, modulePath);
          ModuleLocation module = new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath);
          ctx.server.getCheckerFor(Collections.singletonList(module)).typecheck(ctx.cancellation, progressReporter);
          reportStoredDiagnostics(ctx, module, reported);
          checkedModules++;
        }
        ctx.finishProgressLine();

        time = System.currentTimeMillis() - time;

        // Output nice per-module typechecking results
        int numWithErrors = 0;
        int numWithGoals = 0;
        for (ModuleLocation module : ctx.server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
            GeneralError.Level result = ctx.moduleResults.get(module);
            reportTypeCheckResult(ctx, module.getModulePath(), result);
            if (result == GeneralError.Level.ERROR) numWithErrors++;
            if (result == GeneralError.Level.GOAL) numWithGoals++;
          }
        }

        if (numWithErrors > 0) {
          ctx.exitWithError = true;
          System.out.println("Number of modules with errors: " + numWithErrors);
        }
        if (numWithGoals > 0) {
          System.out.println("Number of modules with goals: " + numWithGoals);
        }
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
        printTimes(timedProgressReporter);

        TypecheckReports.showRequested(ctx.server, library.getLibraryName(), cmdLine, System.out);

        if (cmdLine.hasOption(ArendOptions.LINT)) {
          System.out.println();
          TypecheckReports.lint(ctx.server, modulesOf(ctx, library, ModuleLocation.LocationKind.SOURCE), ctx.exitWithError, System.out);
        }

        if (ctx.doubleCheck && numWithErrors == 0) {
          doubleCheck(ctx, library.getLibraryName(), modulesOf(ctx, library, ModuleLocation.LocationKind.SOURCE));
        }

        // Not after a cancellation: the modules that were skipped did not fail, they were never
        // reached, and writing .arc caches for that state leaves the gap behind for every later
        // run to load as if it were checked.
        if (ctx.serialize && !ctx.cancellation.isCanceled()) {
          persistLibrary(ctx, library, ctx.binaryLoader.getBinaryCacheLoaded());
        }
      }
    } else {
      // Times and sizes are reported once for all requested modules, in one list each, so
      // everything is typechecked first and double-checked only after they are printed.
      // Reported like a whole-library run: one banner, a progress line, the result table and
      // the total time, rather than a banner and a time per requested module.
      System.out.println();
      System.out.println("--- Typechecking ---");
      long time = System.currentTimeMillis();

      Set<ModuleLocation> checkedModules = new LinkedHashSet<>();
      List<ModuleLocation> modulesToDoubleCheck = new ArrayList<>();
      int totalTargets = ctx.requestedModules.size();
      int checkedTargets = 0;
      List<ModuleLocation> requestedLocations = new ArrayList<>();
      for (Pair<ModulePath, LongName> requested : ctx.requestedModules) {
        ModulePath modulePath = requested.proj1;
        LongName definitionName = requested.proj2;
        ctx.reportModuleProgress("Typechecking", checkedTargets++, totalTargets, definitionName == null ? modulePath.toString() : modulePath + ":" + definitionName);
        ModuleLocation module = ctx.server.findModule(modulePath, null, true, false);
        if (module != null) requestedLocations.add(module);
        if (module == null) {
          ctx.systemErrErrorReporter.report(new ModuleNotFoundError(modulePath));
        } else if (definitionName != null) {
          if (!TypecheckReports.checkDefinitionExists(ctx.server, module, definitionName, ctx.systemErrErrorReporter)) continue;
          FullName fullName = new FullName(module, definitionName);
          ctx.server.getCheckerFor(Collections.singletonList(module)).typecheck(Collections.singletonList(fullName), ctx.errorReporter, ctx.cancellation, progressReporter);
          reportStoredDiagnostics(ctx, module, reported);
          checkedModules.add(module);
        } else {
          ctx.server.getCheckerFor(Collections.singletonList(module)).typecheck(ctx.cancellation, progressReporter);
          reportStoredDiagnostics(ctx, module, reported);
          checkedModules.add(module);
          modulesToDoubleCheck.add(module);
        }
      }
      ctx.finishProgressLine();

      time = System.currentTimeMillis() - time;

      int numWithErrors = 0;
      int numWithGoals = 0;
      for (ModuleLocation module : checkedModules) {
        GeneralError.Level result = ctx.moduleResults.get(module);
        reportTypeCheckResult(ctx, module.getModulePath(), result);
        if (result == GeneralError.Level.ERROR) numWithErrors++;
        if (result == GeneralError.Level.GOAL) numWithGoals++;
      }

      if (numWithErrors > 0) {
        ctx.exitWithError = true;
        System.out.println("Number of modules with errors: " + numWithErrors);
      }
      if (numWithGoals > 0) {
        System.out.println("Number of modules with goals: " + numWithGoals);
      }
      System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

      boolean qualify = checkedModules.size() > 1;
      printTimes(timedProgressReporter, qualify);
      if (cmdLine.hasOption(ArendOptions.SHOW_SIZES)) {
        TypecheckReports.showSizes(ctx.server, checkedModules, qualify, System.out);
      }

      if (ctx.doubleCheck && numWithErrors == 0 && !modulesToDoubleCheck.isEmpty()) {
        doubleCheck(ctx, null, modulesToDoubleCheck);
      }
      if (cmdLine.hasOption(ArendOptions.LINT)) {
        System.out.println();
        TypecheckReports.lint(ctx.server, requestedLocations, ctx.exitWithError, System.out);
      }

      // See above: a cancelled run has nothing worth caching.
      if (ctx.serialize && !ctx.cancellation.isCanceled()) {
        // Persist all libraries that had modules typechecked
        for (SourceLibrary library : ctx.requestedLibraries) {
          persistLibrary(ctx, library, ctx.binaryLoader.getBinaryCacheLoaded());
        }
      }
    }

    if (cmdLine.hasOption("p")) {
      TypecheckReports.printDefinitions(ctx.server, cmdLine.getOptionValue("p"), ctx.systemErrErrorReporter, System.out);
    }

    if (cmdLine.hasOption("t")) {
      for (SourceLibrary library : ctx.requestedLibraries) {
        long time = TypecheckReports.testsStarted(library.getLibraryName(), System.out);

        List<ModulePath> testModulesToTypecheck = library.findModules(true);
        int totalTestModules = testModulesToTypecheck.size();
        int checkedTestModules = 0;
        for (ModulePath modulePath : testModulesToTypecheck) {
          ctx.reportModuleProgress("Typechecking", checkedTestModules, totalTestModules, modulePath);
          ctx.server.getCheckerFor(Collections.singletonList(new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.TEST, modulePath))).typecheck(ctx.cancellation, progressReporter);
          checkedTestModules++;
        }
        ctx.finishProgressLine();

        TypecheckReports.testsFinished(ctx.server, library.getLibraryName(), time, System.out);
        printTimes(timedProgressReporter);

        if (ctx.doubleCheck) {
          doubleCheck(ctx, "tests in " + library.getLibraryName(), modulesOf(ctx, library, ModuleLocation.LocationKind.TEST));
        }
      }
    }

    return true;
  }

  /**
   * {@code -c}: checks the core of {@code modules} again, under the banner
   * {@code --- Checking what ---}, or {@code --- Checking ---} when {@code what} is null.
   */
  private static void doubleCheck(CommandContext ctx, String what, List<? extends ModuleLocation> modules) {
    System.out.println();
    System.out.println(what == null ? "--- Checking ---" : "--- Checking " + what + " ---");
    long time = System.currentTimeMillis();
    try {
      CoreModuleChecker checker = new CoreModuleChecker(ctx::reportAndPrint);
      for (int i = 0; i < modules.size(); i++) {
        ModuleLocation module = modules.get(i);
        ctx.reportModuleProgress("Checking", i, modules.size(), module.getModulePath());
        ConcreteGroup group = ctx.server.getRawGroup(module);
        if (group != null) checker.checkGroup(group);
      }
    } finally {
      ctx.finishProgressLine();
      System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
    }
  }

  /** The modules of {@code library} of {@code kind} the server holds. */
  private static List<? extends ModuleLocation> modulesOf(CommandContext ctx, SourceLibrary library, ModuleLocation.LocationKind kind) {
    return ctx.server.getModules().stream()
        .filter(module -> module.getLocationKind() == kind && module.getLibraryName().equals(library.getLibraryName()))
        .toList();
  }

  /**
   * Times are printed right after the phase that collected them, not at the end of the run:
   * whatever follows (double-check, persisting, tests) may fail or hang, and should not take
   * the times with it.
   */
  private static void printTimes(TimedProgressReporter timedProgressReporter) {
    printTimes(timedProgressReporter, false);
  }

  private static void printTimes(TimedProgressReporter timedProgressReporter, boolean qualify) {
    if (timedProgressReporter != null) {
      timedProgressReporter.print(System.out, qualify);
    }
  }

  private static void reportTypeCheckResult(CommandContext ctx, ModulePath modulePath, GeneralError.Level result) {
    System.out.println("[" + resultChar(result) + "]" + " " + modulePath);
  }

  private static char resultChar(GeneralError.Level result) {
    if (result == null) {
      return ' ';
    }
    return switch (result) {
      case GOAL -> '◯';
      case ERROR -> '✗';
      default -> '·';
    };
  }

  private static void typecheckUncachedDependencyModules(CommandContext ctx, SourceLibrary library) {
    List<ModuleLocation> modules = new ArrayList<>();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
          && module.getLibraryName().equals(library.getLibraryName())
          && !ctx.binaryLoader.getBinaryCacheLoaded().contains(module)) {
        modules.add(module);
      }
    }
    if (modules.isEmpty()) return;

    // Nothing needs silencing here any more: the pipeline prints from the store, per module,
    // and only for the libraries that were asked for. Whatever is wrong in a dependency is
    // recorded but not printed, and will be reported by the pass that actually asks for it.
    ctx.server.getCheckerFor(modules).typecheck(ctx.cancellation, ProgressReporter.empty());
  }

  /**
   * {@code -p MODULE[:DEF]} prints an elaborated form <em>after</em> the typecheck, so there is
   * nothing to print unless the target was typechecked. When a scope was named on the command
   * line and the target is outside it, add it -- that is what the user asked for.
   *
   * <p>Only then. With no positional scope the run is whole-library and already covers the
   * target; adding a module would make the scope non-empty and flip the run into targeted mode,
   * silently replacing the library-wide typecheck -- and its banner, module table, error count,
   * {@code --show-*} output and {@code --double-check} pass -- with one module.
   */
  private static void extendScopeForPrintTarget(CommandContext ctx, CommandLine cmdLine) {
    if (!cmdLine.hasOption("p")) return;
    ModulePath module = TypecheckReports.printTargetOutside(cmdLine.getOptionValue("p"),
        ctx.requestedModules.stream().map(requested -> requested.proj1).toList(), ctx.systemErrErrorReporter, System.out);
    if (module != null) ctx.requestedModules.add(new Pair<>(module, null));
  }

  private static List<SourceLibrary> dependencyFirstLibraries(CommandContext ctx) {
    List<SourceLibrary> result = new ArrayList<>();
    Set<String> visiting = new HashSet<>();
    Set<String> visited = new HashSet<>();
    for (SourceLibrary library : ctx.requestedLibraries) {
      collectDependencyFirst(ctx.libraryManager, library, visiting, visited, result);
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

  /**
   * Prints everything the server's error store holds for {@code module}, once per run.
   *
   * <p>Called after the module has been checked, so the store is final: whatever this run
   * produced is in it, and so is whatever an earlier run produced and this one did not repeat.
   * That second case is the whole point. Name-resolution errors are pushed to the reporters
   * only by {@code ErrorService.setResolverErrors}, i.e. only when a module is actually
   * (re-)resolved, so on a context that outlives one command they were reported once and never
   * again -- the module then listed as fine, no error printed, no "Number of modules with
   * errors" line, exit 0, while the source on disk was still broken (arend-lang/Arend#138).
   *
   * <p>Reading the verdict from the same place that prints it is what keeps the two from
   * disagreeing: {@link CommandContext#reportAndPrint} records the level as it prints, so the
   * module list and the exit code follow from the store rather than from whether anything
   * happened to be emitted.
   */
  private static void reportStoredDiagnostics(CommandContext ctx, ModuleLocation module, Set<ModuleLocation> reported) {
    if (!(ctx.server instanceof ArendServerImpl impl) || !reported.add(module)) return;
    ErrorService errorService = impl.getErrorService();
    for (GeneralError error : errorService.getResolverErrors(module)) {
      ctx.reportAndPrint(error);
    }
    for (GeneralError error : errorService.getTypecheckingErrors(module)) {
      ctx.reportAndPrint(error);
    }
  }

  /** The source modules of each of {@code libraries}, read off its directory. */
  private static Map<SourceLibrary, List<ModulePath>> sourcesOf(List<SourceLibrary> libraries) {
    Map<SourceLibrary, List<ModulePath>> sources = new HashMap<>();
    for (SourceLibrary library : libraries) sources.put(library, library.findModules(false));
    return sources;
  }

  /** {@code library}'s source modules {@code modulePaths}, as the server names them. */
  private static List<ModuleLocation> sourceLocations(SourceLibrary library, List<ModulePath> modulePaths) {
    return modulePaths.stream()
        .map(modulePath -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath))
        .toList();
  }

  /**
   * Removes modules of the requested libraries whose source file is gone.
   *
   * <p>A warm server keeps a module after its {@code .ard} is deleted, because nothing rescans
   * the library between commands. Dropping it here -- rather than filtering it out of the
   * module list, the diagnostics and the persist pass one at a time -- is what makes it stay
   * gone: {@code ArendServerImpl.removeModule} also clears the module's entries from the error
   * store, so a deleted file stops being reported at all rather than being reported forever.
   *
   * <p>Inert on a cold run, where the server was told about exactly the modules on disk.
   */
  private static void dropDeletedModules(CommandContext ctx, Map<SourceLibrary, List<ModulePath>> sources) {
    List<ModuleLocation> gone = new ArrayList<>();
    for (SourceLibrary library : sources.keySet()) {
      Set<ModulePath> onDisk = new HashSet<>(sources.get(library));
      for (ModuleLocation module : ctx.server.getModules()) {
        if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
            && module.getLibraryName().equals(library.getLibraryName())
            && !onDisk.contains(module.getModulePath())) {
          gone.add(module);
        }
      }
    }
    // Collected first: removeModule mutates what getModules() iterates.
    for (ModuleLocation module : gone) {
      ctx.server.removeModule(module);
    }
  }

  private static void persistLibrary(CommandContext ctx, SourceLibrary library, Set<ModuleLocation> skipModules) {
    if (!library.supportsPersisting()) return;
    int persisted = 0;
    int skipped = 0;
    int failed = 0;
    int skippedWithErrors = 0;
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        if (skipModules.contains(module)) {
          skipped++;
          continue;
        }
        // A module is skipped only if something that was checked has an error or an unfilled
        // goal -- not if the run left some (or all) of its definitions untouched, which is the
        // normal case for a targeted run: it typechecks the requested import cone, and most of
        // the transitively-loaded modules only get the definitions that cone actually reached, or
        // none at all. ModuleSerialization writes whatever cores it has -- none, some, or all --
        // and leaves the rest out rather than refusing the whole module, so refusing it here
        // would throw away a cache the next load can use just fine for what it does contain. A
        // HAS_ERRORS def is refused for a different reason: in a long-lived daemon, a cache the
        // next load cannot use accumulates orphan FunctionDefinitions pinned by cached expression
        // trees across the deserialize → clear → re-typecheck cycle.
        ConcreteGroup group = ctx.server.getRawGroup(module);
        if (group != null && !BinaryCacheFilter.isCacheable(group)) {
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
      System.out.println("[INFO] Persisted " + persisted + " module(s)"
          + (failed > 0 ? ", " + failed + " failed" : "")
          + (skippedWithErrors > 0 ? ", " + skippedWithErrors + " skipped (had errors or goals)" : "")
          + (skipped > 0 ? " (" + skipped + " up-to-date)" : ""));
    }
  }

}
