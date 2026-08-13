package org.arend.ext.core.expr;

import org.arend.ext.core.definition.CoreDataDefinition;
import org.arend.ext.core.level.CoreLevels;
import org.jetbrains.annotations.NotNull;

public interface CoreDataCallExpression extends CoreDefCallExpression {
  @Override @NotNull CoreDataDefinition getDefinition();
  @NotNull CoreLevels getLevels();
}
