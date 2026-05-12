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
import org.arend.frontend.cli.CommandContext;
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
import org.arend.frontend.symbol.ReferenceResolveSuggest;
import org.arend.frontend.symbol.SignatureFileWriter;
import org.arend.frontend.symbol.SymbolSearch;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.prelude.Prelude;
import org.arend.server.ArendServer;
import org.arend.server.ProgressReporter;
import org.arend.server.impl.DefinitionData;
import org.arend.source.PersistableBinarySource;
import org.arend.term.concrete.Concrete;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.typechecking.doubleChecker.CoreModuleChecker;
import org.arend.typechecking.order.MapTarjanSCC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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

    TimedProgressReporter timedProgressReporter = cmdLine.hasOption(SHOW_TIMES) ? new TimedProgressReporter() : null;
    ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter =
        timedProgressReporter != null ? timedProgressReporter : ProgressReporter.empty();

    if (aiMode) {
      runAiNameResolve(ctx);
    }

    // Pre-load binary caches (unless --recompile is set)
    if (!ctx.recompile) {
      // Typecheck Prelude first — binary cache loading needs Prelude definitions to be available
      ctx.server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
      if (ctx.requestedModules.isEmpty()) {
        // Whole-library typechecking: pre-load every module of each requested library.
        for (SourceLibrary library : ctx.requestedLibraries) {
          List<ModuleLocation> allModules = library.findModules(false).stream()
              .map(mp -> new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
              .toList();
          if (!allModules.isEmpty()) {
            ctx.server.getCheckerFor(allModules).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
            ctx.requester.loadBinaryCache(library, ctx.server);
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
          ctx.server.getCheckerFor(targets).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
          for (SourceLibrary library : ctx.requestedLibraries) {
            ctx.requester.loadBinaryCache(library, ctx.server);
          }
        }
      }
      reportCachedGoals(ctx, ctx.requester.getBinaryCacheLoaded());
    }

    if (ctx.requestedModules.isEmpty()) {
      for (SourceLibrary library : ctx.requestedLibraries) {
        System.out.println();
        System.out.println("--- Typechecking " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        for (ModulePath modulePath : library.findModules(false)) {
          ctx.server.getCheckerFor(Collections.singletonList(
                  new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath)))
              .typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);
        }

        time = System.currentTimeMillis() - time;

        int numWithErrors = 0;
        int numWithGoals = 0;
        for (ModuleLocation module : ctx.server.getModules()) {
          if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
              && module.getLibraryName().equals(library.getLibraryName())) {
            GeneralError.Level result = ctx.moduleResults.get(module);
            reportTypeCheckResult(module.getModulePath(), result);
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
          System.out.println();
          System.out.println("--- Checking " + library.getLibraryName() + " ---");
          time = System.currentTimeMillis();

          try {
            CoreModuleChecker checker = new CoreModuleChecker(ctx.errorReporter);
            for (ModuleLocation module : ctx.server.getModules()) {
              if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
                  && module.getLibraryName().equals(library.getLibraryName())) {
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

        persistLibrary(ctx, library);
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

          ctx.server.getCheckerFor(Collections.singletonList(module))
              .typecheck(Collections.singletonList(fullName), ctx.errorReporter,
                  UnstoppableCancellationIndicator.INSTANCE, progressReporter);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
        } else {
          System.out.println();
          System.out.println("--- Typechecking " + module + " ---");
          long time = System.currentTimeMillis();

          ctx.server.getCheckerFor(Collections.singletonList(module))
              .typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");

          if (ctx.doubleCheck) {
            System.out.println();
            System.out.println("--- Checking " + module + " ---");
            time = System.currentTimeMillis();

            try {
              CoreModuleChecker checker = new CoreModuleChecker(ctx.errorReporter);
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
      for (SourceLibrary library : ctx.requestedLibraries) {
        persistLibrary(ctx, library);
      }
    }

    if (aiMode) {
      finalizeAi(ctx);
    }

    printDefinitions(ctx.server, cmdLine.getOptionValue("p"), ctx);

    if (cmdLine.hasOption("t")) {
      for (SourceLibrary library : ctx.requestedLibraries) {
        System.out.println();
        System.out.println("--- Running tests in " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        for (ModulePath modulePath : library.findModules(true)) {
          ctx.server.getCheckerFor(Collections.singletonList(
                  new ModuleLocation(library.getLibraryName(), ModuleLocation.LocationKind.TEST, modulePath)))
              .typecheck(UnstoppableCancellationIndicator.INSTANCE, progressReporter);
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

        System.out.println("Tests completed: " + total[0] + ", Failed: " + failed[0]);
        System.out.println("--- Done (" + TimedProgressReporter.timeToString(time) + ") ---");

        if (ctx.doubleCheck) {
          System.out.println();
          System.out.println("--- Checking tests in " + library.getLibraryName() + " ---");
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

  // ───────── AI pipeline phases ─────────

  /**
   * Step 1 of the -ai pipeline: resolve every module in scope, buffer the resulting name-
   * resolution errors, hand them to {@link ReferenceResolveSuggest}, and print suggestions.
   * Never rewrites sources.
   */
  private static void runAiNameResolve(CommandContext ctx) {
    System.out.println();
    System.out.println("--- AI: resolve + suggest ---");
    long t = System.currentTimeMillis();
    ctx.bufferErrors = true;
    ctx.bufferedErrors.clear();
    try {
      if (ctx.requestedModules.isEmpty()) {
        for (SourceLibrary lib : ctx.requestedLibraries) {
          List<ModuleLocation> mods = lib.findModules(false).stream()
              .map(mp -> new ModuleLocation(lib.getLibraryName(), ModuleLocation.LocationKind.SOURCE, mp))
              .toList();
          if (!mods.isEmpty()) {
            ctx.server.getCheckerFor(mods).resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
          }
        }
      } else {
        for (Pair<ModulePath, LongName> requested : ctx.requestedModules) {
          ModuleLocation module = ctx.server.findModule(requested.proj1, null, true, false);
          if (module == null) {
            ctx.systemErrErrorReporter.report(new ModuleNotFoundError(requested.proj1));
            continue;
          }
          ctx.server.getCheckerFor(Collections.singletonList(module))
              .resolveAll(UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
        }
      }
    } finally {
      ctx.bufferErrors = false;
    }

    ReferenceResolveSuggest.Result result =
        ReferenceResolveSuggest.process(ctx.server, ctx.libraryManager, ctx.bufferedErrors, ctx.requestedLibraries);

    for (GeneralError error : result.errorsToPrint()) ctx.printError(error);
    for (String block : result.suggestionBlocks()) { System.out.println(block); System.out.flush(); }
    for (String warning : result.warnings()) { System.out.println(warning); System.out.flush(); }

    // Name-resolution errors recorded against failedDefinitions are reset so the typecheck
    // phase tracks only real failures; unresolved refs will resurface as typecheck errors.
    ctx.failedDefinitions.clear();
    ctx.bufferedErrors.clear();
    System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - t) + ") ---");
  }

  /**
   * Steps 3-4 of the -ai pipeline: write the .sig mirror for verified definitions only
   * (per-def filter via {@link CommandContext#failedDefinitions}) and refresh the binary
   * symbol index used by -ss / -fu / -ch / -sc.
   */
  private static void finalizeAi(CommandContext ctx) {
    Set<ModulePath> only = ctx.requestedModules.isEmpty() ? null : collectModulePaths(ctx.requestedModules);
    System.out.println();
    System.out.println("--- AI: .sig + reindex ---");
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
      System.out.println(line);
    }
    SymbolSearch.reindex(ctx.requestedLibraries, ctx.libraryManager, ctx.server, null);
  }

  private static Set<ModulePath> collectModulePaths(Set<Pair<ModulePath, LongName>> requestedModules) {
    Set<ModulePath> result = new HashSet<>();
    for (Pair<ModulePath, LongName> p : requestedModules) result.add(p.proj1);
    return result;
  }

  // ───────── reporting helpers ─────────

  /**
   * Scans definitions loaded from binary cache for goals ({@code {?}}) and reports them.
   * The goal flag ({@code isGoal}) is preserved in .arc files, so we can detect goals
   * without re-typechecking.
   */
  private static void reportCachedGoals(CommandContext ctx, Set<ModuleLocation> cachedModules) {
    for (ModuleLocation module : cachedModules) {
      ConcreteGroup group = ctx.server.getRawGroup(module);
      if (group == null) continue;
      reportGoalsInGroup(ctx, group, module);
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

  private static void persistLibrary(CommandContext ctx, SourceLibrary library) {
    if (!library.supportsPersisting()) return;
    int persisted = 0;
    int skipped = 0;
    int failed = 0;
    Set<ModuleLocation> skipModules = ctx.requester.getBinaryCacheLoaded();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
          && module.getLibraryName().equals(library.getLibraryName())) {
        if (skipModules.contains(module)) {
          skipped++;
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
    if (persisted > 0 || failed > 0) {
      System.out.println("[INFO] Persisted " + persisted + " module(s)"
          + (failed > 0 ? ", " + failed + " failed" : "")
          + (skipped > 0 ? " (" + skipped + " up-to-date)" : ""));
    }
  }

  private static void reportTypeCheckResult(ModulePath modulePath, GeneralError.Level result) {
    System.out.println("[" + resultChar(result) + "]" + " " + modulePath);
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
