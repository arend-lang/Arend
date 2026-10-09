package org.arend.core.context.binding;

import org.arend.core.expr.Expression;
import org.arend.core.expr.visitor.StripVisitor;
import org.arend.core.subst.InPlaceLevelSubstVisitor;
import org.arend.ext.core.context.BindingVariance;
import org.jetbrains.annotations.NotNull;

public class TypedEvaluatingBinding extends TypedBinding implements EvaluatingBinding {
  private Expression myExpression;
  private final BindingVariance myVariance;

  public TypedEvaluatingBinding(String name, Expression expression, Expression type, BindingVariance variance) {
    super(name, type);
    myExpression = expression;
    myVariance = variance;
  }

  public TypedEvaluatingBinding(String name, Expression expression, Expression type) {
    this(name, expression, type, BindingVariance.INVARIANT);
  }

  @Override
  public @NotNull BindingVariance getVariance() {
    return myVariance;
  }

  @NotNull
  @Override
  public Expression getExpression() {
    return myExpression;
  }

  @Override
  public void strip(StripVisitor stripVisitor) {
    super.strip(stripVisitor);
    myExpression = myExpression.accept(stripVisitor, null);
  }

  @Override
  public void subst(InPlaceLevelSubstVisitor visitor) {
    myExpression.accept(visitor, null);
  }
}
