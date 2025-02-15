package org.arend.server;

import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.module.ModuleLocation;
import org.arend.naming.reference.LocatedReferable;
import org.arend.naming.reference.Referable;
import org.arend.naming.resolving.typing.TypingInfo;
import org.arend.naming.scope.Scope;
import org.arend.server.impl.GroupData;
import org.arend.term.abs.AbstractReference;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public interface ArendServer {
  /**
   * Adds or updates a library.
   */
  void updateLibrary(@NotNull ArendLibrary library, @NotNull ErrorReporter errorReporter);

  /**
   * Removes the library and all the modules from it.
   *
   * @param name    the name of the library.
   */
  void removeLibrary(@NotNull String name);

  /**
   * Unloads loaded libraries.
   * It is necessary to invoke this method before updating libraries in order for library extensions to be updated.
   *
   * @param onlyInternal    if {@code true}, updates only internal libraries; otherwise, updates all libraries.
   */
  void unloadLibraries(boolean onlyInternal);

  /**
   * @return a library with the given name.
   */
  @Nullable ArendLibrary getLibrary(@NotNull String name);

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
   * @return a checker that can be used to resolve and typecheck specified modules.
   */
  @NotNull ArendChecker getCheckerFor(@NotNull List<? extends @NotNull ModuleLocation> modules);

  /**
   * @return the set of registered modules.
   */
  @NotNull Collection<? extends ModuleLocation> getModules();

  /**
   * @return the set of registered libraries.
   */
  @NotNull Set<String> getLibraries();

  /**
   * @return The typing info for the whole project.
   */
  @NotNull TypingInfo getTypingInfo();

  /**
   * @return the content of the given module.
   */
  @Nullable ConcreteGroup getRawGroup(@NotNull ModuleLocation module);

  /**
   * @return the group data of the specified module.
   */
  @Nullable GroupData getGroupData(@NotNull ModuleLocation module);

  /**
   * @return errors grouped by modules.
   */
  @NotNull Map<ModuleLocation, List<GeneralError>> getErrorMap();

  /**
   * @return typechecking errors in the specified module.
   */
  @NotNull List<GeneralError> getTypecheckingErrors(@NotNull ModuleLocation module);

  default boolean hasErrors() {
    return !getErrorMap().isEmpty();
  }

  /**
   * @return list of possible completion variants for the given reference.
   */
  @NotNull List<Referable> getCompletionVariants(@Nullable ConcreteGroup group, @NotNull AbstractReference reference);

  /**
   * @return the scope corresponding to the given referable.
   */
  @Nullable Scope getReferableScope(@NotNull LocatedReferable referable);
}
