package org.arend.ext.core.sort;

import org.jetbrains.annotations.NotNull;

public interface PiSortExpression extends CoreSortExpression {
  @NotNull CoreSortExpression getDomain();
  @NotNull CoreSortExpression getCodomain();
}
