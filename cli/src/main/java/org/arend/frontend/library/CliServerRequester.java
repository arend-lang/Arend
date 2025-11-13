package org.arend.frontend.library;

import org.arend.ext.module.ModuleLocation;
import org.arend.server.ArendServer;
import org.arend.server.ArendServerRequester;
import org.arend.source.Source;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.arend.repl.Repl.REPL_NAME;

public class CliServerRequester implements ArendServerRequester {
  private final LibraryManager myLibraryManager;

  public CliServerRequester(LibraryManager libraryManager) {
    myLibraryManager = libraryManager;
  }

  public LibraryManager getLibraryManager() {
    return myLibraryManager;
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

  @Override
  public @Nullable List<String> getFiles(@NotNull String libraryName, boolean inTests, @NotNull List<String> prefix) {
    List<String> libraries = new ArrayList<>();
    if (libraryName.equals(REPL_NAME)) {
      libraries.addAll(getLibraryManager().getLibraries().stream().filter(library -> !library.equals(REPL_NAME)).toList());
    } else {
      libraries.add(libraryName);
    }
    List<String> result = new ArrayList<>();
    for (String libName : libraries) {
      SourceLibrary sourceLibrary = myLibraryManager.getLibrary(libName);
      if (sourceLibrary instanceof FileSourceLibrary fileSourceLibrary) {
        Path dir = fileSourceLibrary.sourceBasePath;
        if (inTests) {
          dir = fileSourceLibrary.testBasePath;
        }
        if (dir == null) {
          continue;
        }
        boolean isBreak = false;
        for (String name : prefix) {
          dir = dir.resolve(name);
          if (!dir.toFile().exists()) {
            isBreak = true;
            break;
          }
        }
        if (isBreak) {
          continue;
        }
        File[] files = dir.toFile().listFiles();
        if (files != null) {
          for (File file : files) {
            if (file.isDirectory()) {
              result.add(file.getName());
            } else if (file.getName().endsWith(FileUtils.EXTENSION)) {
              String fileName = file.getName();
              result.add(fileName.substring(0, fileName.lastIndexOf(FileUtils.EXTENSION)));
            }
          }
        }
      }
    }
    return result;
  }
}
