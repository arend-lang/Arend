package org.arend.frontend.library;

import org.arend.ext.module.ModulePath;
import org.arend.ext.ui.ArendUI;
import org.arend.frontend.source.StreamRawSource;
import org.arend.query.QueryLibrary;
import org.arend.frontend.ui.ArendCliUI;
import org.arend.server.ArendLibrary;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;
import org.arend.util.Range;
import org.arend.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public abstract class SourceLibrary implements ArendLibrary, QueryLibrary {
  private final String myName;
  private final boolean myExternalLibrary;
  private final long myModificationStamp;
  private final List<String> myDependencies;
  private final Version myVersion;
  private final Range<Version> myLangVersion;
  private final String myExtensionMainClass;

  public SourceLibrary(String name, boolean isExternalLibrary, long modificationStamp, List<String> dependencies, Version version, Range<Version> langVersion, String extensionMainClass) {
    myName = name;
    myExternalLibrary = isExternalLibrary;
    myModificationStamp = modificationStamp;
    myDependencies = dependencies;
    myVersion = version;
    myLangVersion = langVersion;
    myExtensionMainClass = extensionMainClass;
  }

  @Override
  public @NotNull String getLibraryName() {
    return myName;
  }

  @Override
  public boolean isExternalLibrary() {
    return myExternalLibrary;
  }

  @Override
  public long getModificationStamp() {
    return myModificationStamp;
  }

  @Override
  public @NotNull List<String> getLibraryDependencies() {
    return myDependencies;
  }

  @Override
  public @Nullable String getExtensionMainClass() {
    return myExtensionMainClass;
  }

  @Override
  public @NotNull ArendUI getArendUI() {
    return new ArendCliUI();
  }

  @Override
  public @Nullable Version getLibraryVersion() {
    return myVersion;
  }

  @Override
  public @Nullable Range<Version> getLanguageVersion() {
    return myLangVersion;
  }

  public abstract @Nullable Source getSource(@NotNull ModulePath modulePath, boolean inTests);

  @Override
  public abstract @NotNull List<ModulePath> findModules(boolean inTests);

  /**
   * Read through the library's {@link Source}, not the filesystem, so that a zip library answers
   * too; decoded as UTF-8, as the parser does, so that offsets stay aligned with lines and columns.
   */
  @Override
  public @Nullable String readModuleText(@NotNull ModulePath modulePath, boolean inTests) {
    if (!(getSource(modulePath, inTests) instanceof StreamRawSource source)) return null;
    try (InputStream in = source.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Gets a binary source for the given module path.
   *
   * @param modulePath  the module path.
   * @return a persistable binary source, or null if binary caching is not supported.
   */
  public @Nullable PersistableBinarySource getBinarySource(@NotNull ModulePath modulePath) {
    return null;
  }

  /**
   * Checks whether this library supports persisting binary caches.
   */
  public boolean supportsPersisting() {
    return !isExternalLibrary();
  }
}
