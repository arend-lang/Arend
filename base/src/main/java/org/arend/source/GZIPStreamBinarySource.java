package org.arend.source;

import org.arend.ext.module.ModuleLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GZIPStreamBinarySource extends StreamBinarySource {
  private final StreamBinarySource mySource;

  /**
   * Creates a new {@code GZIPStreamBinarySource} from a specified source.
   * @param source  the input source.
   */
  public GZIPStreamBinarySource(StreamBinarySource source) {
    mySource = source;
  }

  @Nullable
  @Override
  protected InputStream getInputStream() throws IOException {
    InputStream stream = mySource.getInputStream();
    return stream == null ? null : new GZIPInputStream(stream);
  }

  @Nullable
  @Override
  protected OutputStream getOutputStream() throws IOException {
    OutputStream stream = mySource.getOutputStream();
    return stream == null ? null : new GZIPOutputStream(stream);
  }

  @NotNull
  @Override
  public ModuleLocation getModule() {
    return mySource.getModule();
  }

  @Override
  public long getTimeStamp() {
    return mySource.getTimeStamp();
  }
}
