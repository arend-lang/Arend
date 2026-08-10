package org.arend.typechecking.implicitargs.equations;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.util.Objects;

public class LevelEquation<Var> {
  private final Var myVar1;
  private final Var myVar2;
  private final BigInteger myConstant;
  private final BigInteger myMaxConstant;

  public LevelEquation(Var var1, Var var2, @NotNull BigInteger constant, @Nullable BigInteger maxConstant) {
    myVar1 = var1;
    myVar2 = var2;
    myConstant = constant;
    myMaxConstant = maxConstant;
  }

  public LevelEquation(Var var1, Var var2, @NotNull BigInteger constant) {
    myVar1 = var1;
    myVar2 = var2;
    myConstant = constant;
    myMaxConstant = null;
  }

  public LevelEquation(LevelEquation<? extends Var> equation) {
    myVar1 = equation.myVar1;
    myVar2 = equation.myVar2;
    myConstant = equation.myConstant;
    myMaxConstant = equation.myMaxConstant;
  }

  public Var getVariable1() {
    return myVar1;
  }

  public Var getVariable2() {
    return myVar2;
  }

  public @NotNull BigInteger getConstant() {
    return myConstant;
  }

  public @Nullable BigInteger getMaxConstant() {
    return myMaxConstant;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    LevelEquation<?> that = (LevelEquation<?>) o;
    return Objects.equals(myVar1, that.myVar1) && Objects.equals(myVar2, that.myVar2) && Objects.equals(myConstant, that.myConstant) && Objects.equals(myMaxConstant, that.myMaxConstant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myVar1, myVar2, myConstant, myMaxConstant);
  }
}
