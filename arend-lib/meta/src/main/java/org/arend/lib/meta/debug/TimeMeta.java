package org.arend.lib.meta.debug;

import org.arend.ext.typechecking.BaseMetaDefinition;
import org.arend.ext.typechecking.ContextData;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.TypedExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

public class TimeMeta extends BaseMetaDefinition {
  @Override
  public boolean @Nullable [] argumentExplicitness() {
    return new boolean[] {};
  }

  @Override
  public boolean allowExcessiveArguments() {
    return false;
  }

  @Override
  public @Nullable TypedExpression invokeMeta(@NotNull ExpressionTypechecker typechecker, @NotNull ContextData contextData) {
    return typechecker.typecheck(contextData.getFactory().number(BigInteger.valueOf(System.currentTimeMillis())), contextData.getExpectedType());
  }
}
