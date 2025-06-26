package org.arend.library;

import org.arend.ext.module.ModulePath;
import org.arend.frontend.source.StreamRawSource;
import org.arend.ext.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MemoryRawSource extends StreamRawSource {
  private final String myText;
  private final long myTimeStamp;

  public MemoryRawSource(ModulePath modulePath, String text, long timeStamp) {
    super(new ModuleLocation(MemoryLibrary.INSTANCE.getLibraryName(), ModuleLocation.LocationKind.SOURCE, modulePath));
    myText = text;
    myTimeStamp = timeStamp;
  }

  public MemoryRawSource(ModulePath modulePath, String text) {
    this(modulePath, text, 0);
  }

  public MemoryRawSource(MemoryRawSource source) {
    this(source.getModule().getModulePath(), source.myText, source.myTimeStamp);
  }

  @NotNull
  @Override
  protected InputStream getInputStream() {
    return new ByteArrayInputStream(myText.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public long getTimeStamp() {
    return myTimeStamp;
  }
}
