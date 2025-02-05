package org.arend.server;

import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.module.ModuleLocation;
import org.arend.naming.resolving.ResolverListener;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.typechecking.computation.UnstoppableCancellationIndicator;
import org.arend.util.FullName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Stores a set of modules that can be resolved and typechecked.
 */
public interface ArendChecker {
  /**
   * @return the set of dependencies of stored modules or {@code null} if the computation was interrupted.
   */
  @Nullable Set<ModuleLocation> getDependencies(@NotNull CancellationIndicator indicator);

  /**
   * Resolves stored modules.
   */
  void resolveModules(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener);

  /**
   * Resolves stored modules together with their dependencies if they are not already resolved.
   */
  void resolveAll(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener);

  /**
   * Sorts definitions in stored modules before typechecking.
   *
   * @return the number of definitions that will be typechecked.
   */
  int prepareTypechecking();

  /**
   * Sorts given definitions before typechecking.
   *
   * @param definitions     definitions to be typechecked
   * @param errorReporter   reports {@link DefinitionNotFoundError} and, possibly, resolver errors.
   * @return the number of definitions that will be typechecked.
   */
  int prepareTypechecking(@NotNull List<FullName> definitions, @NotNull ErrorReporter errorReporter);

  /**
   * Typechecks prepared definitions.
   */
  void typecheckPrepared(@NotNull CancellationIndicator indicator, @NotNull TypecheckingListener listener);

  // TODO[server2]: Delete this. Instead, typecheck extension definitions as needed.
  default void typecheckExtensionDefinition(@NotNull FullName definition) {
    typecheck(Collections.singletonList(definition), DummyErrorReporter.INSTANCE, UnstoppableCancellationIndicator.INSTANCE);
  }

  class DefinitionNotFoundError extends GeneralError {
    public final FullName definition;

    public DefinitionNotFoundError(@NotNull FullName definition) {
      super(Level.ERROR, "Definition " + definition.longName + " is not found in " + definition.module);
      this.definition = definition;
    }
  }

  interface TypecheckingListener {
    void definitionsTypechecked(@NotNull List<? extends Concrete.ResolvableDefinition> definitions);
    TypecheckingListener EMPTY = definitions -> {};
  }

  default void typecheck(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator) {
    resolveAll(errorReporter, indicator, ResolverListener.EMPTY);
    prepareTypechecking();
    typecheckPrepared(indicator, TypecheckingListener.EMPTY);
  }

  default void typecheck(@NotNull List<FullName> definitions, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator) {
    resolveAll(errorReporter, indicator, ResolverListener.EMPTY);
    prepareTypechecking(definitions, errorReporter);
    typecheckPrepared(indicator, TypecheckingListener.EMPTY);
  }

  ArendChecker EMPTY = new ArendChecker() {
    @Override
    public @NotNull Set<ModuleLocation> getDependencies(@NotNull CancellationIndicator indicator) {
      return Collections.emptySet();
    }

    @Override
    public void resolveModules(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {}

    @Override
    public void resolveAll(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener) {}

    @Override
    public int prepareTypechecking() {
      return 0;
    }

    @Override
    public int prepareTypechecking(@NotNull List<FullName> definitions, @NotNull ErrorReporter errorReporter) {
      return 0;
    }

    @Override
    public void typecheckPrepared(@NotNull CancellationIndicator indicator, @NotNull TypecheckingListener listener) {}
  };
}
