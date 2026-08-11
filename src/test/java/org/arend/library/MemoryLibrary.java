package org.arend.library;

import org.arend.ext.ui.ArendUI;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.arend.naming.reference.LocatedReferable;
import org.arend.server.ArendLibrary;
import org.arend.util.Range;
import org.arend.util.Version;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MemoryLibrary implements ArendLibrary {
  public final static ArendLibrary INSTANCE = new MemoryLibrary();

  private MemoryLibrary() {}

  @Override
  public @NotNull String getLibraryName() {
    return "test_library";
  }

  @Override
  public boolean isExternalLibrary() {
    return false;
  }

  @Override
  public long getModificationStamp() {
    return -1;
  }

  @Override
  public @NotNull List<String> getLibraryDependencies() {
    return Collections.emptyList();
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

  @Override
  public @Nullable Version getLibraryVersion() {
    return null;
  }

  @Override
  public @Nullable Range<Version> getLanguageVersion() {
    return null;
  }

  @Override
  public @NotNull Map<String, LocatedReferable> getGeneratedNames() {
    return ArendLibrary.super.getGeneratedNames();
  }
}
