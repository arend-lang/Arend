package org.arend.ext.core.definition;

import org.arend.ext.core.level.CoreSort;
import org.arend.ext.core.sort.CoreSortExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface CoreDataDefinition extends CoreDefinition {
  int getTruncatedLevel();
  @Nullable CoreSort getSort();
  @NotNull CoreSortExpression getSortExpression();
  @NotNull List<? extends CoreConstructor> getConstructors();

  default boolean isTruncated() {
    return getTruncatedLevel() >= -1;
  }

  CoreConstructor findConstructor(@NotNull String name);
}
