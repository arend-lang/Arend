package org.arend.server;

import org.arend.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

public interface ArendServerRequester {
  /**
   * Request an invocation of {@link ArendServer#updateModule}.
   *
   * @param server    server to be updated.
   * @param module    module to be updated.
   */
  void requestModuleUpdate(@NotNull ArendServer server, @NotNull ModuleLocation module);
}
