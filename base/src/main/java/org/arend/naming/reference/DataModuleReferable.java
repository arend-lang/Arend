package org.arend.naming.reference;

import org.arend.ext.reference.DataContainer;
import org.arend.module.ModuleLocation;
import org.jetbrains.annotations.Nullable;

public class DataModuleReferable extends FullModuleReferable implements DataContainer {
  private Object myData;

  public DataModuleReferable(Object data, ModuleLocation location) {
    super(location);
    myData = data;
  }

  @Override
  public @Nullable Object getData() {
    return myData;
  }

  public void setData(Object data) {
    myData = data;
  }
}
