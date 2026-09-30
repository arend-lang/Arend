package org.arend.ext.core.expr;

import org.jetbrains.annotations.NotNull;

public interface CorePathExpression extends CoreExpression {
  @NotNull CoreExpression getArgumentType();
  @NotNull CoreExpression getArgument();
  boolean isForcedInfinite();
}
