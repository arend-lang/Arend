package org.arend.ext.core.sort;

import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public interface CoreSortExpression {
  @Nullable BigInteger getSortHLevel();
  boolean isProp();
}
