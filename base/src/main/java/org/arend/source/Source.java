package org.arend.source;

import org.arend.ext.error.ErrorReporter;
import org.arend.module.ModuleLocation;
import org.arend.server.ArendServer;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a persisted module.
 */
public interface Source {
  @NotNull ModuleLocation getModule();

  /**
   * Gets the timestamp for this source.
   *
   * @return timestamp
   */
  long getTimeStamp();

  @Nullable ConcreteGroup loadGroup(@NotNull ErrorReporter errorReporter);

  default void load(@NotNull ArendServer server, @NotNull ErrorReporter errorReporter) {
    server.updateModule(getTimeStamp(), getModule(), () -> loadGroup(errorReporter));
  }
}
