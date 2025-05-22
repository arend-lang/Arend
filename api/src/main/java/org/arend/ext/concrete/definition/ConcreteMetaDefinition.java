package org.arend.ext.concrete.definition;

import org.arend.ext.concrete.ConcreteParameter;
import org.arend.ext.concrete.expr.ConcreteExpression;
import org.arend.ext.reference.MetaRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface ConcreteMetaDefinition extends ConcreteDefinition {
  @Override @NotNull MetaRef getRef();
  @NotNull List<? extends ConcreteParameter> getParameters();
  @Nullable ConcreteExpression getBody();
}
