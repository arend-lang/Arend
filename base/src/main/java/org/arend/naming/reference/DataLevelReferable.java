package org.arend.naming.reference;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DataLevelReferable implements LevelReferable {
  private final Object myData;
  private final String myName;

  public DataLevelReferable(Object data, String name) {
    myData = data;
    myName = name;
  }

  @Override
  public @Nullable Object getData() {
    return myData;
  }

  @Override
  public @NotNull Referable.RefKind getRefKind() {
    return RefKind.LEVEL;
  }

  @Override
  public @NotNull String textRepresentation() {
    return myName;
  }
}
