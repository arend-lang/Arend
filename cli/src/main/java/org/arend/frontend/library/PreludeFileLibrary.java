package org.arend.frontend.library;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.source.FileRawSource;
import org.arend.prelude.Prelude;
import org.arend.prelude.PreludeResourceSource;
import org.arend.prelude.PreludeTypecheckingLibrary;
import org.arend.source.*;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A library which is used to load and persist prelude from and to a file.
 */
public class PreludeFileLibrary extends PreludeTypecheckingLibrary {
  private final Path myBinaryPath;

  /**
   * Creates a new {@code PreludeFileLibrary}
   */
  public PreludeFileLibrary(Path binaryPath) {
    myBinaryPath = binaryPath;
  }

  public static Source getSource() {
    Path preludePath = PreludeResourceSource.BASE_PATH;
    String arendPath = System.getenv("AREND_PATH");
    if (arendPath != null) {
      preludePath = Paths.get(arendPath).resolve(preludePath);
    }
    return new FileRawSource(preludePath, Prelude.MODULE_LOCATION);
  }

  public static PersistableBinarySource getBinarySource(Path binaryPath) {
    return new GZIPStreamBinarySource(new FileBinarySource(binaryPath.resolve(PreludeResourceSource.BASE_PATH), Prelude.MODULE_LOCATION));
  }

  @Nullable
  @Override
  public Source getRawSource(ModulePath modulePath) {
    return modulePath.equals(Prelude.MODULE_PATH) ? getSource() : null;
  }

  @Nullable
  @Override
  public PersistableBinarySource getPersistableBinarySource(ModulePath modulePath) {
    return myBinaryPath != null && modulePath.equals(Prelude.MODULE_PATH) ? getBinarySource(myBinaryPath) : null;
  }

  @Override
  public boolean supportsPersisting() {
    return myBinaryPath != null;
  }
}
