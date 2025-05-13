package org.arend.frontend;

import org.arend.ext.error.ErrorReporter;
import org.arend.library.*;
import org.arend.library.error.LibraryIOError;
import org.arend.library.resolver.LibraryResolver;
import org.arend.typechecking.order.dependency.DependencyListener;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FileLibraryResolver implements LibraryResolver {
  private final List<Path> myLibDirs;
  private final ErrorReporter myErrorReporter;
  private final Map<String, SourceLibrary> myLibraries = new HashMap<>();

  public FileLibraryResolver(List<Path> libDirs, ErrorReporter errorReporter, DependencyListener dependencyListener) {
    myLibDirs = libDirs;
    myErrorReporter = errorReporter;
  }

  private SourceLibrary getLibrary(Path headerFile) {
    return null;
  }

  private SourceLibrary getLibraryFromZip(Path zipFile) {
    return null;
  }

  private SourceLibrary findLibrary(Path libDir, String libName) {
    SourceLibrary library;
    Path zipFile = libDir.resolve(libName + FileUtils.ZIP_EXTENSION);
    if (Files.exists(zipFile)) {
      library = getLibraryFromZip(zipFile);
    } else {
      Path yaml = libDir.resolve(libName).resolve(FileUtils.LIBRARY_CONFIG_FILE);
      library = Files.exists(yaml) ? getLibrary(yaml) : null;
    }
    return library != null && library.getName().equals(libName) ? library : null;
  }

  public void addLibraryDirectory(Path libDir) {
    myLibDirs.add(libDir);
  }

  public void addLibraryDirectories(Collection<? extends Path> libDirs) {
    myLibDirs.addAll(libDirs);
  }

  /**
   * @param libPath Recommended to use a
   *                <code>.toAbsolutePath().normalize()</code> path.
   */
  public SourceLibrary registerLibrary(Path libPath) {
    SourceLibrary library;
    if (Files.isDirectory(libPath)) {
      library = getLibrary(libPath.resolve(FileUtils.LIBRARY_CONFIG_FILE));
    } else if (libPath.endsWith(FileUtils.LIBRARY_CONFIG_FILE)) {
      library = getLibrary(libPath);
    } else if (libPath.endsWith(FileUtils.ZIP_EXTENSION)) {
      library = getLibraryFromZip(libPath);
    } else {
      myErrorReporter.report(new LibraryIOError(libPath.toString(), "Unrecognized file type"));
      library = null;
    }

    if (library == null) {
      return null;
    }

    return myLibraries.putIfAbsent(library.getName(), library);
  }

  @Nullable
  @Override
  public Library resolve(Library lib, String dependencyName) {
    if (!FileUtils.isLibraryName(dependencyName)) {
      return null;
    }

    SourceLibrary library = myLibraries.get(dependencyName);
    if (library != null) {
      return library;
    }

    library = findLibrary(FileUtils.getCurrentDirectory(), dependencyName);
    if (library == null) {
      for (Path libDir : myLibDirs) {
        library = findLibrary(libDir, dependencyName);
      }
    }

    if (library != null) {
      myLibraries.put(dependencyName, library);
    }

    return library;
  }
}
