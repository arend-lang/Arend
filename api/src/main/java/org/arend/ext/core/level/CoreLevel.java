package org.arend.ext.core.level;

import org.arend.ext.variable.Variable;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.Map;
import java.util.Set;

public interface CoreLevel {
  @NotNull BigInteger getConstant();
  boolean isInfinity();
  boolean isClosed();
  @NotNull Set<? extends Map.Entry<? extends Variable,BigInteger>> getVarPairs();
}
