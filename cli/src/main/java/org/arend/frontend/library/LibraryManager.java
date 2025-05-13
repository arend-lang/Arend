package org.arend.frontend.library;

import org.arend.ext.error.ErrorReporter;
import org.arend.server.ArendServer;

import java.util.HashMap;
import java.util.Map;

public class LibraryManager {
  private final ErrorReporter myErrorReporter;
  private final Map<String, SourceLibrary> myLibraries = new HashMap<>();

  public LibraryManager(ErrorReporter errorReporter) {
    myErrorReporter = errorReporter;
  }

  public void updateLibrary(SourceLibrary library, ArendServer server) {
    if (myLibraries.put(library.getLibraryName(), library) != null) {
      server.removeLibrary(library.getLibraryName());
    }
    server.updateLibrary(library, myErrorReporter);
  }

  public void removeLibrary(String name, ArendServer server) {
    server.removeLibrary(name);
    myLibraries.remove(name);
  }

  public SourceLibrary getLibrary(String name) {
    return myLibraries.get(name);
  }

  public boolean containsLibrary(String name) {
    return myLibraries.containsKey(name);
  }

  public ErrorReporter getErrorReporter() {
    return myErrorReporter;
  }
}
