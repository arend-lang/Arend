package org.arend.frontend.source;

import org.arend.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipFileRawSource extends StreamRawSource {
  private final ZipFile myFile;
  private final ZipEntry myEntry;

  public ZipFileRawSource(ModuleLocation module, ZipFile file, ZipEntry entry) {
    super(module);
    myFile = file;
    myEntry = entry;
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  protected @NotNull InputStream getInputStream() throws IOException {
    return myFile.getInputStream(myEntry);
  }
}
