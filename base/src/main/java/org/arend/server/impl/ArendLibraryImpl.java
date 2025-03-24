package org.arend.server.impl;

import org.arend.ext.ArendExtension;
import org.arend.ext.ui.ArendUI;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.arend.naming.reference.LocatedReferable;
import org.arend.server.ArendLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ArendLibraryImpl implements ArendLibrary {
  private final String myLibraryName;
  private final boolean myExternalLibrary;
  private final long myModificationStamp;
  private final List<String> myDependencies;
  private final ArendExtension myExtension;
  private final Map<String, LocatedReferable> myGeneratedNames;

  public ArendLibraryImpl(String libraryName, boolean externalLibrary, long modificationStamp, List<String> dependencies, ArendExtension extension, Map<String, LocatedReferable> generatedNames) {
    myLibraryName = libraryName;
    myExternalLibrary = externalLibrary;
    myModificationStamp = modificationStamp;
    myDependencies = dependencies;
    myExtension = extension;
    myGeneratedNames = generatedNames == null ? new HashMap<>() : new HashMap<>(generatedNames);
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

  @Override
  public @NotNull Map<String, LocatedReferable> getGeneratedNames() {
    return Collections.unmodifiableMap(myGeneratedNames);
  }

  public void putGeneratedName(String name, LocatedReferable referable) {
    myGeneratedNames.put(name, referable);
  }
}
