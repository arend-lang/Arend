package org.arend.frontend.source;

import org.arend.prelude.Prelude;
import org.arend.source.FileBinarySource;
import org.arend.source.GZIPStreamBinarySource;
import org.arend.source.PersistableBinarySource;
import org.arend.source.Source;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PreludeSources {
  private static final Path BASE_PATH = Paths.get("lib");

  public static Source getFileSource() {
    Path preludePath = BASE_PATH;
    String arendPath = System.getenv("AREND_PATH");
    if (arendPath != null) {
      preludePath = Paths.get(arendPath).resolve(preludePath);
    }
    return new FileRawSource(preludePath, Prelude.MODULE_LOCATION);
  }

  public static PersistableBinarySource getBinarySource(Path binaryPath) {
    return new GZIPStreamBinarySource(new FileBinarySource(binaryPath.resolve(BASE_PATH), Prelude.MODULE_LOCATION));
  }
}
