package org.arend.server;

import org.apache.commons.cli.CommandLine;
import org.arend.cli.ArendOptions;
import org.arend.core.definition.Definition;
import org.arend.core.expr.visitor.SizeExpressionVisitor;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.module.FullName;
import org.arend.ext.module.LongName;
import org.arend.ext.module.ModuleLocation;
import org.arend.ext.module.ModulePath;
import org.arend.ext.prettyprinting.PrettyPrinterFlag;
import org.arend.ext.util.Pair;
import org.arend.module.error.DefinitionNotFoundError;
import org.arend.module.error.ModuleNotFoundError;
import org.arend.naming.reference.GlobalReferable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.naming.scope.EmptyScope;
import org.arend.query.TargetResolver;
import org.arend.server.impl.DefinitionData;
import org.arend.term.group.ConcreteGroup;
import org.arend.term.group.ConcreteNamespaceCommand;
import org.arend.term.group.ConcreteStatement;
import org.arend.term.prettyprint.PrettyPrinterConfigWithRenamer;
import org.arend.term.prettyprint.ToAbstractVisitor;
import org.arend.typechecking.error.local.GoalError;
import org.arend.typechecking.order.MapTarjanSCC;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.arend.ext.prettyprinting.PrettyPrinterConfig.DEFAULT;

/**
 * What {@code arend} prints about a typechecked library on request: {@code -p}, {@code --show-sizes},
 * {@code --show-modules[-with-instances]} and the test count of {@code -t}. Read off the server
 * alone, so that the CLI and the IntelliJ plugin's daemon print the same.
 */
public final class TypecheckReports {
  private TypecheckReports() {}

  /** A diagnostic as {@code arend} prints it: a goal shows the instances its local fields come from. */
  public static @NotNull String render(@NotNull GeneralError error) {
    PrettyPrinterConfigWithRenamer config = new PrettyPrinterConfigWithRenamer(EmptyScope.INSTANCE);
    if (error instanceof GoalError) {
      config.expressionFlags = EnumSet.of(PrettyPrinterFlag.SHOW_LOCAL_FIELD_INSTANCE);
    }
    return error.getDoc(config).toString();
  }

  /** {@code MODULE[:DEF]} as a module and, if given, a definition in it; null, reported, when malformed. */
  public static @Nullable Pair<ModulePath, LongName> parseFullName(@NotNull String fullName, @NotNull ErrorReporter errorReporter) {
    LongName longName = null;
    int index = fullName.indexOf(':');
    if (index >= 0) {
      longName = LongName.fromString(fullName.substring(index + 1));
      if (!FileUtils.isCorrectDefinitionName(longName)) {
        errorReporter.report(FileUtils.illegalDefinitionName(longName.toString()));
        return null;
      }
      fullName = fullName.substring(0, index);
    }
    ModulePath modulePath = ModulePath.fromString(fullName);
    if (!FileUtils.isCorrectModulePath(modulePath)) {
      errorReporter.report(FileUtils.illegalModuleName(modulePath.toString()));
      return null;
    }
    return new Pair<>(modulePath, longName);
  }

  /**
   * Whether {@code module} declares {@code name}, a definition or a field or constructor of one;
   * when it does not, says so to {@code errorReporter}. Typechecking a name the module does not
   * declare checks nothing, and so would succeed.
   */
  public static boolean checkDefinitionExists(@NotNull ArendServer server, @NotNull ModuleLocation module,
                                              @NotNull LongName name, @NotNull ErrorReporter errorReporter) {
    ConcreteGroup group = server.getRawGroup(module);
    if (group != null && TargetResolver.walkLongName(group, name) != null) return true;
    errorReporter.report(new DefinitionNotFoundError(new FullName(module, name)));
    return false;
  }

  /** {@code -p}: the elaborated form of every definition of a module, or of the one it names. */
  public static void printDefinitions(@NotNull ArendServer server, @NotNull String printString,
                                      @NotNull ErrorReporter errorReporter, @NotNull PrintStream out) {
    Pair<ModulePath, LongName> pair = parseFullName(printString, errorReporter);
    if (pair == null) return;
    ModuleLocation module = server.findModule(pair.proj1, null, false, false);
    if (module == null) {
      errorReporter.report(new ModuleNotFoundError(pair.proj1));
      return;
    }
    boolean found = pair.proj2 == null;
    for (DefinitionData definitionData : server.getResolvedDefinitions(module)) {
      if (pair.proj2 == null || definitionData.definition().getData().getRefLongName().equals(pair.proj2)) {
        Definition definition = definitionData.definition().getData().getTypechecked();
        if (definition != null) {
          out.println();
          StringBuilder builder = new StringBuilder();
          ToAbstractVisitor.convert(definition, DEFAULT).prettyPrint(builder, DEFAULT);
          out.println(builder);
        }

        if (pair.proj2 != null) {
          found = true;
          break;
        }
      }
    }
    if (!found) {
      errorReporter.report(new DefinitionNotFoundError(new FullName(module, pair.proj2)));
    }
  }

  /** {@code --show-sizes}: the core-term size of every typechecked definition of {@code library}, largest first. */
  private static void showSizes(@NotNull ArendServer server, @NotNull String library, @NotNull PrintStream out) {
    Map<Definition, Integer> sizes = new HashMap<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library)) {
        for (DefinitionData definitionData : server.getResolvedDefinitions(module)) {
          Definition definition = definitionData.definition().getData().getTypechecked();
          if (definition != null) {
            sizes.put(definition, SizeExpressionVisitor.getSize(definition));
          }
        }
      }
    }

    out.println();
    // Largest first; a tie by name, or the order would be the hash map's.
    sizes.entrySet().stream()
        .sorted(Comparator.comparing((Map.Entry<Definition, Integer> entry) -> -entry.getValue())
            .thenComparing(entry -> entry.getKey().getReferable().getRefLongName().toString()))
        .forEach(entry -> out.println(entry.getKey().getReferable().getRefLongName() + ": " + entry.getValue()));
  }

  /** {@code --show-sizes}, {@code --show-modules} and {@code --show-modules-with-instances} for {@code library}, as {@code cmdLine} asks. */
  public static void showRequested(@NotNull ArendServer server, @NotNull String library, @NotNull CommandLine cmdLine,
                                   @NotNull PrintStream out) {
    if (cmdLine.hasOption(ArendOptions.SHOW_SIZES)) showSizes(server, library, out);
    if (cmdLine.hasOption(ArendOptions.SHOW_MODULES)) showModules(server, library, true, out);
    if (cmdLine.hasOption(ArendOptions.SHOW_MODULES_WITH_INSTANCES)) showModules(server, library, false, out);
  }

  /** The banner {@code -t} prints before running the tests of {@code library}; returns when they started. */
  public static long testsStarted(@NotNull String library, @NotNull PrintStream out) {
    out.println();
    out.println("--- Running tests in " + library + " ---");
    return System.currentTimeMillis();
  }

  /** What {@code -t} prints once the tests of {@code library}, started at {@code startedAt}, have run. */
  public static void testsFinished(@NotNull ArendServer server, @NotNull String library, long startedAt, @NotNull PrintStream out) {
    // A test is a typecheckable definition of a test module.
    int total = 0, failed = 0;
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() != ModuleLocation.LocationKind.TEST || !module.getLibraryName().equals(library)) continue;
      ConcreteGroup group = server.getRawGroup(module);
      if (group == null) continue;
      for (ConcreteStatement statement : group.statements()) {
        if (statement.group() != null && statement.group().referable() instanceof TCDefReferable referable) {
          Definition definition = referable.getTypechecked();
          if (definition != null || referable.getKind().isTypecheckable()) {
            total++;
            if (definition == null || definition.status() != Definition.TypeCheckingStatus.NO_ERRORS) {
              failed++;
            }
          }
        }
      }
    }
    out.println("Tests completed: " + total + ", Failed: " + failed);
    out.println("--- Done (" + TimedProgressReporter.timeToString(System.currentTimeMillis() - startedAt) + ") ---");
  }

  /**
   * {@code --show-modules}, or with {@code allModules} unset {@code --show-modules-with-instances}:
   * the import graph of {@code library} in topological order, a cycle as one entry.
   */
  private static void showModules(@NotNull ArendServer server, @NotNull String library, boolean allModules, @NotNull PrintStream out) {
    out.println();
    out.println(allModules ? "Modules cycles:" : "Modules with instances cycles:");
    Map<ModulePath, List<ModulePath>> map = new HashMap<>();
    for (ModuleLocation module : server.getModules()) {
      if (module.getLocationKind() == ModuleLocation.LocationKind.SOURCE && module.getLibraryName().equals(library)) {
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
        out.println("[" + unit + "]");
      }

      @Override
      protected void sccFound(List<ModulePath> scc) {
        out.println(scc);
      }
    }.order();
  }
}
