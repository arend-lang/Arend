package org.arend.ext.concrete.expr;

import org.arend.ext.concrete.ConcreteLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ConcreteUniverseExpression extends ConcreteExpression {
  enum Kind { SORT, CAT, TYPE }

  @Nullable ConcreteLevel getPLevel();
  @Nullable ConcreteLevel getHLevel();
  @NotNull Kind getKind();
}
