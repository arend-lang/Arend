package org.arend.source;

import org.arend.ext.error.ErrorReporter;
import org.arend.library.SourceLibrary;

public interface PersistableBinarySource extends BinarySource {
  /**
   * Persists the source.
   *
   * @param library             the library to which this source belongs.
   * @param errorReporter       a reporter for all errors that occur during persisting process.
   *
   * @return true if the operation is successful, false otherwise
   */
  boolean persist(SourceLibrary library, ErrorReporter errorReporter);

  /**
   * Deletes the source.
   *
   * @param library             the library to which this source belongs.
   *
   * @return true if the operation is successful, false otherwise
   */
  boolean delete(SourceLibrary library);
}
