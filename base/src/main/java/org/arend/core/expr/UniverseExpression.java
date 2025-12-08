package org.arend.core.expr;

import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.sort.Sort;
import org.arend.core.sort.SortExpression;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.expr.CoreExpressionVisitor;
import org.arend.ext.core.expr.CoreUniverseExpression;
import org.arend.util.Decision;
import org.jetbrains.annotations.NotNull;

public class UniverseExpression extends Expression implements CoreUniverseExpression {
  public static final UniverseExpression OMEGA = new UniverseExpression(Sort.INFINITY);

  private SortExpression mySortExpression;

  public UniverseExpression(SortExpression sort) {
    mySortExpression = sort;
  }

  public UniverseExpression(Sort sort) {
    this(new SortExpression.Const(sort));
  }

  @Override
  public boolean isOmega() {
    return mySortExpression instanceof SortExpression.Const(Sort sort) && sort.isOmega();
  }

  public void substSort(LevelSubstitution substitution) {
    if (!substitution.isEmpty()) mySortExpression = mySortExpression.subst(substitution);
  }

  @NotNull
  @Override
  public SortExpression getSortExpression() {
    return mySortExpression;
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitUniverse(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitUniverse(this, param1, param2);
  }

  @Override
  public <P, R> R accept(@NotNull CoreExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitUniverse(this, params);
  }

  @Override
  public Decision isWHNF() {
    return Decision.YES;
  }

  @Override
  public Expression getStuckExpression() {
    return null;
  }
}
