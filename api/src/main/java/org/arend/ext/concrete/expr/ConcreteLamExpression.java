package org.arend.ext.concrete.expr;

import org.arend.ext.concrete.ConcreteParameter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface ConcreteLamExpression extends ConcreteExpression {
  @NotNull List<? extends ConcreteParameter> getParameters();
  @NotNull ConcreteExpression getBody();
}
