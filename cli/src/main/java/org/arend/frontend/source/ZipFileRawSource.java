package org.arend.frontend.source;

import org.arend.ext.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

public class ZipFileRawSource extends StreamRawSource {
  private final File myFile;
  private final String myEntry;

  public ZipFileRawSource(ModuleLocation module, File file, String entry) {
    super(module);
    myFile = file;
    myEntry = entry;
  }

  @Override
  public long getTimeStamp() {
    return myFile.lastModified();
  }

  @Override
  protected @NotNull InputStream getInputStream() throws IOException {
    try (ZipFile zipFile = new ZipFile(myFile)) {
      ZipEntry entry = zipFile.getEntry(myEntry);
      if (entry == null) {
        throw new ZipException("Cannot find " + myEntry + " in " + myFile);
      }
      return zipFile.getInputStream(entry);
    }
  }
}
