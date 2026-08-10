package org.arend.term.concrete;

public interface ConcreteLevelExpressionVisitor<P, R> {
  R visitNumber(Concrete.NumberLevelExpression expr, P param);
  R visitVar(Concrete.VarLevelExpression expr, P param);
  R visitSuc(Concrete.SucLevelExpression expr, P param);
  R visitMax(Concrete.MaxLevelExpression expr, P param);
}
