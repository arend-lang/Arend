package org.arend.query;

import org.arend.ext.module.ModulePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * A library as the query tools see it: its name, its modules and where they live. The CLI's
 * libraries and the IntelliJ plugin's each provide one, so the tools answer the same either way.
 */
public interface QueryLibrary {
  @NotNull String getLibraryName();

  @NotNull List<ModulePath> findModules(boolean inTests);

  /** The directory holding the library's sources, or {@code null} when they are not files (a zip). */
  default @Nullable Path getSourceBasePath() {
    return null;
  }

  /** The directory holding the library's binary caches, or {@code null} when it has none. */
  default @Nullable Path getBinaryBasePath() {
    return null;
  }

  /** The text of a module's source, or {@code null} when it has none or it cannot be read. */
  @Nullable String readModuleText(@NotNull ModulePath modulePath, boolean inTests);
}
