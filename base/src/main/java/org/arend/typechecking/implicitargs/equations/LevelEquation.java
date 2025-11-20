package org.arend.typechecking.implicitargs.equations;

import java.util.Objects;

public class LevelEquation<Var> {
  private final Var myVar1;
  private final Var myVar2;
  private final Integer myConstant;
  private final Integer myMaxConstant;
  private final boolean myCat;

  public LevelEquation(Var var1, Var var2, int constant, Integer maxConstant) {
    myVar1 = var1;
    myVar2 = var2;
    myConstant = constant;
    myMaxConstant = maxConstant;
    myCat = false;
  }

  public LevelEquation(Var var1, Var var2, int constant) {
    this(var1, var2, constant, null);
  }

  public LevelEquation(Var var, boolean isCat) {
    myVar1 = null;
    myVar2 = var;
    myConstant = null;
    myMaxConstant = null;
    myCat = isCat;
  }

  public LevelEquation(LevelEquation<? extends Var> equation) {
    myVar1 = equation.myVar1;
    myVar2 = equation.myVar2;
    myConstant = equation.myConstant;
    myMaxConstant = equation.myMaxConstant;
    myCat = equation.myCat;
  }

  public boolean isInfinity() {
    return myConstant == null && !myCat;
  }

  public boolean isCat() {
    return myCat;
  }

  public Var getVariable1() {
    if (myConstant == null) {
      throw new IllegalStateException();
    }
    return myVar1;
  }

  public Var getVariable2() {
    if (myConstant == null) {
      throw new IllegalStateException();
    }
    return myVar2;
  }

  public int getConstant() {
    if (myConstant == null) {
      throw new IllegalStateException();
    }
    return myConstant;
  }

  public Integer getMaxConstant() {
    if (myConstant == null) {
      throw new IllegalStateException();
    }
    return myMaxConstant;
  }

  public Var getVariable() {
    if (myConstant != null) {
      throw new IllegalStateException();
    }
    return myVar2;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    LevelEquation<?> that = (LevelEquation<?>) o;
    return myCat == that.myCat && Objects.equals(myVar1, that.myVar1) && Objects.equals(myVar2, that.myVar2) && Objects.equals(myConstant, that.myConstant) && Objects.equals(myMaxConstant, that.myMaxConstant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myVar1, myVar2, myConstant, myMaxConstant, myCat);
  }
}
