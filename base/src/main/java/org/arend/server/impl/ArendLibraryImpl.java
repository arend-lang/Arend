package org.arend.server.impl;

import org.arend.ext.ArendExtension;
import org.arend.ext.ui.ArendUI;
import org.arend.extImpl.SerializableKeyRegistryImpl;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.arend.naming.reference.LocatedReferable;
import org.arend.server.ArendLibrary;
import org.arend.util.Range;
import org.arend.util.Version;
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
  private ArendExtension myExtension;
  private SerializableKeyRegistryImpl myKeyRegistry;
  private final Map<String, LocatedReferable> myGeneratedNames;
  private final Version myVersion;
  private final Range<Version> myLanguageVersion;

  public ArendLibraryImpl(String libraryName, Version version, Range<Version> languageVersion, boolean externalLibrary, long modificationStamp, List<String> dependencies, ArendExtension extension, Map<String, LocatedReferable> generatedNames) {
    myLibraryName = libraryName;
    myExternalLibrary = externalLibrary;
    myModificationStamp = modificationStamp;
    myDependencies = dependencies;
    myExtension = extension;
    myGeneratedNames = generatedNames == null ? new HashMap<>() : new HashMap<>(generatedNames);
    myVersion = version;
    myLanguageVersion = languageVersion;
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
  public @Nullable Version getLibraryVersion() { return myVersion; }

  @Override
  public @Nullable Range<Version> getLanguageVersion() {
    return myLanguageVersion;
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

  public void setExtension(ArendExtension extension) {
    myExtension = extension;
  }

  public @Nullable SerializableKeyRegistryImpl getKeyRegistry() {
    return myKeyRegistry;
  }

  public void setKeyRegistry(@Nullable SerializableKeyRegistryImpl keyRegistry) {
    myKeyRegistry = keyRegistry;
  }

  @Override
  public @NotNull Map<String, LocatedReferable> getGeneratedNames() {
    return Collections.unmodifiableMap(myGeneratedNames);
  }

  public void putGeneratedName(String name, LocatedReferable referable) {
    myGeneratedNames.put(name, referable);
  }
}
