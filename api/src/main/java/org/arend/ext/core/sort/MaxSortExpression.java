package org.arend.ext.core.sort;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface MaxSortExpression extends CoreSortExpression {
  @NotNull List<? extends CoreSortExpression> getSorts();
}
