package org.arend.frontend.library;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.source.ZipFileRawSource;
import org.arend.library.LibraryConfig;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.arend.library.classLoader.ZipClassLoaderDelegate;
import org.arend.library.error.LibraryIOError;
import org.arend.module.ModuleLocation;
import org.arend.source.Source;
import org.arend.util.FileUtils;
import org.arend.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipSourceLibrary extends SourceLibrary {
  private final File myFile;
  private final String mySourcesDir;
  private final ClassLoaderDelegate myClassLoaderDelegate;
  private final Set<ModulePath> myModules;

  public ZipSourceLibrary(String name, List<String> dependencies, Version version, String extensionMainClass, File zipFile, String sourceDir, String extDir, Set<ModulePath> modules) {
    super(name, true, -1, dependencies, version, extensionMainClass);
    myFile = zipFile;
    mySourcesDir = sourceDir;
    myClassLoaderDelegate = extDir == null ? null : new ZipClassLoaderDelegate(zipFile, extDir);
    myModules = modules;
  }

  public static ZipSourceLibrary fromFile(File file, ErrorReporter errorReporter) {
    String fileName = file.getName();
    if (!fileName.endsWith(FileUtils.ZIP_EXTENSION)) {
      errorReporter.report(new LibraryIOError(fileName, "Incorrect zip name"));
      return null;
    }
    String libName = fileName.substring(0, fileName.length() - FileUtils.ZIP_EXTENSION.length());
    if (!FileUtils.isLibraryName(libName)) {
      errorReporter.report(new LibraryIOError(fileName, "Incorrect library name: " + libName));
      return null;
    }

    try (ZipFile zipFile = new ZipFile(file)) {
      ZipEntry yamlEntry = zipFile.getEntry(FileUtils.LIBRARY_CONFIG_FILE);
      if (yamlEntry == null) {
        errorReporter.report(new LibraryIOError(file.getPath(), "Cannot find " + FileUtils.LIBRARY_CONFIG_FILE + " in " + file));
        return null;
      }

      LibraryHeader header = LibraryHeader.fromConfig(new YAMLMapper().readValue(zipFile.getInputStream(yamlEntry), LibraryConfig.class), file.toString(), errorReporter);
      if (header == null) return null;

      String sourcesDir = header.sourcesDir() != null ? header.sourcesDir() : "";
      Set<ModulePath> modules = header.modules() != null ? header.modules() : zipFile.stream()
          .map(ZipEntry::getName)
          .filter(name -> name.startsWith(sourcesDir + "/") && name.endsWith(FileUtils.EXTENSION))
          .map(name -> new ModulePath(name.substring(sourcesDir.length() + 1, name.length() - FileUtils.EXTENSION.length()).split("/")))
          .filter(FileUtils::isCorrectModulePath)
          .collect(Collectors.toSet());

      return new ZipSourceLibrary(libName, header.dependencies(), header.version(), header.extMainClass(), file, sourcesDir, header.extDir(), modules);
    } catch (IOException e) {
      errorReporter.report(new LibraryIOError(fileName, "Cannot read file", e.getLocalizedMessage()));
      return null;
    }
  }

  @Override
  public @Nullable Source getSource(@NotNull ModulePath modulePath, boolean inTests) {
    if (inTests || !myModules.contains(modulePath)) return null;
    StringBuilder builder = new StringBuilder();
    builder.append(mySourcesDir);
    for (String name : modulePath.toList()) {
      builder.append("/");
      builder.append(name);
    }
    builder.append(FileUtils.EXTENSION);
    return new ZipFileRawSource(new ModuleLocation(getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath), myFile, builder.toString());
  }

  @Override
  public @NotNull List<ModulePath> findModules(boolean inTests) {
    return new ArrayList<>(myModules);
  }

  @Override
  public @Nullable ClassLoaderDelegate getClassLoaderDelegate() {
    return myClassLoaderDelegate;
  }
}
