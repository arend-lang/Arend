package org.arend.ext.core.sort;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PiSortExpression extends CoreSortExpression {
  @NotNull List<? extends CoreSortExpression> getDomain();
  @NotNull CoreSortExpression getCodomain();
}
