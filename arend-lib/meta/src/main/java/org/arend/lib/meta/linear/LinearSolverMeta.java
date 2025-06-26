package org.arend.lib.meta.linear;

import org.arend.ext.core.definition.*;
import org.arend.ext.reference.ArendRef;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.arend.ext.typechecking.meta.Dependency;
import org.arend.lib.meta.equation.BaseAlgebraicMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LinearSolverMeta extends BaseAlgebraicMeta {
  @Dependency                                         ArendRef Bool;
  @Dependency(name = "Bool.true")                     ArendRef true_;

  @Dependency(name = "zero<=_")                       ArendRef zeroLE_;
  @Dependency(name = "pos<=pos")                      ArendRef posLEpos;
  @Dependency(name = "pos<pos")                       ArendRef posLpos;
  @Dependency(name = "fromInt_<=")                    ArendRef fromIntLE;
  @Dependency(name = "fromInt_<")                     ArendRef fromIntL;
  @Dependency(name = "Rat.fromInt")                   ArendRef fromInt;
  @Dependency(name = "Preorder.=_<=")                 ArendRef eqToLeq;
  @Dependency(name = "OrderedAAlgebra.coef_<")        ArendRef coefMapL;
  @Dependency(name = "OrderedAAlgebra.coef_<=")       ArendRef coefMapLE;
  @Dependency                                         CoreClassDefinition LinearlyOrderedSemiring;
  @Dependency                                         CoreClassDefinition OrderedRing;
  @Dependency(name = "OrderedAddGroup.<")             ArendRef addGroupLess;
  @Dependency(name = "LinearOrder.<=")                ArendRef linearOrederLeq;
  @Dependency(name = "Preorder.<=")                   CoreClassField lessOrEquals;
  @Dependency(name = "StrictPoset.<")                 CoreClassField less;

  @Dependency                                         ArendRef LinearRatAlgebraData;
  @Dependency                                         ArendRef LinearRatData;
  @Dependency                                         ArendRef LinearSemiringData;
  @Dependency                                         ArendRef LinearRingData;
  @Dependency(name = "LinearData.solveContrProblem")  ArendRef solveContrProblem;
  @Dependency(name = "LinearData.solve<=Problem")     ArendRef solveLeqProblem;
  @Dependency(name = "LinearData.solve<Problem")      ArendRef solveLessProblem;
  @Dependency(name = "LinearData.solve=Problem")      ArendRef solveEqProblem;
  @Dependency(name = "Operation.Less")                ArendRef opLess;
  @Dependency(name = "Operation.LessOrEquals")        ArendRef opLessOrEquals;
  @Dependency(name = "Operation.Equals")              ArendRef opEquals;

  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] { true };
  }

  @Override
  public int numberOfOptionalExplicitArguments() {
    return 1;
  }

  @Override
  public boolean requireExpectedType() {
    return true;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    return new LinearSolver(typechecker, contextData.getMarker(), this).solve(contextData.getExpectedType(), contextData.getArguments().isEmpty() ? null : contextData.getArguments().getFirst().getExpression());
  }
}
