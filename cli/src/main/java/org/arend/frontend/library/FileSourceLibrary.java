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
import org.arend.ext.module.ModuleLocation;
import org.arend.source.FileBinarySource;
import org.arend.source.GZIPStreamBinarySource;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.util.FileUtils;
import org.arend.util.Range;
import org.arend.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class FileSourceLibrary extends SourceLibrary {
  protected final Path sourceBasePath;
  protected final Path binaryBasePath;
  protected final Path testBasePath;
  private @Nullable Path basePath;
  private final Set<ModulePath> myModules;
  private final ClassLoaderDelegate myClassLoaderDelegate;

  public FileSourceLibrary(String name, boolean isExternalLibrary, long modificationStamp, List<String> dependencies, Version version, Range<Version> langVersion, String extensionMainClass, @Nullable Set<ModulePath> modules, Path sourceBasePath, Path binaryBasePath, Path testBasePath, ClassLoaderDelegate classLoaderDelegate) {
    super(name, isExternalLibrary, modificationStamp, dependencies, version, langVersion, extensionMainClass);
    this.sourceBasePath = sourceBasePath;
    this.binaryBasePath = binaryBasePath;
    this.testBasePath = testBasePath;
    myModules = modules;
    myClassLoaderDelegate = classLoaderDelegate;
  }

  public static FileSourceLibrary fromConfigFile(Path configFile, boolean isExternalLibrary, ErrorReporter errorReporter) {
    try {
      Path configAbsolutePath = configFile.toAbsolutePath();
      Path basePath = configAbsolutePath.getParent();
      Path dirName = basePath == null ? null : basePath.getFileName();
      if (dirName == null) {
        errorReporter.report(new LibraryIOError(configAbsolutePath.toString(), "Configuration file does not have a parent"));
        return null;
      }

      String libName = dirName.toString();
      if (!FileUtils.isLibraryName(libName)) {
        errorReporter.report(new LibraryIOError(configAbsolutePath.toString(), "Incorrect library name: " + libName));
        return null;
      }

      LibraryHeader header = LibraryHeader.fromConfig(new YAMLMapper().readValue(configFile.toFile(), LibraryConfig.class), configFile.toString(), errorReporter);

      if (header == null) return null;
      FileSourceLibrary lib = new FileSourceLibrary(libName, isExternalLibrary, Files.getLastModifiedTime(configFile).toMillis(),
          header.dependencies(), header.version(), header.langVersion(), header.extMainClass(), header.modules(),
          header.sourcesDir() == null ? basePath : basePath.resolve(header.sourcesDir()),
          header.binariesDir() == null ? null : basePath.resolve(header.binariesDir()),
          header.testDir() == null ? null : basePath.resolve(header.testDir()),
          header.extDir() == null ? null : new FileClassLoaderDelegate(basePath.resolve(header.extDir())));
      lib.basePath = basePath;
      return lib;
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
  public @Nullable PersistableBinarySource getBinarySource(@NotNull ModulePath modulePath) {
    if (binaryBasePath == null) return null;
    return new GZIPStreamBinarySource(new FileBinarySource(binaryBasePath, new ModuleLocation(getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath)));
  }

  public @Nullable Path getBinaryBasePath() {
    return binaryBasePath;
  }

  public @Nullable Path getSourceBasePath() {
    return sourceBasePath;
  }

  /**
   * The directory containing this library's {@code arend.yaml}. Set when the
   * library is loaded via {@link #fromConfigFile}; null for libraries created
   * directly through the constructor (REPL, default project, tests).
   */
  public @Nullable Path getBasePath() {
    return basePath;
  }

  public void setBasePath(@Nullable Path basePath) {
    this.basePath = basePath;
  }

  @Override
  public @Nullable ClassLoaderDelegate getClassLoaderDelegate() {
    return myClassLoaderDelegate;
  }

  /**
   * Resolves the aux file under {@link #getBasePath()}, falling back to the parent
   * of {@link #getSourceBasePath()} for libraries built without {@code fromConfigFile}.
   */
  @Override
  public @Nullable InputStream openAuxFile(@NotNull String relPath) throws IOException {
    Path root = basePath;
    if (root == null && sourceBasePath != null) root = sourceBasePath.getParent();
    if (root == null) return null;
    Path target = root;
    for (String part : relPath.split("/")) {
      if (part.isEmpty() || part.equals(".") || part.equals("..")) return null;
      target = target.resolve(part);
    }
    if (!Files.isRegularFile(target)) return null;
    return Files.newInputStream(target);
  }
}