package org.arend.frontend.library;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.arend.error.DummyErrorReporter;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.module.ModulePath;
import org.arend.frontend.source.FileRawSource;
import org.arend.library.LibraryConfig;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.arend.library.classLoader.FileClassLoaderDelegate;
import org.arend.library.error.LibraryIOError;
import org.arend.module.ModuleLocation;
import org.arend.source.Source;
import org.arend.util.FileUtils;
import org.arend.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FileSourceLibrary extends SourceLibrary {
  protected final Path sourceBasePath;
  protected final Path binaryBasePath;
  protected final Path testBasePath;
  private final Set<ModulePath> myModules;
  private final ClassLoaderDelegate myClassLoaderDelegate;

  public FileSourceLibrary(String name, boolean isExternalLibrary, long modificationStamp, List<String> dependencies, Version version, String extensionMainClass, @Nullable Set<ModulePath> modules, Path sourceBasePath, Path binaryBasePath, Path testBasePath, ClassLoaderDelegate classLoaderDelegate) {
    super(name, isExternalLibrary, modificationStamp, dependencies, version, extensionMainClass);
    this.sourceBasePath = sourceBasePath;
    this.binaryBasePath = binaryBasePath;
    this.testBasePath = testBasePath;
    myModules = modules;
    myClassLoaderDelegate = classLoaderDelegate;
  }

  public static FileSourceLibrary fromConfigFile(Path configFile, boolean isExternalLibrary, ErrorReporter errorReporter) {
    try {
      Path basePath = configFile.getParent();
      Path dirName = basePath == null ? null : basePath.getFileName();
      if (dirName == null) {
        errorReporter.report(new LibraryIOError(configFile.toString(), "Configuration file does not have a parent"));
        return null;
      }

      String libName = dirName.toString();
      if (!FileUtils.isLibraryName(libName)) {
        errorReporter.report(new LibraryIOError(configFile.toString(), "Incorrect library name: " + libName));
        return null;
      }

      LibraryHeader header = LibraryHeader.fromConfig(new YAMLMapper().readValue(configFile.toFile(), LibraryConfig.class), configFile.toString(), errorReporter);

      return header == null ? null : new FileSourceLibrary(libName, isExternalLibrary, Files.getLastModifiedTime(configFile).toMillis(),
          header.dependencies(), header.version(), header.extMainClass(), header.modules(),
          header.sourcesDir() == null ? basePath : basePath.resolve(header.sourcesDir()),
          header.binariesDir() == null ? null : basePath.resolve(header.binariesDir()),
          header.testDir() == null ? null : basePath.resolve(header.testDir()),
          header.extDir() == null ? null : new FileClassLoaderDelegate(basePath.resolve(header.extDir())));
    } catch (IOException e) {
      errorReporter.report(new LibraryIOError(configFile.toString(), "Failed to read configuration file", e.getLocalizedMessage()));
      return null;
    }
  }

  @Override
  public @Nullable Source getSource(@NotNull ModulePath modulePath, boolean inTests) {
    if (myModules != null && !myModules.contains(modulePath) || inTests && testBasePath == null) return null;
    FileRawSource source = new FileRawSource(inTests ? testBasePath : sourceBasePath, new ModuleLocation(getLibraryName(), inTests ? ModuleLocation.LocationKind.TEST : ModuleLocation.LocationKind.SOURCE, modulePath));
    return source.isAvailable() ? source : null;
  }

  @Override
  public @NotNull List<ModulePath> findModules(boolean inTests) {
    if (inTests && testBasePath == null) return Collections.emptyList();
    List<ModulePath> result = new ArrayList<>();
    FileUtils.getModules(inTests ? testBasePath : sourceBasePath, FileUtils.EXTENSION, result, DummyErrorReporter.INSTANCE);
    return result;
  }

  @Override
  public @Nullable ClassLoaderDelegate getClassLoaderDelegate() {
    return myClassLoaderDelegate;
  }
}