package org.arend.frontend.library;

import org.arend.ext.module.ModulePath;
import org.arend.ext.ui.ArendUI;
import org.arend.frontend.ui.ArendCliUI;
import org.arend.server.ArendLibrary;
import org.arend.source.Source;
import org.arend.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class SourceLibrary implements ArendLibrary {
  private final String myName;
  private final boolean myExternalLibrary;
  private final long myModificationStamp;
  private final List<String> myDependencies;
  private final Version myVersion;
  private final String myExtensionMainClass;

  public SourceLibrary(String name, boolean isExternalLibrary, long modificationStamp, List<String> dependencies, Version version, String extensionMainClass) {
    myName = name;
    myExternalLibrary = isExternalLibrary;
    myModificationStamp = modificationStamp;
    myDependencies = dependencies;
    myVersion = version;
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

  public @Nullable Version getVersion() {
    return myVersion;
  }

  public abstract @Nullable Source getSource(@NotNull ModulePath modulePath, boolean inTests);
}
