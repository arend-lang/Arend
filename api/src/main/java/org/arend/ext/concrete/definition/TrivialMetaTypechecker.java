package org.arend.ext.concrete.definition;

import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.typechecking.MetaTypechecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TrivialMetaTypechecker implements MetaTypechecker {
  private final MetaDefinition myDefinition;

  public TrivialMetaTypechecker(MetaDefinition definition) {
    myDefinition = definition;
  }

  @Override
  public @Nullable MetaDefinition typecheck(@NotNull ExpressionTypechecker typechecker, @NotNull ConcreteMetaDefinition definition) {
    return myDefinition;
  }
}
