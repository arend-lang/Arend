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
import org.arend.frontend.TimedProgressReporter;
import org.arend.frontend.cli.CliSetup;
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.query.QualifiedName;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.error.SourcePosition;
import org.arend.server.BinaryCacheFilter;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.ArendServerImpl;
import org.arend.server.impl.DefinitionData;
import org.arend.server.impl.ErrorService;
import org.arend.server.imports.ImportUsageData;
import org.arend.source.PersistableBinarySource;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;
import org.arend.typechecking.order.MapTarjanSCC;
import org.arend.typechecking.visitor.CollectDefCallsVisitor;

import static org.arend.ext.prettyprinting.PrettyPrinterConfig.DEFAULT;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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
  public static final String LINT = "lint";
  public static final String SHOW_CYCLES = "show-cycles";
  public static final String DEPENDENCY_GRAPH = "dependency-graph";
  private static final int MAX_CYCLE_USERS = 6;

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
      Set<String> libraryNames = QualifiedName.libraryNames(ctx.requestedLibraries);
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

        if (cmdLine.hasOption(SHOW_SIZES)) {
          List<ModuleLocation> modules = new ArrayList<>();
          for (ModuleLocation module : ctx.server.getModules()) {
            if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
              modules.add(module);
            }
          }
          showSizes(ctx, modules, false);
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

        if (cmdLine.hasOption(SHOW_CYCLES)) {
          System.out.println();
          showCycles(ctx, library);
        }

        if (cmdLine.hasOption(DEPENDENCY_GRAPH)) {
          writeDependencyGraph(ctx, library, cmdLine.getOptionValue(DEPENDENCY_GRAPH));
        }

        if (cmdLine.hasOption(LINT)) {
          System.out.println();
          lint(ctx, library);
        }

        if (ctx.doubleCheck && numWithErrors == 0) {
          System.out.println();
          System.out.println("--- Checking " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            List<ModuleLocation> modulesToCheck = new ArrayList<>();
            for (ModuleLocation module : ctx.server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
                modulesToCheck.add(module);
              }
            }
            CoreModuleChecker checker = new CoreModuleChecker(ctx::reportAndPrint);
            for (int i = 0; i < modulesToCheck.size(); i++) {
              ModuleLocation module = modulesToCheck.get(i);
              ctx.reportModuleProgress("Checking", i, modulesToCheck.size(), module.getModulePath());
              ConcreteGroup group = ctx.server.getRawGroup(module);
              if (group != null) {
                checker.checkGroup(group);
              }
            }
          } finally {
            ctx.finishProgressLine();
            time = System.currentTimeMillis() - time;
            System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
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
      if (cmdLine.hasOption(SHOW_SIZES)) {
        showSizes(ctx, checkedModules, qualify);
      }

      if (ctx.doubleCheck && numWithErrors == 0 && !modulesToDoubleCheck.isEmpty()) {
        System.out.println();
        System.out.println("--- Checking ---");
        time = System.currentTimeMillis();

        try {
          CoreModuleChecker checker = new CoreModuleChecker(ctx::reportAndPrint);
          for (int i = 0; i < modulesToDoubleCheck.size(); i++) {
            ModuleLocation module = modulesToDoubleCheck.get(i);
            ctx.reportModuleProgress("Checking", i, modulesToDoubleCheck.size(), module.getModulePath());
            ConcreteGroup group = ctx.server.getRawGroup(module);
            if (group != null) {
              checker.checkGroup(group);
            }
          }
        } finally {
          ctx.finishProgressLine();
          time = System.currentTimeMillis() - time;
          System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
        }
      }
      if (cmdLine.hasOption(LINT)) {
        System.out.println();
        lint(ctx, requestedLocations);
      }

      // See above: a cancelled run has nothing worth caching.
      if (ctx.serialize && !ctx.cancellation.isCanceled()) {
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
          ctx.reportModuleProgress("Typechecking", checkedTestModules, totalTestModules, modulePath);
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
        printTimes(timedProgressReporter);

        if (ctx.doubleCheck) {
          System.out.println();
          System.out.println("--- Checking tests in " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            List<ModuleLocation> modulesToCheck = new ArrayList<>();
            for (ModuleLocation module : ctx.server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.TEST && module.getLibraryName().equals(library.getLibraryName())) {
                modulesToCheck.add(module);
              }
            }
            CoreModuleChecker checker = new CoreModuleChecker(ctx::reportAndPrint);
            for (int i = 0; i < modulesToCheck.size(); i++) {
              ModuleLocation module = modulesToCheck.get(i);
              ctx.reportModuleProgress("Checking", i, modulesToCheck.size(), module.getModulePath());
              ConcreteGroup group = ctx.server.getRawGroup(module);
              if (group != null) {
                checker.checkGroup(group);
              }
            }
          } finally {
            ctx.finishProgressLine();
            time = System.currentTimeMillis() - time;
            System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");
          }
        }
      }
    }

    return true;
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
      timedProgressReporter.print(qualify);
    }
  }

  private static void showSizes(CommandContext ctx, Collection<ModuleLocation> modules, boolean qualify) {
    Map<Definition, Integer> sizes = new HashMap<>();
    for (ModuleLocation module : modules) {
      for (DefinitionData definitionData : ctx.server.getResolvedDefinitions(module)) {
        Definition definition = definitionData.definition().getData().getTypechecked();
        if (definition != null) {
          sizes.put(definition, SizeExpressionVisitor.getSize(definition));
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
      TCDefReferable referable = pair.proj1.getReferable();
      System.out.println((qualify ? referable.getRefFullName() : referable.getRefLongName()) + ": " + pair.proj2);
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

  private static void lint(CommandContext ctx, SourceLibrary library) {
    List<ModuleLocation> modules = new ArrayList<>();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library.getLibraryName())) {
        modules.add(module);
      }
    }
    lint(ctx, modules);
  }

  private static void lint(CommandContext ctx, List<ModuleLocation> modules) {
    if (ctx.exitWithError) {
      System.out.println("Unused imports are not reported because the run has errors");
      return;
    }

    List<ModuleLocation> sorted = new ArrayList<>(modules);
    sorted.sort(Comparator.comparing(module -> module.getModulePath().toString()));

    int total = 0;
    List<ModuleLocation> notAnalysed = new ArrayList<>();
    for (ModuleLocation module : sorted) {
      ImportUsageData usage = ctx.server.getNamespaceCommandUsage(module);
      if (usage == null) {
        // not fully typechecked, e.g. only one of its definitions was requested: silence here is not "clean"
        notAnalysed.add(module);
        continue;
      }
      for (ImportUsageData.Part part : usage.getUnusedParts()) {
        Object data = part.data();
        System.out.println((data instanceof SourcePosition ? data : module) + ": " + lintMessage(part));
        total++;
      }
    }
    System.out.println(total == 0 ? "No unused imports" : "Unused imports: " + total);
    if (!notAnalysed.isEmpty()) {
      System.out.println("Not analysed (not fully typechecked): " + notAnalysed.size() + " module(s)");
      for (ModuleLocation module : notAnalysed) {
        System.out.println("  " + module);
      }
    }
  }

  private static String lintMessage(ImportUsageData.Part part) {
    String name = "'" + part.name() + "'";
    return switch (part.kind()) {
      case IMPORT -> "unused import of " + name;
      case OPEN -> "unused open of " + name;
      case NAME -> "unused imported name " + name;
      case ALIAS -> "unused alias " + name;
    };
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

  /**
   * Prints the import cycles of {@param library} that contain at least one source module.
   * Meta modules of extensions are nodes of the graph too; their edges are the imports declared by the extension.
   * For every import inside a cycle, it prints which definitions of the importing module use which definitions of the imported one.
   */
  private static void showCycles(CommandContext ctx, SourceLibrary library) {
    Map<ModulePath, ModuleLocation> nodes = new LinkedHashMap<>();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLibraryName().equals(library.getLibraryName()) && (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE || module.getLocationKind() == ModuleLocation.LocationKind.GENERATED)) {
        nodes.putIfAbsent(module.getModulePath(), module);
      }
    }

    Map<ModulePath, List<ModulePath>> graph = new LinkedHashMap<>();
    for (Map.Entry<ModulePath, ModuleLocation> entry : nodes.entrySet()) {
      ConcreteGroup group = ctx.server.getRawGroup(entry.getValue());
      List<ModulePath> dependencies = new ArrayList<>();
      if (group != null) {
        for (ConcreteStatement statement : group.statements()) {
          ConcreteNamespaceCommand cmd = statement.command();
          if (cmd != null && cmd.isImport()) {
            ModulePath path = new ModulePath(cmd.module().getPath());
            if (nodes.containsKey(path) && !path.equals(entry.getKey()) && !dependencies.contains(path)) {
              dependencies.add(path);
            }
          }
        }
      }
      graph.put(entry.getKey(), dependencies);
    }

    List<List<ModulePath>> cycles = new ArrayList<>();
    new MapTarjanSCC<>(graph) {
      @Override
      protected void unitFound(ModulePath unit, boolean withLoops) {}

      @Override
      protected void sccFound(List<ModulePath> scc) {
        for (ModulePath module : scc) {
          if (nodes.get(module).getLocationKind() == ModuleLocation.LocationKind.SOURCE) {
            cycles.add(scc);
            return;
          }
        }
      }
    }.order();

    Comparator<ModulePath> comparator = Comparator.comparing(ModulePath::toString);
    int number = 0;
    for (List<ModulePath> cycle : cycles) {
      List<ModulePath> sorted = new ArrayList<>(cycle);
      sorted.sort(comparator);
      Set<ModulePath> inCycle = new HashSet<>(cycle);
      System.out.println("Cycle " + (++number) + " (" + cycle.size() + " modules): " + sorted);
      for (ModulePath module : sorted) {
        ModuleLocation location = nodes.get(module);
        Map<ModulePath, Map<String, Set<String>>> uses = location.getLocationKind() == ModuleLocation.LocationKind.SOURCE ? collectUses(ctx, location) : null;
        List<ModulePath> targets = new ArrayList<>(graph.get(module));
        targets.retainAll(inCycle);
        targets.sort(comparator);
        for (ModulePath target : targets) {
          if (uses == null) {
            System.out.println("  " + module + " -> " + target + ": declared by the extension");
            continue;
          }
          Map<String, Set<String>> targetUses = uses.get(target);
          if (targetUses == null || targetUses.isEmpty()) {
            System.out.println("  " + module + " -> " + target + ": removable, no definition uses it");
            continue;
          }
          System.out.println("  " + module + " -> " + target + ": " + targetUses.size() + " definition(s)");
          for (Map.Entry<String, Set<String>> entry : targetUses.entrySet()) {
            List<String> users = new ArrayList<>(entry.getValue());
            String suffix = users.size() > MAX_CYCLE_USERS ? ", ... (" + (users.size() - MAX_CYCLE_USERS) + " more)" : "";
            System.out.println("    " + entry.getKey() + " <- " + String.join(", ", users.subList(0, Math.min(users.size(), MAX_CYCLE_USERS))) + suffix);
          }
        }
      }
      System.out.println();
    }
    System.out.println(number == 0 ? "No import cycles" : "Import cycles: " + number);
  }

  /**
   * Writes the definitions of the source modules of {@param library} and their dependencies as a JSON array.
   * Every element is {@code {"module", "name", "kind", "dynamic", "deps": ["module:name", ...]}}.
   * Imports of every module are written as {@code {"module", "imports": [...]}}.
   */
  private static void writeDependencyGraph(CommandContext ctx, SourceLibrary library, String file) {
    StringBuilder builder = new StringBuilder("{\"definitions\": [\n");
    boolean first = true;
    List<String> imports = new ArrayList<>();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (!module.getLibraryName().equals(library.getLibraryName())) continue;
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE && module.getLocationKind() != ModuleLocation.LocationKind.GENERATED) continue;
      ConcreteGroup group = ctx.server.getRawGroup(module);
      if (group != null) {
        List<String> moduleImports = new ArrayList<>();
        for (ConcreteStatement statement : group.statements()) {
          ConcreteNamespaceCommand cmd = statement.command();
          if (cmd != null && cmd.isImport()) moduleImports.add(jsonString(String.join(".", cmd.module().getPath())));
        }
        imports.add("{\"module\": " + jsonString(module.getModulePath().toString()) + ", \"generated\": " + (module.getLocationKind() == ModuleLocation.LocationKind.GENERATED) + ", \"imports\": [" + String.join(", ", moduleImports) + "]}");
      }
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE) continue;
      for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
        Concrete.ResolvableDefinition definition = data.definition();
        Set<TCDefReferable> dependencies = new LinkedHashSet<>();
        definition.accept(new CollectDefCallsVisitor(dependencies, true), null);
        Definition typechecked = definition.getData().getTypechecked();
        if (typechecked != null && typechecked.getUsedInstances() != null) {
          dependencies.addAll(typechecked.getUsedInstances());
        }
        List<String> deps = new ArrayList<>();
        for (TCDefReferable dependency : dependencies) {
          ModuleLocation location = dependency.getLocation();
          if (location == null || dependency == definition.getData()) continue;
          deps.add(jsonString(location.getModulePath() + ":" + dependency.getRefLongName()));
        }
        if (!first) builder.append(",\n");
        first = false;
        builder.append("{\"module\": ").append(jsonString(module.getModulePath().toString()))
          .append(", \"name\": ").append(jsonString(definition.getData().getRefLongName().toString()))
          .append(", \"kind\": ").append(jsonString(definition.getData().getKind().toString()))
          .append(", \"dynamic\": ").append(definition.getEnclosingClass() != null)
          .append(", \"deps\": [").append(String.join(", ", deps)).append("]}");
      }
    }
    builder.append("\n], \"modules\": [\n").append(String.join(",\n", imports)).append("\n]}\n");
    try {
      java.nio.file.Files.writeString(java.nio.file.Path.of(file), builder);
      System.out.println("Dependency graph is written to " + file);
    } catch (java.io.IOException e) {
      System.out.println("[ERROR] Cannot write " + file + ": " + e.getMessage());
    }
  }

  private static String jsonString(String str) {
    StringBuilder builder = new StringBuilder("\"");
    for (char c : str.toCharArray()) {
      if (c == '"' || c == '\\') builder.append('\\').append(c);
      else if (c < 0x20) builder.append(String.format("\\u%04x", (int) c));
      else builder.append(c);
    }
    return builder.append('"').toString();
  }

  /**
   * @return a map from imported modules to the map from the used definitions to the definitions of {@param module} that use them.
   */
  private static Map<ModulePath, Map<String, Set<String>>> collectUses(CommandContext ctx, ModuleLocation module) {
    Map<ModulePath, Map<String, Set<String>>> result = new HashMap<>();
    for (DefinitionData data : ctx.server.getResolvedDefinitions(module)) {
      Concrete.ResolvableDefinition definition = data.definition();
      Set<TCDefReferable> dependencies = new LinkedHashSet<>();
      definition.accept(new CollectDefCallsVisitor(dependencies, true), null);
      Definition typechecked = definition.getData().getTypechecked();
      if (typechecked != null && typechecked.getUsedInstances() != null) {
        dependencies.addAll(typechecked.getUsedInstances());
      }
      String user = definition.getData().getRefLongName().toString();
      for (TCDefReferable dependency : dependencies) {
        ModuleLocation location = dependency.getLocation();
        if (location == null || location.getModulePath().equals(module.getModulePath())) continue;
        result.computeIfAbsent(location.getModulePath(), k -> new TreeMap<>())
          .computeIfAbsent(dependency.getRefLongName().toString(), k -> new TreeSet<>())
          .add(user);
      }
    }
    return result;
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
