package org.arend.ext.core.level;

import org.arend.ext.core.ops.CMP;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public record ConstLevel(BigInteger value) {
  public final static ConstLevel INFINITY = new ConstLevel(null);
  public final static ConstLevel PROP = new ConstLevel(BigInteger.valueOf(-1));

  public boolean isProp() {
    return value != null && value.equals(PROP.value);
  }

  public boolean isInfinity() {
    return value == null;
  }

  public boolean isLessOrEquals(ConstLevel level) {
    return level.value == null || value != null && value.compareTo(level.value) <= 0;
  }

  public boolean isLess(ConstLevel level) {
    return value != null && (level.value == null || value.compareTo(level.value) < 0);
  }

  public boolean compare(ConstLevel level, CMP cmp) {
    return cmp == CMP.EQ ? equals(level) : cmp == CMP.LE ? isLessOrEquals(level) : level.isLessOrEquals(this);
  }

  public ConstLevel max(ConstLevel level) {
    return value == null || level.value == null ? INFINITY : new ConstLevel(value.max(level.value));
  }

  public ConstLevel add(BigInteger val) {
    return value == null ? this : new ConstLevel(value.add(val));
  }

  @Override
  public @NotNull String toString() {
    return value == null ? "∞" : value.toString();
  }
}
