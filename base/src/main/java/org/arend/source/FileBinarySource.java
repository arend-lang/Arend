package org.arend.source;

import org.arend.module.ModuleLocation;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileBinarySource extends StreamBinarySource {
  private final Path myFile;
  private final ModuleLocation myModule;

  /**
   * Creates a new {@code FileBinarySource} from a path to the base directory and a path to the source.
   *
   * @param basePath    a path to the base directory.
   * @param module      a path to the source.
   */
  public FileBinarySource(Path basePath, ModuleLocation module) {
    myFile = FileUtils.binaryFile(basePath, module.getModulePath());
    myModule = module;
  }

  @NotNull
  @Override
  public ModuleLocation getModule() {
    return myModule;
  }

  @Nullable
  @Override
  protected InputStream getInputStream() throws IOException {
    return Files.newInputStream(myFile);
  }

  @Nullable
  @Override
  protected OutputStream getOutputStream() throws IOException {
    Files.createDirectories(myFile.getParent());
    return Files.newOutputStream(myFile, StandardOpenOption.CREATE);
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
