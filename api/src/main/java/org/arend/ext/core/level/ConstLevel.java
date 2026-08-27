package org.arend.ext.core.level;

import org.arend.ext.core.ops.CMP;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;

public record ConstLevel(BigInteger value, boolean isCat) {
  public final static ConstLevel CAT_INFINITY = new ConstLevel(null, true);
  public final static ConstLevel INFINITY = new ConstLevel(null, false);
  public final static ConstLevel PROP = new ConstLevel(BigInteger.valueOf(-1), false);

  public boolean isProp() {
    return value != null && value.equals(PROP.value);
  }

  public boolean isInfinity() {
    return value == null;
  }

  public boolean isLessOrEquals(ConstLevel level) {
    if (level.value == null) {
      return value != null || level.isCat || !isCat;
    } else {
      if (value == null) return false;
      int cmp = value.compareTo(level.value);
      return cmp < 0 || cmp == 0 && (level.isCat || !isCat);
    }
  }

  public boolean isLess(ConstLevel level) {
    if (value == null) {
      return level.value == null && !isCat && level.isCat;
    } else {
      if (level.value == null) return true;
      int cmp = value.compareTo(level.value);
      return cmp < 0 || cmp == 0 && !isCat && level.isCat;
    }
  }

  public boolean compare(ConstLevel level, CMP cmp) {
    return cmp == CMP.EQ ? equals(level) : cmp == CMP.LE ? isLessOrEquals(level) : level.isLessOrEquals(this);
  }

  public ConstLevel max(ConstLevel level) {
    return new ConstLevel(value == null || level.value == null ? null : value.max(level.value), isCat || level.isCat);
  }

  public ConstLevel add(BigInteger val) {
    return value == null ? this : new ConstLevel(value.add(val), isCat);
  }

  public ConstLevel succ() {
    return value == null ? CAT_INFINITY : new ConstLevel(value.add(BigInteger.ONE), false);
  }

  @Override
  public @NotNull String toString() {
    return value == null ? (isCat ? "Cat∞" : "∞") : value.toString();
  }
}
