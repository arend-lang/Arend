package org.arend.library;

import org.arend.module.ModuleLocation;
import org.arend.source.StreamBinarySource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MemoryBinarySource extends StreamBinarySource {
  private final ModuleLocation myModule;
  private ByteArrayOutputStream myOutputStream;

  public MemoryBinarySource(ModuleLocation module) {
    myModule = module;
  }

  @Nullable
  @Override
  protected InputStream getInputStream() {
    return new ByteArrayInputStream(myOutputStream.toByteArray());
  }

  @Nullable
  @Override
  protected OutputStream getOutputStream() {
    if (myOutputStream == null) {
      myOutputStream = new ByteArrayOutputStream();
    }
    return myOutputStream;
  }

  @NotNull
  @Override
  public ModuleLocation getModule() {
    return myModule;
  }

  @Override
  public long getTimeStamp() {
    return 0;
  }

  @Override
  public boolean delete(SourceLibrary library) {
    myOutputStream = null;
    return true;
  }
}
