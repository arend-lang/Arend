package org.arend.frontend.library;

import org.arend.ext.module.ModuleLocation;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.source.Source;
import org.jetbrains.annotations.NotNull;

public class CliServerRequester implements ArendServerRequester {
  private final LibraryManager myLibraryManager;

  public CliServerRequester(LibraryManager libraryManager) {
    myLibraryManager = libraryManager;
  }

  @Override
  public void requestModuleUpdate(@NotNull ArendServer server, @NotNull ModuleLocation module) {
    if (module.getLocationKind() == ModuleLocation.LocationKind.GENERATED) return;
    SourceLibrary library = myLibraryManager.getLibrary(module.getLibraryName());
    if (library == null) return;
    Source source = library.getSource(module.getModulePath(), module.getLocationKind() == ModuleLocation.LocationKind.TEST);
    if (source == null) return;
    source.load(server, myLibraryManager.getErrorReporter());
  }
}
