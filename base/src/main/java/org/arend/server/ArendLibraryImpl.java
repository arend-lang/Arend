package org.arend.server;

import org.arend.ext.ArendExtension;
import org.arend.ext.ui.ArendUI;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ArendLibraryImpl implements ArendLibrary {
  private final String myLibraryName;
  private final boolean myExternalLibrary;
  private final long myModificationStamp;
  private final List<String> myDependencies;
  private final ArendExtension myExtension;

  public ArendLibraryImpl(String libraryName, boolean externalLibrary, long modificationStamp, List<String> dependencies, ArendExtension extension) {
    myLibraryName = libraryName;
    myExternalLibrary = externalLibrary;
    myModificationStamp = modificationStamp;
    myDependencies = dependencies;
    myExtension = extension;
  }

  @Override
  public @NotNull String getLibraryName() {
    return myLibraryName;
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
  public @Nullable ClassLoaderDelegate getClassLoaderDelegate() {
    return null;
  }

  @Override
  public @Nullable String getExtensionMainClass() {
    return null;
  }

  @Override
  public @Nullable ArendUI getArendUI() {
    return null;
  }

  public ArendExtension getExtension() {
    return myExtension;
  }
}
