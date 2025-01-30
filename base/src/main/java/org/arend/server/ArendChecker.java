package org.arend.server;

import org.arend.ext.error.ErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.naming.resolving.ResolverListener;
import org.arend.typechecking.computation.CancellationIndicator;
import org.arend.util.FullName;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Stores a set of modules that can be resolved and typechecked.
 */
public interface ArendChecker {
  /**
   * @return the set of dependencies of stored modules.
   */
  @NotNull Set<ModuleLocation> getDependencies(@NotNull CancellationIndicator indicator);

  /**
   * Resolves stored modules.
   */
  void resolveModules(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener);

  /**
   * Resolves stored modules together with their dependencies if they are not already resolved.
   */
  void resolveAll(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener);

  /**
   * Typechecks stored modules.
   * This method also invokes {@link #resolveAll}
   */
  void typecheckModules(@NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator);

  /**
   * Typechecks the specified definition.
   * This method also invokes {@link #resolveAll}
   */
  void typecheckDefinition(@NotNull FullName definition, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator);
}
