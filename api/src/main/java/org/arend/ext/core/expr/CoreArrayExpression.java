package org.arend.ext.core.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CoreArrayExpression extends CoreExpression {
  @NotNull List<? extends CoreExpression> getElements();
  @NotNull CoreExpression getElementsType();
  @Nullable CoreExpression getTail();
}
