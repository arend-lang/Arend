package org.arend.ext.core.expr;

import org.jetbrains.annotations.NotNull;

public interface CorePathTypeExpression extends CoreExpression {
  @NotNull CoreExpression getArgumentType();
  @NotNull CoreExpression getLeftArgument();
  @NotNull CoreExpression getRightArgument();
  boolean isDirected();
}
