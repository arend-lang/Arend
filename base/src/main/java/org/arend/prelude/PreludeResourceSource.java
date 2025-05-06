package org.arend.prelude;

import org.arend.library.SourceLibrary;
import org.arend.module.ModuleLocation;
import org.arend.source.StreamBinarySource;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

public class PreludeResourceSource extends StreamBinarySource {
  public static final Path BASE_PATH = Paths.get("lib");
  private static final Path BINARY_PATH = FileUtils.binaryFile(BASE_PATH, Prelude.MODULE_PATH);
  private static final String BINARY_RESOURCE_PATH = "/lib/" + BINARY_PATH.getFileName();

  @Nullable
  @Override
  protected InputStream getInputStream() {
    return Prelude.class.getResourceAsStream(BINARY_RESOURCE_PATH);
  }

  @Nullable
  @Override
  protected OutputStream getOutputStream() {
    return null;
  }

  @NotNull
  @Override
  public ModuleLocation getModule() {
    return Prelude.MODULE_LOCATION;
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  public boolean delete(SourceLibrary library) {
    return false;
  }
}
