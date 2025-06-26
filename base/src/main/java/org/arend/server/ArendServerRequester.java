package org.arend.server;

import org.arend.ext.module.ModuleLocation;
import org.arend.naming.reference.Referable;
import org.arend.naming.reference.TCDefReferable;
import org.arend.term.abs.AbstractReferable;
import org.arend.term.abs.AbstractReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ArendServerRequester {
  /**
   * Request an invocation of {@link ArendServer#updateModule}.
   *
   * @param server    server to be updated.
   * @param module    module to be updated.
   */
  default void requestModuleUpdate(@NotNull ArendServer server, @NotNull ModuleLocation module) {}

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

  default void runUnderReadLock(@NotNull Runnable runnable) {
    runnable.run();
  }

  default void addReference(@NotNull ModuleLocation module, @NotNull AbstractReference reference, @NotNull Referable referable) {}

  default void addReference(@NotNull ModuleLocation module, @NotNull AbstractReferable referable, @NotNull TCDefReferable tcReferable) {}

  default void addModuleDependency(@NotNull ModuleLocation module, @NotNull ModuleLocation dependency) {}

  ArendServerRequester TRIVIAL = new ArendServerRequester() {};
}
