package org.arend.frontend.cli.commands;

import org.apache.commons.cli.CommandLine;
import org.arend.core.definition.Definition;
import org.arend.core.expr.visitor.SizeExpressionVisitor;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.util.Pair;
import org.arend.frontend.TimedProgressReporter;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
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
import org.arend.typechecking.error.local.GoalError;

import static org.arend.ext.prettyprinting.PrettyPrinterConfig.DEFAULT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  public static final String SHOW_TIMES = "show-times";
  public static final String SHOW_SIZES = "show-sizes";
  public static final String SHOW_MODULES = "show-modules";
  public static final String SHOW_MODULES_WITH_INSTANCES = "show-modules-with-instances";

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
    extendScopeForPrintTarget(ctx, cmdLine);
    dropDeletedModules(ctx);

    // Modules whose stored diagnostics have already been printed by this run.
    Set<ModuleLocation> reported = new HashSet<>();

    TimedProgressReporter timedProgressReporter = cmdLine.hasOption(SHOW_TIMES) ? new TimedProgressReporter() : null;
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter = timedProgressReporter != null ? timedProgressReporter : ProgressReporter.empty();

    // Pre-load binary caches (unless --recompile is set)
    if (!ctx.recompile) {
      // Typecheck Prelude first — binary cache loading needs Prelude definitions to be available
      ctx.server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
      Set<String> libraryNames = requestedLibraryNames(ctx.requestedLibraries);
      boolean resolvedRequestedScope = false;
      if (ctx.requestedModules.isEmpty()) {
        // Whole-library typechecking: resolve every module of each requested library.
        for (SourceLibrary library : ctx.requestedLibraries) {
          List<ModuleLocation> allModules = library.findModules(false).stream()
              .map(mp -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
              .toList();
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

        List<ModulePath> modulesToTypecheck = library.findModules(false);
        int totalModules = modulesToTypecheck.size();
        int checkedModules = 0;
        for (ModulePath modulePath : modulesToTypecheck) {
          ctx.reportModuleProgress(checkedModules, totalModules, modulePath);
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

        if (cmdLine.hasOption(SHOW_SIZES)) {
          showSizes(ctx, library);
        }

        if (cmdLine.hasOption(SHOW_MODULES)) {
          System.out.println();
          System.out.println("Modules cycles:");
          showModules(ctx, library, true);
        }

        if (cmdLine.hasOption(SHOW_MODULES_WITH_INSTANCES)) {
          System.out.println();
          System.out.println("Modules with instances cycles:");
          showModules(ctx, library, false);
        }

        if (ctx.doubleCheck && numWithErrors == 0) {
          System.out.println();
          System.out.println("--- Checking " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(ctx::reportAndPrint);
            for (ModuleLocation module : ctx.server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
                ConcreteGroup group = ctx.server.getRawGroup(module);
                if (group != null) {
                  checker.checkGroup(group);
                }
              }
            }
          } finally {
            time = System.currentTimeMillis() - time;
            System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }

        if (ctx.serialize) {
          persistLibrary(ctx, library, ctx.binaryLoader.getBinaryCacheLoaded());
        }
      }
    } else {
      for (Pair<ModulePath, LongName> requested : ctx.requestedModules) {
        ModulePath modulePath = requested.proj1;
        LongName definitionName = requested.proj2;
        ModuleLocation module = ctx.server.findModule(modulePath, null, true, false);
        if (module == null) {
          ctx.systemErrErrorReporter.report(new ModuleNotFoundError(modulePath));
        } else if (definitionName != null) {
          System.out.println();
          FullName fullName = new FullName(module, definitionName);
          System.out.println("--- Typechecking " + fullName + " ---");
          long time = System.currentTimeMillis();

          ctx.server.getCheckerFor(Collections.singletonList(module)).typecheck(Collections.singletonList(fullName), ctx.errorReporter, ctx.cancellation, progressReporter);
          reportStoredDiagnostics(ctx, module, reported);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
        } else {
          System.out.println();
          System.out.println("--- Typechecking " + module + " ---");
          long time = System.currentTimeMillis();

          ctx.server.getCheckerFor(Collections.singletonList(module)).typecheck(ctx.cancellation, progressReporter);
          reportStoredDiagnostics(ctx, module, reported);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");

          if (ctx.doubleCheck) {
            System.out.println();
            System.out.println("--- Checking " + module + " ---");
            time = System.currentTimeMillis();

            try {
              CoreModuleChecker checker = new CoreModuleChecker(ctx::reportAndPrint);
              ConcreteGroup group = ctx.server.getRawGroup(module);
              if (group != null) {
                checker.checkGroup(group);
              }
            } finally {
              time = System.currentTimeMillis() - time;
              System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
            }
          }
        }
      }
      if (ctx.serialize) {
        // Persist all libraries that had modules typechecked
        for (SourceLibrary library : ctx.requestedLibraries) {
          persistLibrary(ctx, library, ctx.binaryLoader.getBinaryCacheLoaded());
        }
      }
    }

    printDefinitions(ctx, cmdLine.getOptionValue("p"));

    if (cmdLine.hasOption("t")) {
      for (SourceLibrary library : ctx.requestedLibraries) {
        System.out.println();
        System.out.println("--- Running tests in " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        List<ModulePath> testModulesToTypecheck = library.findModules(true);
        int totalTestModules = testModulesToTypecheck.size();
        int checkedTestModules = 0;
        for (ModulePath modulePath : testModulesToTypecheck) {
          ctx.reportModuleProgress(checkedTestModules, totalTestModules, modulePath);
          ctx.server.getCheckerFor(Collections.singletonList(new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.TEST, modulePath))).typecheck(ctx.cancellation, progressReporter);
          checkedTestModules++;
        }
        ctx.finishProgressLine();

        time = System.currentTimeMillis() - time;

        int[] total = new int[1];
        int[] failed = new int[1];
        for (ModuleLocation module : ctx.server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.TEST && module.getLibraryName().equals(library.getLibraryName())) {
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

        System.out.println("Tests completed: " + total[0] + ", Failed: " + failed[0]);
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

        if (ctx.doubleCheck) {
          System.out.println();
          System.out.println("--- Checking tests in " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(ctx::reportAndPrint);
            for (ModuleLocation module : ctx.server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.TEST && module.getLibraryName().equals(library.getLibraryName())) {
                ConcreteGroup group = ctx.server.getRawGroup(module);
                if (group != null) {
                  checker.checkGroup(group);
                }
              }
            }
          } finally {
            time = System.currentTimeMillis() - time;
            System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }
      }
    }

    if (timedProgressReporter != null) {
      timedProgressReporter.print();
    }

    return true;
  }

  private static void showSizes(CommandContext ctx, SourceLibrary library) {
    Map<Definition, Integer> sizes = new HashMap<>();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        for (DefinitionData definitionData : ctx.server.getResolvedDefinitions(module)) {
          Definition definition = definitionData.definition().getData().getTypechecked();
          if (definition != null) {
            sizes.put(definition, SizeExpressionVisitor.getSize(definition));
          }
        }
      }
    }

    System.out.println();
    List<Pair<Definition,Integer>> list = new ArrayList<>(sizes.size());
    for (Map.Entry<Definition, Integer> entry : sizes.entrySet()) {
      list.add(new Pair<>(entry.getKey(), entry.getValue()));
    }
    list.sort((o1, o2) -> Long.compare(o2.proj2, o1.proj2));
    for (Pair<Definition, Integer> pair : list) {
      System.out.println(pair.proj1.getReferable().getRefLongName() + ": " + pair.proj2);
    }
  }

  private static void printDefinitions(CommandContext ctx, String printString) {
    if (printString == null) return;

    Pair<ModulePath, LongName> pair = ctx.parseFullName(printString);
    if (pair != null) {
      ModuleLocation module = ctx.server.findModule(pair.proj1, null, false, false);
      if (module == null) {
        ctx.systemErrErrorReporter.report(new ModuleNotFoundError(pair.proj1));
      } else {
        boolean found = pair.proj2 == null;
        for (DefinitionData definitionData : ctx.server.getResolvedDefinitions(module)) {
          if (pair.proj2 == null || definitionData.definition().getData().getRefLongName().equals(pair.proj2)) {
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

  private static void showModules(CommandContext ctx, SourceLibrary library, boolean allModules) {
    Map<ModulePath, List<ModulePath>> map = new HashMap<>();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        ConcreteGroup group = ctx.server.getRawGroup(module);
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
    if (!cmdLine.hasOption("p") || ctx.requestedModules.isEmpty()) return;
    Pair<ModulePath, LongName> parsed = ctx.parseFullName(cmdLine.getOptionValue("p"));
    if (parsed == null) return;
    for (Pair<ModulePath, LongName> requested : ctx.requestedModules) {
      if (requested.proj1.equals(parsed.proj1)) return;
    }
    System.out.println("[INFO] -p target " + cmdLine.getOptionValue("p")
        + " is outside the typecheck scope; adding " + parsed.proj1 + " to it");
    ctx.requestedModules.add(new Pair<>(parsed.proj1, null));
  }

  private static Set<String> requestedLibraryNames(List<SourceLibrary> requestedLibraries) {
    Set<String> result = new HashSet<>();
    for (SourceLibrary library : requestedLibraries) {
      result.add(library.getLibraryName());
    }
    return result;
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
  private static void dropDeletedModules(CommandContext ctx) {
    List<ModuleLocation> gone = new ArrayList<>();
    for (SourceLibrary library : ctx.requestedLibraries) {
      Set<ModulePath> onDisk = new HashSet<>(library.findModules(false));
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
        // Skip modules whose typechecked state contains any HAS_ERRORS def.  Persisting
        // them would write a cache that the next load can't use (the deserialized
        // module would still need re-typechecking from source) and, in a long-lived
        // daemon, accumulates orphan FunctionDefinitions pinned by cached expression
        // trees across the deserialize → clear → re-typecheck cycle.
        org.arend.term.group.ConcreteGroup group = ctx.server.getRawGroup(module);
        if (group != null && (groupHasTypecheckingErrors(group) || groupHasGoals(group))) {
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
          + (skippedWithErrors > 0 ? ", " + skippedWithErrors + " skipped (had errors)" : "")
          + (skipped > 0 ? " (" + skipped + " up-to-date)" : ""));
    }
  }

  /**
   * True if any definition in {@code group} holds an unfilled goal.
   *
   * <p>Goals keep a module out of the cache for the same reason errors do, and for one more:
   * the {@code .arc} records that a definition had a goal but not what the goal was, so a
   * module restored from cache could only ever report a contentless placeholder. Not caching
   * it means every goal comes from a real typecheck, with its context intact.
   */
  public static boolean groupHasGoals(ConcreteGroup group) {
    if (group.referable() instanceof TCDefReferable tcRef) {
      Definition def = tcRef.getTypechecked();
      if (def != null && def.getGoals().contains(def)) return true;
    }
    for (ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && groupHasGoals(statement.group())) return true;
    }
    for (ConcreteGroup dynGroup : group.dynamicGroups()) {
      if (groupHasGoals(dynGroup)) return true;
    }
    return false;
  }

  public static boolean groupHasTypecheckingErrors(org.arend.term.group.ConcreteGroup group) {
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
    for (org.arend.term.group.ConcreteStatement statement : group.statements()) {
      if (statement.group() != null && groupHasTypecheckingErrors(statement.group())) return true;
    }
    for (org.arend.term.group.ConcreteGroup dynGroup : group.dynamicGroups()) {
      if (groupHasTypecheckingErrors(dynGroup)) return true;
    }
    return false;
  }
}
