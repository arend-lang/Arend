package org.arend.core.expr;

import org.arend.core.expr.visitor.ExpressionVisitor;
import org.arend.core.expr.visitor.ExpressionVisitor2;
import org.arend.core.sort.Sort;
import org.arend.ext.concrete.ConcreteSourceNode;
import org.arend.ext.core.expr.CoreErrorExpression;
import org.arend.ext.core.expr.CoreExpressionVisitor;
import org.arend.ext.error.ErrorReporter;
import org.arend.ext.error.GeneralError;
import org.arend.ext.error.LocalError;
import org.arend.ext.error.TypecheckingError;
import org.arend.typechecking.error.local.GoalError;
import org.arend.util.Decision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ErrorExpression extends Expression implements CoreErrorExpression {
  private final Expression myExpression;
  private final String myGoalName;
  private LocalError myError;

  public ErrorExpression(Expression expression, LocalError error) {
    myGoalName = error instanceof GoalError ? ((GoalError) error).goalName : error != null && error.level == GeneralError.Level.GOAL ? "" : null;
    myExpression = error instanceof GoalError && ((GoalError) error).errors.isEmpty() ? expression : null;
    myError = error;
  }

  public ErrorExpression(LocalError error) {
    this(null, error);
  }

  public ErrorExpression(Expression expression, String goalName) {
    myExpression = expression;
    myGoalName = goalName;
    myError = null;
  }

  public ErrorExpression() {
    myExpression = null;
    myGoalName = null;
    myError = null;
  }

  public Expression getExpression() {
    return myExpression;
  }

  public ErrorExpression replaceExpression(Expression expr) {
    return new ErrorExpression(expr, myGoalName);
  }

  public String getGoalName() {
    return myGoalName;
  }

  @Override
  public boolean isGoal() {
    return myGoalName != null;
  }

  @Override
  public boolean isError() {
    return !isGoal();
  }

  @Override
  public boolean reportIfError(@NotNull ErrorReporter errorReporter, @Nullable ConcreteSourceNode marker) {
    if (myError != null) {
      if (myError instanceof TypecheckingError && ((TypecheckingError) myError).cause == null) {
        ((TypecheckingError) myError).cause = marker;
      }
      errorReporter.report(myError);
      myError = null;
    }
    return isError();
  }

  @Override
  public boolean canBeConstructor() {
    return false;
  }

  @Override
  public boolean isBoxed() {
    return !isGoal();
  }

  @Override
  public <P, R> R accept(ExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitError(this, params);
  }

  @Override
  public <P1, P2, R> R accept(ExpressionVisitor2<? super P1, ? super P2, ? extends R> visitor, P1 param1, P2 param2) {
    return visitor.visitError(this, param1, param2);
  }

  @Override
  public <P, R> R accept(@NotNull CoreExpressionVisitor<? super P, ? extends R> visitor, P params) {
    return visitor.visitError(this, params);
  }

  @Override
  public Decision isWHNF() {
    return Decision.YES;
  }

  @Override
  public Expression getStuckExpression() {
    return this;
  }

  @Override
  public Sort getSortOfType() {
    return Sort.PROP;
  }
}
