package org.arend.library;

import org.arend.ArendTestCase;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.ListErrorReporter;
import org.junit.Before;

import java.util.ArrayList;
import java.util.List;

public class LibraryTestCase extends ArendTestCase {
  protected final List<GeneralError> errorList = new ArrayList<>();
  protected final ListErrorReporter errorReporter = new ListErrorReporter(errorList);

  @Before
  public void initialize() {
    server.updateLibrary(MemoryLibrary.INSTANCE, errorReporter);
  }
}
