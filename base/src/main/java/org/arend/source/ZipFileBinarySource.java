package org.arend.source;

import org.arend.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipFileBinarySource extends StreamBinarySource {
  private final ModuleLocation myModule;
  private final ZipFile myFile;
  private final ZipEntry myEntry;

  public ZipFileBinarySource(ModuleLocation module, ZipFile file, ZipEntry entry) {
    myModule = module;
    myFile = file;
    myEntry = entry;
  }

  @Override
  public @NotNull ModuleLocation getModule() {
    return myModule;
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  protected @Nullable InputStream getInputStream() throws IOException {
    return myFile.getInputStream(myEntry);
  }

  @Override
  protected @Nullable OutputStream getOutputStream() {
    return null;
  }
}
