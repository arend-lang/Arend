package org.arend.ext.concrete.expr;

import org.arend.ext.concrete.ConcreteLevel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public interface ConcreteUniverseExpression extends ConcreteExpression {
  enum Kind { CAT, TYPE }

  @Nullable ConcreteLevel getPLevel();
  @Nullable BigInteger getHLevel();
  @NotNull Kind getKind();
}
