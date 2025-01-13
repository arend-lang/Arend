package org.arend.server;

import org.arend.ext.ui.ArendUI;
import org.arend.library.classLoader.ClassLoaderDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ArendLibrary {
  /**
   * @return the library name.
   */
  @NotNull String getLibraryName();

  /**
   * @return {@code true} if the library is external.
   * The content of an external library is always read-only.
   * Additionally, an external library cannot depend on an internal one.
   */
  boolean isExternalLibrary();

  /**
   * Tracks the version of the library.
   * If {@param modificationStamp} equals -1, then the library is always updated.
   */
  long getModificationStamp();

  /**
   * @return the list of library dependencies.
   */
  @NotNull List<String> getLibraryDependencies();

  /**
   * @return a class loader delegate which is used to load the library extension.
   */
  @Nullable ClassLoaderDelegate getClassLoaderDelegate();

  /**
   * @return the name of the library extension main class.
   */
  @Nullable String getExtensionMainClass();

  @Nullable ArendUI getArendUI();
}
