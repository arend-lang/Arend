package org.arend.source;

import org.arend.ext.error.ErrorReporter;
import org.arend.server.ArendServer;

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
}
