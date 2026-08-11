package org.arend.core.sort;

import org.arend.core.expr.UniverseExpression;
import org.arend.ext.core.level.ConstLevel;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.level.CoreSort;
import org.arend.ext.core.ops.CMP;
import org.arend.term.concrete.Concrete;
import org.arend.typechecking.implicitargs.equations.DummyEquations;
import org.arend.typechecking.implicitargs.equations.Equations;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.List;

public class Sort implements CoreSort {
  private final Level myPLevel;
  private final ConstLevel myHLevel;
  private final boolean myCat;

  public static final Sort PROP = new Sort(new Level(BigInteger.ZERO), ConstLevel.PROP);
  public static final Sort SET0 = new Sort(new Level(BigInteger.ZERO), new ConstLevel(BigInteger.ZERO));
  public static final Sort INFINITY = new Sort(Level.INFINITY, ConstLevel.INFINITY, true);

  public static Sort SetOfLevel(int pLevel) {
    return new Sort(pLevel, 0);
  }

  public static Sort SetOfLevel(Level pLevel) {
    return new Sort(pLevel, new ConstLevel(BigInteger.ZERO));
  }

  public static Sort TypeOfLevel(int pLevel) {
    return new Sort(new Level(BigInteger.valueOf(pLevel)), ConstLevel.INFINITY);
  }

  public Sort(@NotNull Level pLevel, @NotNull ConstLevel hLevel, boolean isCat) {
    myPLevel = hLevel.isProp() && !pLevel.isZero() ? new Level(BigInteger.ZERO) : pLevel;
    myHLevel = hLevel;
    myCat = false; // TODO[sorts]: Temporarily disable \Cat sorts.
  }

  public Sort(@NotNull Level pLevel, @NotNull ConstLevel hLevel) {
    this(pLevel, hLevel, false);
  }

  public Sort(int pLevel, int hLevel) {
    this(new Level(BigInteger.valueOf(pLevel)), new ConstLevel(BigInteger.valueOf(hLevel)));
    assert pLevel >= 0;
    assert hLevel >= 0;
  }

  public Sort(Level pLevel, boolean isCat) {
    this(pLevel, ConstLevel.INFINITY, isCat);
  }

  @NotNull
  @Override
  public Level getPLevel() {
    return myPLevel;
  }

  @NotNull
  @Override
  public ConstLevel getHLevel() {
    return myHLevel;
  }

  public boolean isOmega() {
    return /* TODO[sorts]: myCat && */ myPLevel.isInfinity() && myHLevel.isInfinity();
  }

  public boolean isCat() {
    return myCat;
  }

  public Sort succ() {
    return isProp() ? SET0 : new Sort(getPLevel().add(BigInteger.ONE), getHLevel().add(BigInteger.ONE));
  }

  public Sort max(Sort sort) {
    if (isProp()) return sort;
    if (sort.isProp()) return this;
    Level pLevel = myPLevel.max(sort.myPLevel);
    ConstLevel hLevel = myHLevel.max(sort.myHLevel);
    return pLevel == null || hLevel == null ? null : new Sort(pLevel, hLevel, myCat || sort.myCat);
  }

  public static Sort max(List<Sort> sorts) {
    Sort result = Sort.PROP;
    for (Sort sort : sorts) {
      result = result.max(sort);
    }
    return result;
  }

  @Override
  public boolean isProp() {
    return myHLevel.isProp();
  }

  @Override
  public boolean isSet() {
    return !myHLevel.isInfinity() && myHLevel.value().equals(BigInteger.ZERO);
  }

  public static boolean compare(Sort sort1, Sort sort2, CMP cmp, Equations equations, Concrete.SourceNode sourceNode) {
    if (sort2.isOmega() && cmp == CMP.LE || sort1.isOmega() && cmp == CMP.GE) {
      return true;
    }
    if (sort1.isProp() && !sort2.getPLevel().hasInferenceVariables()) {
      return cmp == CMP.LE || sort2.isProp();
    }
    if (sort2.isProp() && !sort1.getPLevel().hasInferenceVariables()) {
      return cmp == CMP.GE || sort1.isProp();
    }
    if (sort1.myCat != sort2.myCat && (cmp == CMP.EQ || cmp == CMP.LE && sort1.myCat || cmp == CMP.GE && sort2.myCat)) {
      return false;
    }
    return sort1.getHLevel().compare(sort2.getHLevel(), cmp) && Level.compare(sort1.getPLevel(), sort2.getPLevel(), cmp, equations, sourceNode);
  }

  public boolean isLessOrEquals(Sort other) {
    return compare(this, other, CMP.LE, DummyEquations.getInstance(), null);
  }

  public Sort subst(LevelSubstitution subst) {
    return subst.isEmpty() || myPLevel.isClosed() ? this : new Sort(myPLevel.subst(subst), myHLevel, myCat);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Sort && compare(this, (Sort) other, CMP.EQ, DummyEquations.getInstance(), null);
  }

  @Override
  public String toString() {
    return new UniverseExpression(this).toString();
  }
}
