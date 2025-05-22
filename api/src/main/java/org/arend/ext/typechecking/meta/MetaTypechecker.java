package org.arend.ext.typechecking.meta;

import org.arend.ext.concrete.definition.ConcreteMetaDefinition;
import org.arend.ext.core.context.CoreParameter;
import org.arend.ext.typechecking.ExpressionTypechecker;
import org.arend.ext.typechecking.MetaDefinition;
import org.arend.ext.util.Pair;
import org.arend.ext.variable.Variable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public interface MetaTypechecker {
  default @Nullable MetaDefinition typecheck(@NotNull ExpressionTypechecker typechecker, @NotNull ConcreteMetaDefinition definition) {
    throw new UnsupportedOperationException();
  }

  default @Nullable MetaDefinition typecheck(@NotNull ExpressionTypechecker typechecker, @NotNull ConcreteMetaDefinition definition, @NotNull Supplier<@Nullable List<? extends Variable>> levelParametersSupplier, @NotNull Supplier<Pair<CoreParameter, List<Boolean>>> parametersSupplier) {
    return typecheck(typechecker, definition);
  }
}
