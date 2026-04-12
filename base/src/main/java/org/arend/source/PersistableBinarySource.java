package org.arend.source;

import org.arend.ext.error.ErrorReporter;
import org.arend.server.ArendServer;
import org.arend.term.group.ConcreteGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PersistableBinarySource extends BinarySource {
  /**
   * Persists the source.
   *
   * @param server              the Arend server.
   * @param errorReporter       a reporter for all errors that occur during persisting process.
   *
   * @return true if the operation is successful, false otherwise
   */
  boolean persist(ArendServer server, ErrorReporter errorReporter);

  /**
   * Loads the source.
   *
   * @param server              the Arend server.
   * @param errorReporter       a reporter for all errors that occur during loading process.
   *
   * @return the loaded group, or null if loading failed
   */
  @Nullable ConcreteGroup load(@NotNull ArendServer server, @NotNull ErrorReporter errorReporter);
}
