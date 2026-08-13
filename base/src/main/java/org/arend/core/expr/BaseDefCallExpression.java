package org.arend.core.expr;

import org.arend.core.definition.Definition;
import org.arend.core.subst.Levels;
import org.arend.ext.core.expr.CoreExpression;
import org.arend.ext.core.level.LevelSubstitution;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface BaseDefCallExpression extends CoreExpression {
  @NotNull Definition getDefinition();
  @NotNull Levels getLevels();
  @NotNull LevelSubstitution getLevelSubstitution();
  @NotNull List<Expression> getDefCallArguments();
}
