package org.arend.frontend.source;

import org.arend.ext.module.ModuleLocation;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileRawSource extends StreamRawSource {
  private final Path myFile;

  /**
   * Creates a new {@code FileRawSource} from a path to the base directory and a path to the source.
   *
   * @param basePath    a path to the base directory.
   * @param module      a path to the source.
   */
  public FileRawSource(@NotNull Path basePath, @NotNull ModuleLocation module) {
    super(module);
    myFile = FileUtils.sourceFile(basePath, module.getModulePath());
  }

  public boolean isAvailable() {
    return Files.isRegularFile(myFile);
  }

  @NotNull
  @Override
  protected InputStream getInputStream() throws IOException {
    return Files.newInputStream(myFile);
  }

  @Override
  public long getTimeStamp() {
    try {
      return Files.getLastModifiedTime(myFile).toMillis();
    } catch (IOException e) {
      return 0;
    }
  }
}
