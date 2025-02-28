package org.arend.server;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.module.FullName;
import org.arend.typechecking.visitor.ArendCheckerFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Stores a set of modules that can be resolved and typechecked.
 */
public interface ArendChecker {
  /**
   * Resolves stored modules.
   */
  void resolveModules(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter);

  /**
   * Resolves stored modules together with their dependencies if they are not already resolved.
   */
  void resolveAll(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter);

  /**
   * Typechecks given definitions.
   *
   * @param definitions     definitions to be typechecked; if {@code null}, typechecks all definitions in stored modules.
   * @param errorReporter   reports not found definitions.
   * @return the number of definitions that were typechecked.
   */
  int typecheck(@Nullable List<FullName> definitions, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter);

  /**
   * Typechecks the given definition with a custom checker.
   *
   * @param definition      the definition to be typechecked.
   * @param checkerFactory  a factory for creating Arend checkers.
   *                        If it is {@code null}, the default factory will be used.
   *                        If it is not {@code null}, then the definitions will be re-checked even if they were already checked.
   *                        Additionally, in this case, the state of the server won't be changed.
   *                        Note that dependencies of the definitions are always checked with the default factory.
   * @param renamed         If not {@code null}, will be populated with renamed referables.
   * @param errorReporter   reports not found definitions.
   * @return the number of definitions that were typechecked.
   */
  int typecheck(@NotNull FullName definition, @NotNull ArendCheckerFactory checkerFactory, @Nullable Map<TCDefReferable, TCDefReferable> renamed, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter);

  // TODO[server2]: Delete this. Instead, typecheck extension definitions as needed.
  default void typecheckExtensionDefinition(@NotNull FullName definition) {
    typecheck(Collections.singletonList(definition), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE, ProgressReporter.empty());
  }

  ArendChecker EMPTY = new ArendChecker() {
    @Override
    public void resolveModules(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter) {}

    @Override
    public void resolveAll(@NotNull CancellationIndicator indicator, @NotNull ProgressReporter<ModuleLocation> progressReporter) {}

    @Override
    public int typecheck(@Nullable List<FullName> definitions, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter) {
      return 0;
    }

    @Override
    public int typecheck(@NotNull FullName definition, @NotNull ArendCheckerFactory checkerFactory, @Nullable Map<TCDefReferable, TCDefReferable> renamed, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ProgressReporter<List<? extends Concrete.ResolvableDefinition>> progressReporter) {
      return 0;
    }
  };
}
