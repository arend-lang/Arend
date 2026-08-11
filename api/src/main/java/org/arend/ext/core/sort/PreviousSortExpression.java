package org.arend.ext.core.sort;

import org.jetbrains.annotations.NotNull;

public interface PreviousSortExpression extends CoreSortExpression {
  @NotNull CoreSortExpression getSort();
}
