package org.arend.core.expr.let;

import org.arend.core.context.binding.NamedBinding;
import org.arend.core.expr.Expression;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.subst.InPlaceLevelSubstVisitor;
import org.arend.ext.core.context.BindingVariance;
import org.jetbrains.annotations.NotNull;

public class HaveClause extends NamedBinding {
  private LetClausePattern myPattern;
  private Expression myExpression;
  private final BindingVariance myVariance;

  protected HaveClause(String name, LetClausePattern pattern, Expression expression, BindingVariance variance) {
    super(name);
    myPattern = pattern;
    myExpression = expression;
    myVariance = variance;
  }

  public LetClausePattern getPattern() {
    return myPattern;
  }

  public void setPattern(LetClausePattern pattern) {
    myPattern = pattern;
  }

  @Override
  public @NotNull BindingVariance getVariance() {
    return myVariance;
  }

  @NotNull
  public Expression getExpression() {
    return myExpression;
  }

  @Override
  public void subst(InPlaceLevelSubstVisitor visitor) {
    myExpression.accept(visitor, null);
  }

  public void setExpression(Expression expression) {
    myExpression = expression;
  }

  @Override
  public Expression getType() {
    return myExpression.getType();
  }

  @Override
  public void strip(StripVisitor stripVisitor) {
    myExpression = myExpression.accept(stripVisitor, null);
  }
}
