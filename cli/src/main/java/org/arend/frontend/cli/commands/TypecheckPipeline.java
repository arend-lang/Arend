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
import org.arend.frontend.library.LibraryManager;
import org.arend.frontend.library.SourceLibrary;
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
 * Default typecheck pipeline plus its modifiers: binary-cache preload, the
 * typecheck loop, {@code -p print}, {@code -t tests},
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

    ctx.cancellation.checkCanceled();

    // Pre-load binary caches (unless --recompile is set)
    if (!ctx.recompile) {
      // Typecheck Prelude first — binary cache loading needs Prelude definitions to be available
      ctx.server.getCheckerFor(Collections.singletonList(Prelude.MODULE_LOCATION))
          .typecheck(ctx.cancellation, ProgressReporter.empty());
      Set<String> requestedLibraryNames = requestedLibraryNames(ctx.requestedLibraries);
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
        // Targeted typechecking: seed resolveAll with just the requested modules so only
        // their transitive import cone is raw-loaded.
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
        for (SourceLibrary library : dependencyFirstLibraries(ctx.libraryManager, ctx.requestedLibraries)) {
          ctx.requester.loadBinaryCache(library, ctx.server);
          if (!requestedLibraryNames.contains(library.getLibraryName())) {
            typecheckUncachedDependencyModules(ctx, library);
          }
        }
      }
      reportCachedGoals(ctx, ctx.requester.getBinaryCacheLoaded(), requestedLibraryNames);
      // Re-report errors that were detected on a previous typecheck pass and
      // whose modules are still in memory but won't be re-typechecked this run.
      reportInMemoryErrors(ctx, requestedLibraryNames);
    }

    if (ctx.requestedModules.isEmpty()) {
      for (SourceLibrary library : ctx.requestedLibraries) {
        ctx.cancellation.checkCanceled();
        System.out.println();
        System.out.println("--- Typechecking " + library.getLibraryName() + " ---");
        long time = System.currentTimeMillis();

        for (ModulePath modulePath : library.findModules(false)) {
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
              && module.getLibraryName().equals(library.getLibraryName())) {
            GeneralError.Level result = ctx.moduleResults.get(module);
            reportTypeCheckResult(module, result);
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

        if (ctx.serialize) persistLibrary(ctx, library);
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
          System.out.println();
          System.out.println("--- Typechecking " + fullName + " ---");
          long time = System.currentTimeMillis();

          ctx.server.getCheckerFor(Collections.singletonList(module))
              .typecheck(Collections.singletonList(fullName), ctx.errorReporter,
                  ctx.cancellation, progressReporter);

          System.out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - time) + ") ---");
        } else {
          System.out.println();
          System.out.println("--- Typechecking " + module + " ---");
          long time = System.currentTimeMillis();

          ctx.server.getCheckerFor(Collections.singletonList(module))
              .typecheck(ctx.cancellation, progressReporter);

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
        if (ctx.serialize) persistLibrary(ctx, library);
      }
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

  // ───────── reporting helpers ─────────

  /**
   * Scans definitions loaded from binary cache for goals ({@code {?}}) and reports them.
   * The goal flag ({@code isGoal}) is preserved in .arc files, so we can detect goals
   * without re-typechecking.
   */
  private static void reportCachedGoals(CommandContext ctx, Set<ModuleLocation> cachedModules, Set<String> requestedLibraryNames) {
    for (ModuleLocation module : cachedModules) {
      if (!requestedLibraryNames.contains(module.getLibraryName())) continue;
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
    int skippedWithErrors = 0;
    Set<ModuleLocation> skipModules = ctx.requester.getBinaryCacheLoaded();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE
          && module.getLibraryName().equals(library.getLibraryName())) {
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
      System.out.println(line);
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

  /**
   * Re-reports typechecking errors stored in the server's {@code ErrorService} for
   * source modules. Without this step, a daemon that bootstrapped with HAS_ERRORS
   * modules would silently drop the error reports on the second and later client
   * requests: persist now skips those modules, load doesn't see them in the binary
   * cache, and the typechecker skips already-typechecked defs — so the per-request
   * {@code moduleResults} map never gets an ERROR entry. Walking the ErrorService
   * here restores the per-invocation "Number of modules with errors" summary that
   * the old re-typecheck cycle incidentally provided.
   */
  private static void reportInMemoryErrors(CommandContext ctx, Set<String> requestedLibraryNames) {
    if (!(ctx.server instanceof org.arend.server.impl.ArendServerImpl impl)) return;
    org.arend.server.impl.ErrorService errorService = impl.getErrorService();
    for (ModuleLocation module : ctx.server.getModules()) {
      if (module.getLocationKind() != ModuleLocation.LocationKind.SOURCE || !requestedLibraryNames.contains(module.getLibraryName())) continue;
      for (GeneralError error : errorService.getTypecheckingErrors(module)) {
        ctx.errorReporter.report(error);
      }
    }
  }

  private static void reportTypeCheckResult(ModuleLocation module, GeneralError.Level result) {
    System.out.println("[" + resultChar(result) + "]" + " " + module.getModulePath());
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
