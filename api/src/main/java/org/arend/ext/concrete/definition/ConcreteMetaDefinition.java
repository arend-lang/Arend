package org.arend.ext.concrete.definition;

import org.arend.ext.concrete.ConcreteParameter;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ConcreteMetaDefinition extends ConcreteDefinition {
  @NotNull List<? extends ConcreteParameter> getParameters();
  @Nullable ConcreteExpression getBody();
}
