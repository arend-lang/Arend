package org.arend.ext.core.expr;

import org.jetbrains.annotations.NotNull;

public interface CorePathExpression extends CoreExpression {
  /**
   * @return {@code null} if the path is non-dependent; otherwise, an expression of type {@code I -> \Type}.
   */
  @NotNull CoreExpression getArgumentType();
  @NotNull CoreExpression getArgument();
}
