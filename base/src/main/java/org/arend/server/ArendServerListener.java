package org.arend.server;

import org.arend.ext.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Listener for ArendServer state changes.
 * <p>
 * Threading: callbacks may be invoked from server threads. Clients updating UI must switch to EDT.
 */
public interface ArendServerListener {
  /** Called when a library has been added or updated. */
  default void onLibraryUpdated(@NotNull String libraryName) {}

  /** Called when a library has been removed. */
  default void onLibraryRemoved(@NotNull String libraryName) {}

  /** Called when libraries are unloaded. */
  default void onLibrariesUnloaded(boolean onlyInternal) {}

  /** Called when a module has been added or its content updated. */
  default void onModuleUpdated(@NotNull ModuleLocation module) {}

  /** Called when a module has been removed. */
  default void onModuleRemoved(@NotNull ModuleLocation module) {}

  /** Called when name resolution for a module is completed and resolved definitions are committed. */
  default void onModuleResolved(@NotNull ModuleLocation module) {}

  /** Called when a typechecking task finishes (successfully or interrupted). */
  default void onTypecheckingFinished() {}
}
