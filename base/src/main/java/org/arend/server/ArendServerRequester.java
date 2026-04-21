package org.arend.server;

import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public interface ArendServerRequester extends ArendServerResolveListener {
  /**
   * Request an invocation of {@link ArendServer#updateModule}.
   *
   * @param server    server to be updated.
   * @param module    module to be updated.
   */
  default void requestModuleUpdate(@NotNull ArendServer server, @NotNull ModuleLocation module) {}

  default void setupGeneratedModule(@NotNull ModuleLocation module, @NotNull ConcreteGroup group) {}

  /**
   * Returns the list of files and directories in the given library and directory.
   *
   * @param libraryName   a library name.
   * @param inTests       whether search in the test or the source directory.
   * @param prefix        a path to the directory to search.
   * @return the list of file names or {@code null} if cannot determine the list of files.
   */
  default @Nullable List<String> getFiles(@NotNull String libraryName, boolean inTests, @NotNull List<String> prefix) {
    return null;
  }

  /**
   * Parses the source file for the given module and returns the raw ConcreteGroup,
   * WITHOUT updating the server. Used to recover inline meta definitions from source
   * for modules loaded from binary cache.
   *
   * @return the source-parsed group, or null if the source is unavailable.
   */
  default @Nullable ConcreteGroup getSourceGroup(@NotNull ModuleLocation module) {
    return null;
  }

  default <T> T runUnderReadLock(@NotNull Supplier<T> supplier) {
    return supplier.get();
  }

  default @NotNull List<Referable> fixModuleReferences(@NotNull List<Referable> referables) {
    return referables;
  }

  default void addReference(@NotNull ModuleLocation module, @NotNull AbstractReferable referable, @NotNull TCDefReferable tcReferable) {}

  default void addModuleDependency(@NotNull ModuleLocation module, @NotNull ModuleLocation dependency) {}

  ArendServerRequester TRIVIAL = new ArendServerRequester() {};
}
