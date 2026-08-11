package org.arend.ext.core.sort;

import org.jetbrains.annotations.NotNull;

public interface NextSortExpression extends CoreSortExpression {
  @NotNull CoreSortExpression getSort();
}
