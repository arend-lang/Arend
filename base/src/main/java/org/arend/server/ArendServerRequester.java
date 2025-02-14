package org.arend.server;

import org.arend.module.ModuleLocation;
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
  void requestModuleUpdate(@NotNull ArendServer server, @NotNull ModuleLocation module);

  /**
   * Returns the list of files and directories in the given library and directory.
   *
   * @param libraryName   a library name.
   * @param inTests       whether search in the test or the source directory.
   * @param prefix        a path to the directory to search.
   * @return the list of file names or {@code null} if cannot determine the list of files.
   */
  @Nullable List<String> getFiles(@NotNull String libraryName, boolean inTests, @NotNull List<String> prefix);

  void runUnderReadLock(@NotNull Runnable runnable);

  void addReference(@NotNull ModuleLocation module, @NotNull AbstractReference reference, @NotNull Referable referable);

  void addReference(@NotNull ModuleLocation module, @NotNull AbstractReferable referable, @NotNull TCDefReferable tcReferable);

  void addModuleDependency(@NotNull ModuleLocation module, @NotNull ModuleLocation dependency);
}
