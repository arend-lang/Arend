package org.arend.core.context.binding;

import org.arend.ext.core.ops.CMP;

public class ParamLevelVariable implements LevelVariable {
  private final String myName;
  private final int myIndex;

  public ParamLevelVariable(String name, int index) {
    myName = name;
    myIndex = index;
  }

  @Override
  public LevelVariable min(LevelVariable other) {
    return compare(other, CMP.EQ) ? this : null;
  }

  @Override
  public boolean compare(LevelVariable other, CMP cmp) {
    return equals(other);
  }

  public int getIndex() {
    return myIndex;
  }

  @Override
  public String toString() {
    return myName;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || o instanceof ParamLevelVariable && myIndex == ((ParamLevelVariable) o).myIndex;
  }
}
