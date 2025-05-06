package org.arend.library;

import org.arend.ArendTestCase;
import org.junit.Before;

public class LibraryTestCase extends ArendTestCase {
  @Before
  public void initialize() {
    server.updateLibrary(MemoryLibrary.INSTANCE, errorReporter);
  }
}
