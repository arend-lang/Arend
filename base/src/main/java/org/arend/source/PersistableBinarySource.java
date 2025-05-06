package org.arend.source;

import org.arend.ext.error.ErrorReporter;
import org.arend.library.SourceLibrary;
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

  /**
   * Deletes the source.
   *
   * @param library             the library to which this source belongs.
   *
   * @return true if the operation is successful, false otherwise
   */
  boolean delete(SourceLibrary library);
}
