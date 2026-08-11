package org.arend.frontend.source;

import org.arend.ext.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

import java.io.FilterInputStream;
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
  public @NotNull InputStream getInputStream() throws IOException {
    ZipFile zipFile = new ZipFile(myFile);
    try {
      ZipEntry entry = zipFile.getEntry(myEntry);
      if (entry == null) {
        throw new ZipException("Cannot find " + myEntry + " in " + myFile);
      }
      InputStream entryStream = zipFile.getInputStream(entry);
      return new FilterInputStream(entryStream) {
        @Override
        public void close() throws IOException {
          try {
            super.close();
          } finally {
            zipFile.close();
          }
        }
      };
    } catch (IOException e) {
      zipFile.close();
      throw e;
    }
  }
}
