package org.arend.core.expr.let;

import org.arend.core.expr.ClassCallExpression;
import org.arend.core.expr.Expression;
import org.arend.ext.core.context.BindingVariance;

public class TypedLetClause extends LetClause {
  public Expression type;

  protected TypedLetClause(String name, LetClausePattern pattern, Expression expression, Expression type, BindingVariance variance) {
    super(name, pattern, expression, variance);
    this.type = type;
  }

  public static HaveClause make(boolean isLet, String name, LetClausePattern pattern, Expression expression, Expression type, BindingVariance variance) {
    return isLet ? new TypedLetClause(name, pattern, expression, type, variance) : new TypedHaveClause(name, pattern, expression, type, variance);
  }

  @Override
  public Expression getType() {
    return type == null || type instanceof ClassCallExpression ? super.getType() : type;
  }
}
