package org.arend.ext.core.expr;

import org.arend.ext.core.sort.CoreSortExpression;
import org.jetbrains.annotations.NotNull;

public interface CoreUniverseExpression extends CoreExpression {
  @NotNull CoreSortExpression getSortExpression();
}
