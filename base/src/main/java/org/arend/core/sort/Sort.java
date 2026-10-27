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

  public static final Sort PROP = new Sort(new Level(BigInteger.ZERO), ConstLevel.PROP);
  public static final Sort SET0 = new Sort(new Level(BigInteger.ZERO), new ConstLevel(BigInteger.ZERO));
  public static final Sort INFINITY = new Sort(Level.INFINITY, ConstLevel.CAT_INFINITY);

  public static Sort SetOfLevel(int pLevel) {
    return new Sort(pLevel, 0);
  }

  public static Sort TypeOfLevel(int pLevel) {
    return new Sort(new Level(BigInteger.valueOf(pLevel)), ConstLevel.INFINITY);
  }

  public Sort(@NotNull Level pLevel, @NotNull ConstLevel hLevel) {
    myPLevel = hLevel.isProp() && !pLevel.isZero() ? new Level(BigInteger.ZERO) : pLevel;
    myHLevel = hLevel;
  }

  public Sort(int pLevel, int hLevel) {
    this(new Level(BigInteger.valueOf(pLevel)), new ConstLevel(BigInteger.valueOf(hLevel)));
    assert pLevel >= 0;
    assert hLevel >= 0;
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
    return myHLevel.isCat() && myPLevel.isInfinity() && myHLevel.isInfinity();
  }

  public Sort succ() {
    if (isProp()) return SET0;
    // \Cat u is a type when u is finite
    ConstLevel hLevel = myHLevel.isCat() && !myPLevel.isInfinity() ? ConstLevel.INFINITY : myHLevel.succ();
    return new Sort(getPLevel().add(BigInteger.ONE), hLevel);
  }

  public Sort max(Sort sort) {
    return isProp() ? sort : sort.isProp() ? this : new Sort(myPLevel.max(sort.myPLevel), myHLevel.max(sort.myHLevel));
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

  /**
   * A sort which is not \Cat is not a subtype of \Cat u for finite u.
   */
  private static boolean isTypeBelowFiniteCat(Sort sort1, Sort sort2) {
    return !sort1.myHLevel.isCat() && sort2.myHLevel.isCat() && !sort2.myPLevel.isInfinity();
  }

  public static boolean compare(Sort sort1, Sort sort2, CMP cmp, Equations equations, Concrete.SourceNode sourceNode) {
    return compare(sort1, sort2, cmp, true, equations, sourceNode);
  }

  /**
   * @param strictCat if false, a sort which is not \Cat is a subtype of \Cat u for every u.
   *                  This is used to check invariant fields of data types.
   */
  public static boolean compare(Sort sort1, Sort sort2, CMP cmp, boolean strictCat, Equations equations, Concrete.SourceNode sourceNode) {
    if (sort2.isOmega() && cmp == CMP.LE || sort1.isOmega() && cmp == CMP.GE) {
      return true;
    }
    if (strictCat && (cmp != CMP.GE && isTypeBelowFiniteCat(sort1, sort2) || cmp != CMP.LE && isTypeBelowFiniteCat(sort2, sort1))) {
      return false;
    }
    if (sort1.isProp() && !sort2.getPLevel().hasInferenceVariables()) {
      return cmp == CMP.LE || sort2.isProp();
    }
    if (sort2.isProp() && !sort1.getPLevel().hasInferenceVariables()) {
      return cmp == CMP.GE || sort1.isProp();
    }
    return sort1.getHLevel().compare(sort2.getHLevel(), cmp) && Level.compare(sort1.getPLevel(), sort2.getPLevel(), cmp, equations, sourceNode);
  }

  public boolean isLessOrEquals(Sort other) {
    return compare(this, other, CMP.LE, DummyEquations.getInstance(), null);
  }

  public boolean isLessOrEqualsNonStrictCat(Sort other) {
    return compare(this, other, CMP.LE, false, DummyEquations.getInstance(), null);
  }

  public Sort subst(LevelSubstitution subst) {
    return subst.isEmpty() || myPLevel.isClosed() ? this : new Sort(myPLevel.subst(subst), myHLevel);
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
