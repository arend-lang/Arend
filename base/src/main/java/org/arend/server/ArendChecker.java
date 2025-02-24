package org.arend.server;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.module.FullName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

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
  };
}
