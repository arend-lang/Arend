package org.arend.frontend.source;

import org.arend.prelude.Prelude;
import org.arend.util.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public class PreludeResourceSource extends StreamRawSource {
  private static final String RESOURCE_PATH = "/lib/" + Prelude.MODULE_PATH + FileUtils.EXTENSION;

  public PreludeResourceSource() {
    super(Prelude.MODULE_LOCATION);
  }

  @Override
  protected @NotNull InputStream getInputStream() throws IOException {
    InputStream stream = Prelude.class.getResourceAsStream(RESOURCE_PATH);
    if (stream == null) {
      throw new IOException("Cannot find the prelude resource");
    }
    return stream;
  }

  @Override
  public long getTimeStamp() {
    return -1;
  }
}
