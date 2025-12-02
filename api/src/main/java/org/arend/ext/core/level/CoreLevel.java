package org.arend.ext.core.level;

import org.arend.ext.variable.Variable;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public interface CoreLevel {
  int getConstant();
  boolean isInfinity();
  boolean isClosed();
  @NotNull Set<? extends Map.Entry<? extends Variable,Integer>> getVarPairs();
}
