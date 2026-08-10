package org.arend.ext.core.sort;

import org.arend.ext.core.level.CoreSort;
import org.jetbrains.annotations.NotNull;

public interface ConstSortExpression extends CoreSortExpression {
  @NotNull CoreSort getSort();
}
