package org.arend.core.expr.let;

import org.arend.core.context.binding.EvaluatingBinding;
import org.arend.core.expr.Expression;
import org.arend.ext.core.context.BindingVariance;

public class LetClause extends HaveClause implements EvaluatingBinding {
  protected LetClause(String name, LetClausePattern pattern, Expression expression, BindingVariance variance) {
    super(name, pattern, expression, variance);
  }

  public static HaveClause make(boolean isLet, String name, LetClausePattern pattern, Expression expression, BindingVariance variance) {
    return isLet ? new LetClause(name, pattern, expression, variance) : new HaveClause(name, pattern, expression, variance);
  }
}
