package org.arend.core.expr;

import org.arend.core.context.binding.inference.InferenceVariable;
import org.arend.core.definition.ClassField;
import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.sort.Level;
import org.arend.core.sort.Sort;
import org.arend.core.sort.SortExpression;
import org.arend.ext.core.level.ConstLevel;
import org.arend.ext.core.level.LevelSubstitution;
import org.arend.ext.core.expr.CoreExpressionVisitor;
import org.arend.ext.core.expr.CoreUniverseExpression;
import org.arend.util.Decision;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class UniverseExpression extends Expression implements CoreUniverseExpression {
  public static final UniverseExpression OMEGA = new UniverseExpression(Sort.INFINITY);
  public static final UniverseExpression INF_OMEGA = new UniverseExpression(Sort.INFINITY);

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
  public Expression replaceInfinityLevel(InferenceVariable variable) {
    if (mySortExpression instanceof SortExpression.Const(Sort sort) && sort.getPLevel().isInfinity()) {
      variable.setHLevel(sort.getHLevel());
      return new UniverseExpression(new SortExpression.InfVar(variable));
    } else {
      return null;
    }
  }

  @Override
  public Expression replaceInferenceVariable() {
    if (mySortExpression instanceof SortExpression.InfVar var) {
      ConstLevel hLevel = var.getVariable().getHLevel();
      return hLevel == null ? UniverseExpression.OMEGA : new UniverseExpression(new Sort(Level.INFINITY, hLevel));
    } else {
      return this;
    }
  }

  @Override
  public Expression replaceInfinityLevel(int index, List<ClassField> fields) {
    return mySortExpression instanceof SortExpression.Const(Sort sort) && sort.getPLevel().isInfinity() || mySortExpression instanceof SortExpression.Var ? new UniverseExpression(new SortExpression.Var(index, fields)) : null;
  }

  @Override
  public Expression replaceInfinityLevel(Level level) {
    return mySortExpression instanceof SortExpression.Const(Sort sort) ? new UniverseExpression(new Sort(level, sort.getHLevel())) : null;
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
