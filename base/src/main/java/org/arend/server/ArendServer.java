package org.arend.server;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.module.ModuleLocation;
import org.arend.naming.resolving.ResolverListener;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.arend.term.group.ConcreteGroup;
import org.arend.typechecking.computation.CancellationIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public interface ArendServer {
  /**
   * Adds or updates the library dependencies.
   *
   * @param modificationStamp   tracks the version of the library. If the previous modification stamp is
   *                            greater than {@param modificationStamp}, the library will not be updated.
   *                            If {@param modificationStamp} equals -1, then the library is always updated.
   * @param name                the name of the library.
   * @param dependencies        names of library dependencies.
   */
  void updateLibrary(long modificationStamp, @NotNull String name, @NotNull List<String> dependencies);

  /**
   * Removes the library and all the modules from it.
   *
   * @param name    the name of the library.
   */
  void removeLibrary(@NotNull String name);

  /**
   * Adds a read-only module.
   *
   * @param module              the module to be added.
   * @param group               the content of the module.
   */
  void addReadOnlyModule(@NotNull ModuleLocation module, @NotNull ConcreteGroup group);

  /**
   * Adds a module or updates its content if necessary.
   *
   * @param modificationStamp   tracks the version of the module.
   *                            The module is updated only if the previous modification stamp is less than {@param modificationStamp}.
   * @param module              the module to be updated.
   * @param group               the new content of the module.
   */
  void updateModule(long modificationStamp, @NotNull ModuleLocation module, @NotNull Supplier<ConcreteGroup> group);

  /**
   * Removes the specified module.
   */
  void removeModule(@NotNull ModuleLocation module);

  /**
   * Resolves the content of {@param modules} and updates the state of the server.
   */
  void resolveModules(@NotNull List<? extends @NotNull ModuleLocation> modules, @NotNull ErrorReporter errorReporter, @NotNull CancellationIndicator indicator, @NotNull ResolverListener listener);

  /**
   * @return the set of registered libraries.
   */
  @NotNull Set<String> getLibraries();

  /**
   * Returns the content of the given module.
   */
  @Nullable ConcreteGroup getGroup(@NotNull ModuleLocation module);

  /**
   * @return either the data of some element, or {@link ModuleLocation}.
   */
  @Nullable AbstractReferable resolveReference(@NotNull AbstractReference reference);

  /**
   * @return the list of errors corresponding to the given module.
   */
  @NotNull List<GeneralError> getErrorList(@NotNull ModuleLocation module);

  /**
   * @return the collection of modules with errors.
   */
  @NotNull Collection<ModuleLocation> getModulesWithErrors();
}
